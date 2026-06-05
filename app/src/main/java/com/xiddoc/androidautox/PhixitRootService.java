package com.xiddoc.androidautox;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.Log;

import com.topjohnwu.superuser.ipc.RootService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * libsu {@link RootService}: runs in a root process so it can open GMS's private
 * {@code phenotype.db} directly with the platform {@link SQLiteDatabase} API. This
 * replaces the bundled {@code sqlite3} binary and all the SQL that used to be piped
 * through {@code su}. Blobs cross the binder as real {@code byte[]} (no hex), and
 * every query is parameterized (no string-built SQL injected into a shell).
 */
public class PhixitRootService extends RootService {

    static final String TAG = "AndroidAutoX";

    /** Sidecar extensions that, together with the main DB file, make up a restorable set. */
    static final String[] DB_SIDECARS = {"-wal", "-shm", "-journal"};

    static {
        // See neutralizeWalSettingsLookup(): do it as early as the class loads in the
        // root process, before any database is opened.
        neutralizeWalSettingsLookup();
    }

    private static volatile boolean walFlagsReady;

    /**
     * The libsu root process is not a registered app in ActivityManager, so SQLite's lazy
     * read of {@code Settings.Global.sqlite_compatibility_wal_flags} during
     * {@code SQLiteDatabase.openDatabase} -- a ContentResolver query to the {@code settings}
     * provider -- fails with {@code SecurityException: Unable to find app for caller}.
     * Pre-initializing {@code SQLiteCompatibilityWalFlags} with null flags marks it
     * initialized so it never queries the provider. (Hidden-API reflection is allowed here:
     * the root process is started via app_process, not a restricted zygote app.)
     */
    private static void neutralizeWalSettingsLookup() {
        if (walFlagsReady) return;
        try {
            Class<?> c = Class.forName("android.database.sqlite.SQLiteCompatibilityWalFlags");
            java.lang.reflect.Method init = c.getDeclaredMethod("init", String.class);
            init.setAccessible(true);
            init.invoke(null, (String) null);
        } catch (Throwable t) {
            Log.w(TAG, "Could not pre-init SQLiteCompatibilityWalFlags", t);
        }
        walFlagsReady = true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Impl();
    }

    /**
     * Pure guard for {@link Impl#deleteRecursive}: rejects paths that are too dangerous to
     * delete recursively as root. Rejects null/empty/whitespace and the filesystem root,
     * any path containing a {@code ..} traversal component, and -- after canonicalization --
     * anything that does not live under the GMS data dir allowlist
     * ({@link GmsPaths#GMS_DATA_DIR}). Extracted as a static so it can be unit-tested
     * off-device (the service itself can't run in a JVM).
     */
    static boolean isUnsafeDeletePath(String path) {
        if (path == null) return true;
        String p = path.trim();
        if (p.isEmpty() || p.equals("/")) return true;
        // Reject any traversal component outright, before canonicalization, so we never
        // rely on canonicalization succeeding to catch `..`.
        if (containsParentTraversal(p)) return true;
        String canon;
        try {
            canon = new File(p).getCanonicalPath();
        } catch (IOException e) {
            return true; // cannot canonicalize -> treat as unsafe
        }
        // Require the (canonical) target to live under the allowlisted GMS data dir.
        return !isUnderAllowlistedBase(canon);
    }

    /** True if any path component is exactly {@code ..} (or the whole path is/has "/.."). */
    private static boolean containsParentTraversal(String path) {
        if (path.contains("/../") || path.endsWith("/..") || path.equals("..")) return true;
        return path.startsWith("../");
    }

    /** True if {@code canonicalPath} is the allowlisted base itself or nested beneath it. */
    private static boolean isUnderAllowlistedBase(String canonicalPath) {
        String base = GmsPaths.GMS_DATA_DIR; // ends with '/'
        String baseNoSlash = base.substring(0, base.length() - 1);
        return canonicalPath.equals(baseNoSlash) || canonicalPath.startsWith(base);
    }

    /**
     * Pure helper for {@link Impl#deleteTree}: should the recursive delete descend into an
     * entry with this {@code lstat} mode? Descend only into real directories, never into
     * symlinks (even symlinks to dirs) -- descending a symlink would let a root delete reach
     * the link's TARGET tree outside the requested subtree. Extracted as a static so it can
     * be unit-tested off-device against {@link OsConstants} mode bits.
     */
    static boolean shouldDescend(int stMode) {
        return OsConstants.S_ISDIR(stMode) && !OsConstants.S_ISLNK(stMode);
    }

    /** Opens read-write with a rollback journal (not WAL) so GMS never finds a
     *  root-owned {@code -wal}/{@code -shm} it cannot read after we restart it. */
    private static SQLiteDatabase openRW(String dbPath) {
        neutralizeWalSettingsLookup();
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
                dbPath, null, SQLiteDatabase.OPEN_READWRITE);
        try {
            db.disableWriteAheadLogging();
        } catch (Throwable ignored) {
        }
        return db;
    }

    static final class Impl extends IPhixitRoot.Stub {

        @Override
        public List<Partition> readPartitions(String pkg) {
            List<Partition> out = new ArrayList<Partition>();
            SQLiteDatabase db = openRW(GmsPaths.PHENO_DB);
            Cursor c = null;
            try {
                c = db.rawQuery(
                        "SELECT param_partition_id, flags_content FROM param_partitions " +
                                "WHERE static_config_package_id IN (SELECT static_config_package_id " +
                                "FROM static_config_packages WHERE name=?)",
                        new String[]{pkg});
                while (c.moveToNext()) {
                    out.add(new Partition(c.getLong(0), c.getBlob(1)));
                }
            } finally {
                if (c != null) c.close();
                db.close();
            }
            return out;
        }

        @Override
        public void writePartitions(List<Partition> parts, int servingVersion) {
            SQLiteDatabase db = openRW(GmsPaths.PHENO_DB);
            try {
                db.beginTransaction();
                for (Partition p : parts) {
                    ContentValues cv = new ContentValues();
                    cv.put("flags_content", p.blob);
                    db.update("param_partitions", cv,
                            "param_partition_id=?", new String[]{String.valueOf(p.id)});
                }
                if (servingVersion >= 0) {
                    db.execSQL("UPDATE last_fetch SET serving_version=? WHERE type=1",
                            new Object[]{servingVersion});
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
                try {
                    db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)");
                } catch (Throwable ignored) {
                }
                db.close();
            }
        }

        @Override
        public String query(String dbPath, String sql) {
            SQLiteDatabase db = openRW(dbPath);
            Cursor c = null;
            StringBuilder sb = new StringBuilder();
            try {
                c = db.rawQuery(sql, null);
                int cols = c.getColumnCount();
                boolean firstRow = true;
                while (c.moveToNext()) {
                    if (!firstRow) sb.append('\n');
                    firstRow = false;
                    for (int i = 0; i < cols; i++) {
                        if (i > 0) sb.append('|');
                        String v = c.getString(i);
                        if (v != null) sb.append(v);
                    }
                }
            } finally {
                if (c != null) c.close();
                db.close();
            }
            return sb.toString();
        }

        @Override
        public void execStatements(String dbPath, List<String> statements) {
            SQLiteDatabase db = openRW(dbPath);
            try {
                // Lenient, like `sqlite3 -batch` (no .bail): attempt each statement and
                // keep going on error, so e.g. a DELETE against a table that no longer
                // exists on modern GMS doesn't abort the rest.
                for (String s : statements) {
                    if (s == null) continue;
                    String t = s.trim();
                    // Strip a single trailing ';' (the outermost terminator). Statement
                    // bodies -- e.g. a trigger's BEGIN ... END -- keep their inner ';'.
                    if (t.endsWith(";")) t = t.substring(0, t.length() - 1).trim();
                    if (t.isEmpty()) continue;
                    try {
                        db.execSQL(t);
                    } catch (Throwable err) {
                        Log.w(TAG, "execStatements: '" + t + "' -> " + err);
                    }
                }
            } finally {
                try {
                    db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)");
                } catch (Throwable ignored) {
                }
                db.close();
            }
        }

        // --- filesystem primitives (replace stat/chown/rm shell-outs) ---

        @Override
        public int[] statOwner(String path) throws RemoteException {
            try {
                StructStat st = Os.stat(path);
                return new int[]{st.st_uid, st.st_gid};
            } catch (ErrnoException e) {
                throw new RemoteException("statOwner(" + path + ") failed: " + e.getMessage());
            }
        }

        @Override
        public void chownPath(String path, int uid, int gid) throws RemoteException {
            try {
                Os.chown(path, uid, gid);
            } catch (ErrnoException e) {
                throw new RemoteException("chownPath(" + path + ") failed: " + e.getMessage());
            }
        }

        @Override
        public boolean deleteRecursive(String path) throws RemoteException {
            // Guard against catastrophic deletes: refuse empty/whitespace, the filesystem
            // root, traversal paths, and anything outside the GMS data-dir allowlist.
            if (isUnsafeDeletePath(path)) {
                throw new RemoteException("deleteRecursive refused unsafe path: '" + path + "'");
            }
            return deleteTree(new File(path.trim()));
        }

        /**
         * Depth-first delete that never follows symlinks. As root, descending through a
         * symlink would delete the link's TARGET (which may lie outside the requested tree),
         * so we {@code lstat} every child and, when it is a symlink, delete the link itself
         * without recursing. Returns true if nothing remains afterwards.
         */
        static boolean deleteTree(File f) {
            String path = f.getPath();
            StructStat st;
            try {
                st = Os.lstat(path);
            } catch (ErrnoException e) {
                // ENOENT -> already gone (success); anything else -> couldn't stat (failure).
                return e.errno == OsConstants.ENOENT;
            }
            if (shouldDescend(st.st_mode)) {
                String[] children = f.list(); // names only; does not stat-through-links
                if (children != null) {
                    for (String name : children) deleteTree(new File(f, name));
                }
            }
            // Whether it's a regular file, a symlink, or a now-empty real directory, delete
            // the entry itself (never the symlink's target tree).
            return new File(path).delete();
        }

        /**
         * Produces a consistent, restorable backup of a (possibly live) SQLite DB:
         * <ol>
         *   <li>Best-effort {@code PRAGMA wal_checkpoint(TRUNCATE)} on the source so the
         *       main file holds the full committed state and the {@code -wal} sidecar is
         *       emptied (handles the case where GMS left an uncheckpointed WAL).</li>
         *   <li>Copies the main file <em>and</em> any {@code -wal}/{@code -shm}/{@code -journal}
         *       sidecars alongside it, so the set is restorable even if a sidecar still
         *       carries uncommitted frames.</li>
         * </ol>
         * Each file is written to a {@code .part} temp, fsynced, closed, then atomically
         * renamed into place, and the temp is removed on any failure -- so a torn/partial
         * file never appears under the real {@code .bak} name. When {@code uid >= 0} the
         * written files are {@code chown}ed to that app uid/gid so the app can later prune
         * and restore them.
         */
        @Override
        public void backupFile(String srcPath, String destPath, int uid, int gid)
                throws RemoteException {
            File dest = new File(destPath);
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            // 1. Checkpoint the source so the main file is self-consistent (best-effort).
            checkpointQuietly(srcPath);

            // 2. Copy the main file (must succeed) ...
            copyOneAtomic(srcPath, destPath, uid, gid);
            // ... then any sidecars that exist, so the snapshot is fully restorable.
            for (String ext : DB_SIDECARS) {
                File side = new File(srcPath + ext);
                if (side.exists()) {
                    copyOneAtomic(side.getPath(), destPath + ext, uid, gid);
                }
            }
        }

        /** Opens the DB read-only-ish and truncates its WAL so the main file is consistent. */
        private static void checkpointQuietly(String dbPath) {
            if (!new File(dbPath).exists()) return;
            SQLiteDatabase db = null;
            try {
                db = openRW(dbPath);
                db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (Throwable t) {
                Log.w(TAG, "checkpoint before backup of " + dbPath + " failed (continuing): " + t);
            } finally {
                if (db != null) {
                    try { db.close(); } catch (Throwable ignored) {}
                }
            }
        }

        /**
         * Copies {@code src} -> {@code dest} via {@code dest + ".part"} + fsync + atomic
         * rename, deleting the temp on any failure so no partial file lands under the final
         * name. Optionally chowns the result to {@code uid}/{@code gid} (when {@code uid >= 0}).
         */
        private static void copyOneAtomic(String src, String dest, int uid, int gid)
                throws RemoteException {
            File tmp = new File(dest + ".part");
            try (FileInputStream in = new FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                out.getFD().sync();
            } catch (IOException e) {
                tmp.delete();
                throw new RemoteException("backupFile(" + src + " -> " + dest + ") failed: " + e.getMessage());
            }
            if (!tmp.renameTo(new File(dest))) {
                tmp.delete();
                throw new RemoteException("backupFile rename failed: " + tmp + " -> " + dest);
            }
            if (uid >= 0) {
                try {
                    Os.chown(dest, uid, gid);
                } catch (ErrnoException e) {
                    // Non-fatal: the copy succeeded; ownership just couldn't be handed back.
                    Log.w(TAG, "chown of backup " + dest + " to " + uid + ":" + gid + " failed: " + e.getMessage());
                }
            }
        }
    }
}

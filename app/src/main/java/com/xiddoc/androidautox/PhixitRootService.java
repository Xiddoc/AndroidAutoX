package com.xiddoc.androidautox;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.util.Log;

import com.topjohnwu.superuser.ipc.RootService;

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
            SQLiteDatabase db = openRW(PhixitEngine.PHENO_DB);
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
            SQLiteDatabase db = openRW(PhixitEngine.PHENO_DB);
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
    }
}

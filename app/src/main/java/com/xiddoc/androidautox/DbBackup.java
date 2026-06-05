package com.xiddoc.androidautox;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safety-net backup of a GMS / Gearhead database before this app edits it.
 *
 * <p>Default opted-in: the {@code auto_backup_dbs} preference defaults to {@code true},
 * so a copy of every DB this app mutates is stashed in app-private storage before the
 * edit. The user can turn it off (see {@link #isEnabled(SharedPreferences)} /
 * {@link #setEnabled(Context, boolean)}); a UI toggle is expected to be wired by the
 * activity owner (TODO: surface in About/settings — this class only provides the pref).
 *
 * <p><b>Where it runs.</b> {@link #backupBeforeEdit(String)} is invoked automatically at
 * the {@link RootDb} mutation choke point ({@code writePartitions} / {@code exec} /
 * {@code execStatements}) <em>before</em> the edit is delegated to the root service, so
 * EVERY DB mutation — phenotype partition writes, raw phenotype edits, and CarRemover's
 * {@code carservicedata.db} delete — gets the safety net. Read-only paths
 * ({@code query} / {@code readPartitions}) never back up.
 *
 * <p><b>What the backup guarantees.</b> The actual copy runs in the root process (the DB
 * is GMS-private). It first checkpoints the source's WAL into the main file, then copies
 * the main DB file <em>and</em> any {@code -wal}/{@code -shm}/{@code -journal} sidecars as
 * an atomically-renamed, fsynced, restorable set, and chowns them back to the app uid so
 * the app can prune/restore them. Any failure is logged and swallowed — a backup problem
 * must never block the tweak itself.
 *
 * <p>Backups land in {@code <filesDir>/db-backups/<dbname>.<UTC-timestamp-with-millis>.bak}
 * and are pruned to the most recent {@link #DEFAULT_RETENTION} per database. The millisecond
 * precision means two edits in the same second get distinct backups (no overwrite).
 *
 * <p>The pure pieces (filename construction, the retention/prune policy, the pref-gating
 * decision) are static and side-effect-free so they can be unit-tested off-device. The
 * root-touching copy is injected via {@link Copier} (default {@link RootDb#backupFile})
 * so {@link #backupBeforeEdit(String)}'s swallow-and-prune behavior is also testable.
 */
public class DbBackup {

    private static final String TAG = "AndroidAutoX";

    /** Preference key for the default-on auto-backup safety net. */
    public static final String PREF_AUTO_BACKUP = "auto_backup_dbs";

    /** Default-on: users are opted in to backups unless they turn this off. */
    public static final boolean DEFAULT_AUTO_BACKUP = true;

    /** Sub-directory under {@code filesDir} where backups are stored. */
    public static final String BACKUP_DIR = "db-backups";

    /** Suffix appended to every backup file. */
    public static final String BACKUP_SUFFIX = ".bak";

    /** Keep at most this many backups per database; older ones are pruned. */
    public static final int DEFAULT_RETENTION = 5;

    /**
     * UTC timestamp format used in backup filenames (filesystem-safe, sortable). Includes
     * milliseconds so two edits within the same second get distinct, non-colliding names.
     */
    private static final String TS_PATTERN = "yyyyMMdd'T'HHmmssSSS'Z'";

    /**
     * Matches {@code <dbname>.<timestamp>.bak}, capturing the timestamp. The timestamp is
     * the lexicographically-sortable {@link #TS_PATTERN} (8 date digits, 'T', 9 time digits
     * incl. millis, 'Z'), so string order == chronological order and we don't need to parse
     * dates to rank backups.
     */
    private static final Pattern BACKUP_NAME =
            Pattern.compile("^.+\\.(\\d{8}T\\d{9}Z)" + Pattern.quote(BACKUP_SUFFIX) + "$");

    /**
     * The root-touching file copy, abstracted for testability. Signature mirrors
     * {@link RootDb#backupFile(String, String, int, int)}; the default delegates to it.
     */
    interface Copier {
        void copy(String srcPath, String destPath, int uid, int gid) throws Exception;
    }

    private static final Copier DEFAULT_COPIER = RootDb::backupFile;

    private final Context ctx;
    private final int retention;
    private final Copier copier;

    public DbBackup(Context ctx) {
        this(ctx, DEFAULT_RETENTION, DEFAULT_COPIER);
    }

    public DbBackup(Context ctx, int retention) {
        this(ctx, retention, DEFAULT_COPIER);
    }

    /** Test seam: inject a fake {@link Copier} to exercise the swallow/prune logic in a JVM. */
    DbBackup(Context ctx, int retention, Copier copier) {
        this.ctx = ctx.getApplicationContext();
        this.retention = retention;
        this.copier = copier != null ? copier : DEFAULT_COPIER;
    }

    /** Activity.getPreferences() stores under MainActivity's class name; share that file. */
    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE);
    }

    // --- pref gating (pure decision + side-effecting accessors) ---------------

    /** Pure: should we back up given this prefs snapshot? Defaults on when unset. */
    public static boolean isEnabled(SharedPreferences sp) {
        return sp.getBoolean(PREF_AUTO_BACKUP, DEFAULT_AUTO_BACKUP);
    }

    /** Persist the user's choice (for a future settings toggle). */
    public static void setEnabled(Context ctx, boolean enabled) {
        ctx.getApplicationContext()
                .getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_AUTO_BACKUP, enabled).apply();
    }

    public boolean isEnabled() {
        return isEnabled(prefs());
    }

    // --- pure path/name helpers (unit-testable) -------------------------------

    /** UTC timestamp string for {@code epochMillis} in {@link #TS_PATTERN}. */
    public static String timestamp(long epochMillis) {
        SimpleDateFormat f = new SimpleDateFormat(TS_PATTERN, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(epochMillis));
    }

    /** The base filename of a DB path, e.g. {@code /a/b/phenotype.db -> phenotype.db}. */
    public static String dbName(String dbPath) {
        if (dbPath == null) return "";
        int slash = dbPath.lastIndexOf('/');
        return slash >= 0 ? dbPath.substring(slash + 1) : dbPath;
    }

    /** {@code <dbname>.<UTC-timestamp>.bak} for a DB path + time. */
    public static String backupFileName(String dbPath, long epochMillis) {
        return dbName(dbPath) + "." + timestamp(epochMillis) + BACKUP_SUFFIX;
    }

    /** Absolute destination path for a backup of {@code dbPath} taken at {@code epochMillis}. */
    public static String backupDestPath(File backupDir, String dbPath, long epochMillis) {
        return new File(backupDir, backupFileName(dbPath, epochMillis)).getAbsolutePath();
    }

    /** True if {@code fileName} is a backup of the database file {@code dbName}. */
    public static boolean isBackupOf(String dbName, String fileName) {
        if (fileName == null || dbName == null) return false;
        if (!fileName.startsWith(dbName + ".") || !fileName.endsWith(BACKUP_SUFFIX)) return false;
        return BACKUP_NAME.matcher(fileName).matches();
    }

    /**
     * Pure retention policy: given the existing backup filenames for one database and the
     * keep-count {@code retain}, returns the names that should be PRUNED (oldest first).
     * The newest {@code retain} are kept. Names are ranked by their embedded timestamp,
     * which sorts lexicographically (see {@link #TS_PATTERN}). Non-matching names are
     * ignored (never pruned). A non-positive {@code retain} prunes everything matching.
     */
    public static List<String> selectForPruning(List<String> dbBackupNames, int retain) {
        List<String> matches = new ArrayList<>();
        for (String n : dbBackupNames) {
            if (n != null && BACKUP_NAME.matcher(n).matches()) matches.add(n);
        }
        // Sort oldest-first by the embedded timestamp; the newest `keep` are retained at
        // the tail, so everything before the cutoff is pruned (oldest first).
        Collections.sort(matches, (a, b) -> tsOf(a).compareTo(tsOf(b)));
        int keep = Math.max(retain, 0);
        int cutoff = Math.max(matches.size() - keep, 0);
        return new ArrayList<>(matches.subList(0, cutoff));
    }

    private static String tsOf(String name) {
        Matcher m = BACKUP_NAME.matcher(name);
        return m.matches() ? m.group(1) : "";
    }

    // --- the one root-touching entry point ------------------------------------

    /**
     * Backs up {@code dbPath} to app-private storage if the pref is enabled, then prunes
     * old backups of that DB to {@link #retention}. Non-fatal by contract: any failure is
     * logged and swallowed (returns {@code false}) so the caller's edit always proceeds.
     *
     * <p>The backup destination directory is created <em>app-side</em> here (so it is owned
     * by the app, not by root), and the written backup files are chowned back to the app
     * uid/gid in the root process so a later prune/restore by the app succeeds.
     *
     * @return {@code true} if a backup was written; {@code false} if disabled or it failed.
     */
    public boolean backupBeforeEdit(String dbPath) {
        if (!isEnabled()) return false;
        File dir = new File(ctx.getFilesDir(), BACKUP_DIR);
        // Create the dir app-side (NOT via a root mkdirs) so it stays app-owned.
        dir.mkdirs();
        String dest = backupDestPath(dir, dbPath, System.currentTimeMillis());
        int uid = Process.myUid();
        try {
            copier.copy(dbPath, dest, uid, uid);
        } catch (Exception e) {
            // Surface but do not block the tweak. This is the only place a default-on
            // safety net can fail; logging it (rather than swallowing silently) keeps it
            // visible in logcat / the streamed logs.
            Log.w(TAG, "WARNING: DB backup of " + dbPath + " failed (continuing without backup): " + e);
            return false;
        }
        // Prune is non-fatal by contract: it logs and swallows its own failures (including a
        // failed directory listing) so a prune problem never undoes the just-written backup.
        pruneOldBackups(dir, dbName(dbPath));
        return true;
    }

    /** Deletes everything {@link #selectForPruning} flags for {@code dbName} in {@code dir}. */
    private void pruneOldBackups(File dir, String dbName) {
        String[] names;
        try {
            names = dir.list();
        } catch (Exception e) {
            // e.g. a SecurityException from list(); pruning is best-effort -- log and bail.
            Log.w(TAG, "Listing backups in " + dir + " for prune failed: " + e);
            return;
        }
        if (names == null) return;
        List<String> all = new ArrayList<>();
        for (String n : names) {
            if (isBackupOf(dbName, n)) all.add(n);
        }
        for (String prune : selectForPruning(all, retention)) {
            File f = new File(dir, prune);
            if (!f.delete()) Log.w(TAG, "Could not prune backup " + f);
        }
    }
}

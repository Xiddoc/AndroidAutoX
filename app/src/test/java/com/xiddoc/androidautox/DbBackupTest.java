package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests for the pure, root-free logic of {@link DbBackup}: timestamp/filename
 * construction, the retention/prune policy, backup-name recognition, and the
 * default-on pref-gating decision. The actual byte copy + {@code Os} calls run in
 * the root process and are not exercised here.
 */
@RunWith(RobolectricTestRunner.class)
public class DbBackupTest {

    private static final String PHENO = GmsPaths.PHENO_DB; // .../phenotype.db

    // --- filename / path construction ------------------------------------

    @Test
    public void timestamp_isUtcAndSortable() {
        // 2021-01-01T00:00:00.000Z == 1609459200000 ms (millis included for uniqueness).
        assertEquals("20210101T000000000Z", DbBackup.timestamp(1609459200000L));
    }

    @Test
    public void dbName_takesBasename() {
        assertEquals("phenotype.db", DbBackup.dbName(PHENO));
        assertEquals("carservicedata.db",
                DbBackup.dbName("/data/data/x/databases/carservicedata.db"));
        assertEquals("nopath.db", DbBackup.dbName("nopath.db"));
    }

    @Test
    public void backupFileName_combinesNameTimestampSuffix() {
        assertEquals("phenotype.db.20210101T000000000Z.bak",
                DbBackup.backupFileName(PHENO, 1609459200000L));
    }

    @Test
    public void backupDestPath_isUnderBackupDir() {
        File dir = new File("/tmp/files/db-backups");
        String dest = DbBackup.backupDestPath(dir, PHENO, 1609459200000L);
        assertEquals("/tmp/files/db-backups/phenotype.db.20210101T000000000Z.bak", dest);
    }

    // --- sub-second uniqueness (no collision within the same second) -----

    @Test
    public void backupFileName_distinctWithinSameSecond() {
        long t0 = 1609459200000L;          // 00:00:00.000
        long t1 = t0 + 123L;               // 00:00:00.123 (same wall-clock second)
        String a = DbBackup.backupFileName(PHENO, t0);
        String b = DbBackup.backupFileName(PHENO, t1);
        assertFalse("two edits in the same second must not collide", a.equals(b));
        // Both must still be recognized as backups of this DB.
        assertTrue(DbBackup.isBackupOf("phenotype.db", a));
        assertTrue(DbBackup.isBackupOf("phenotype.db", b));
        // Lexicographic order still tracks chronological order.
        assertTrue(a.compareTo(b) < 0);
    }

    // --- backup-name recognition -----------------------------------------

    @Test
    public void isBackupOf_matchesOnlyOwnDb() {
        assertTrue(DbBackup.isBackupOf("phenotype.db", "phenotype.db.20210101T000000000Z.bak"));
        // wrong db name
        assertFalse(DbBackup.isBackupOf("carservicedata.db", "phenotype.db.20210101T000000000Z.bak"));
        // not a backup file
        assertFalse(DbBackup.isBackupOf("phenotype.db", "phenotype.db"));
        // missing timestamp / suffix
        assertFalse(DbBackup.isBackupOf("phenotype.db", "phenotype.db.bak"));
        assertFalse(DbBackup.isBackupOf("phenotype.db", "phenotype.db.20210101T000000000Z"));
        // old (millis-less) timestamp format is no longer recognized
        assertFalse(DbBackup.isBackupOf("phenotype.db", "phenotype.db.20210101T000000Z.bak"));
    }

    // --- retention / prune policy ----------------------------------------

    private static String bak(long ms) {
        return DbBackup.backupFileName(PHENO, ms);
    }

    @Test
    public void selectForPruning_keepsNewestN() {
        // Six backups, one day apart. Keep 5 -> the single oldest is pruned.
        long day = 86_400_000L;
        long base = 1609459200000L; // 2021-01-01
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 6; i++) names.add(bak(base + i * day));
        List<String> prune = DbBackup.selectForPruning(names, 5);
        assertEquals(1, prune.size());
        assertEquals(bak(base), prune.get(0)); // the oldest
    }

    @Test
    public void selectForPruning_underLimit_prunesNothing() {
        long day = 86_400_000L, base = 1609459200000L;
        List<String> names = Arrays.asList(bak(base), bak(base + day), bak(base + 2 * day));
        assertTrue(DbBackup.selectForPruning(names, 5).isEmpty());
    }

    @Test
    public void selectForPruning_ordersByTimestampNotInputOrder() {
        long day = 86_400_000L, base = 1609459200000L;
        // Deliberately shuffled input; oldest two should be the ones pruned when keeping 1.
        List<String> names = Arrays.asList(
                bak(base + 2 * day), bak(base), bak(base + 1 * day));
        List<String> prune = DbBackup.selectForPruning(names, 1);
        assertEquals(2, prune.size());
        // oldest first
        assertEquals(bak(base), prune.get(0));
        assertEquals(bak(base + day), prune.get(1));
    }

    @Test
    public void dbName_null_returnsEmpty() {
        assertEquals("", DbBackup.dbName(null));
    }

    @Test
    public void isBackupOf_nullArgs_returnFalse() {
        assertFalse(DbBackup.isBackupOf("phenotype.db", null));
        assertFalse(DbBackup.isBackupOf(null, "phenotype.db.20210101T000000000Z.bak"));
    }

    @Test
    public void selectForPruning_ignoresNonBackupNames() {
        long base = 1609459200000L;
        List<String> names = Arrays.asList(bak(base), "phenotype.db", "random.txt", null);
        // Only one real backup; keeping 5 prunes nothing and never trips on junk/null.
        assertTrue(DbBackup.selectForPruning(names, 5).isEmpty());
    }

    @Test
    public void selectForPruning_zeroRetention_prunesAllBackups() {
        long day = 86_400_000L, base = 1609459200000L;
        List<String> names = Arrays.asList(bak(base), bak(base + day), "notabackup");
        List<String> prune = DbBackup.selectForPruning(names, 0);
        assertEquals(2, prune.size());
    }

    // --- pref-gating decision (default-on) -------------------------------

    private SharedPreferences prefs() {
        Context ctx = ApplicationProvider.getApplicationContext();
        return ctx.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE);
    }

    @Test
    public void isEnabled_defaultsTrueWhenUnset() {
        assertTrue("backups must be opted-in by default", DbBackup.isEnabled(prefs()));
    }

    @Test
    public void isEnabled_reflectsStoredFalse() {
        Context ctx = ApplicationProvider.getApplicationContext();
        DbBackup.setEnabled(ctx, false);
        assertFalse(DbBackup.isEnabled(prefs()));
        DbBackup.setEnabled(ctx, true);
        assertTrue(DbBackup.isEnabled(prefs()));
    }

    @Test
    public void instanceIsEnabled_matchesStaticDecision() {
        Context ctx = ApplicationProvider.getApplicationContext();
        DbBackup.setEnabled(ctx, false);
        assertFalse(new DbBackup(ctx).isEnabled());
        DbBackup.setEnabled(ctx, true);
        assertTrue(new DbBackup(ctx).isEnabled());
    }

    // --- backupBeforeEdit: injected-copy behavior (swallow + prune) ------

    /** Records every copy call so tests can assert the file-copy was/ wasn't invoked. */
    private static final class RecordingCopier implements DbBackup.Copier {
        final List<String> dests = new ArrayList<>();
        final boolean throwOnCopy;
        RecordingCopier(boolean throwOnCopy) { this.throwOnCopy = throwOnCopy; }
        @Override
        public void copy(String srcPath, String destPath, int uid, int gid) throws Exception {
            dests.add(destPath);
            if (throwOnCopy) throw new RuntimeException("simulated root copy failure");
            // Simulate the root process writing the file so prune has something to act on.
            File f = new File(destPath);
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) {
                os.write(new byte[]{1, 2, 3});
            }
        }
    }

    @Test
    public void backupBeforeEdit_disabled_doesNotCopy_andReturnsFalse() {
        Context ctx = ApplicationProvider.getApplicationContext();
        DbBackup.setEnabled(ctx, false);
        RecordingCopier copier = new RecordingCopier(false);
        DbBackup b = new DbBackup(ctx, DbBackup.DEFAULT_RETENTION, copier);
        assertFalse(b.backupBeforeEdit(PHENO));
        assertTrue("disabled must not invoke the copier", copier.dests.isEmpty());
        DbBackup.setEnabled(ctx, true);
    }

    @Test
    public void backupBeforeEdit_copyThrows_swallowed_returnsFalse() {
        Context ctx = ApplicationProvider.getApplicationContext();
        DbBackup.setEnabled(ctx, true);
        RecordingCopier copier = new RecordingCopier(true);
        DbBackup b = new DbBackup(ctx, DbBackup.DEFAULT_RETENTION, copier);
        // Failure is swallowed (no exception thrown) so the caller's edit can proceed.
        assertFalse(b.backupBeforeEdit(PHENO));
        assertEquals("copy was attempted once", 1, copier.dests.size());
    }

    @Test
    public void twoArgConstructor_usesGivenRetention_andDefaultCopier() {
        Context ctx = ApplicationProvider.getApplicationContext();
        DbBackup.setEnabled(ctx, true);
        // The (ctx, retention) ctor wires the default (root) copier. We don't invoke the
        // copy here -- just prove the object is usable and honours the pref decision.
        DbBackup b = new DbBackup(ctx, 3);
        assertTrue(b.isEnabled());
    }

    @Test
    public void threeArgConstructor_nullCopier_fallsBackToDefault() {
        Context ctx = ApplicationProvider.getApplicationContext();
        DbBackup.setEnabled(ctx, false); // disabled so backupBeforeEdit short-circuits, no root call
        DbBackup b = new DbBackup(ctx, DbBackup.DEFAULT_RETENTION, null);
        // Disabled path returns false without ever touching the (default/root) copier.
        assertFalse(b.backupBeforeEdit(PHENO));
        DbBackup.setEnabled(ctx, true);
    }

    @Test
    public void backupBeforeEdit_pruneSkipsForeignBackups_andToleratesUndeletable() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        DbBackup.setEnabled(ctx, true);
        RecordingCopier copier = new RecordingCopier(false);
        DbBackup b = new DbBackup(ctx, 1, copier); // keep only the newest phenotype backup
        File dir = new File(ctx.getFilesDir(), DbBackup.BACKUP_DIR);
        if (dir.exists()) for (File f : dir.listFiles()) deleteTree(f);
        dir.mkdirs();

        // A backup of a DIFFERENT db must be ignored by this db's prune (isBackupOf false branch).
        File foreign = new File(dir, DbBackup.backupFileName(
                "/x/carservicedata.db", 1609459200000L));
        try (java.io.FileOutputStream os = new java.io.FileOutputStream(foreign)) {
            os.write(1);
        }
        // An OLD phenotype backup that cannot be deleted (a non-empty directory named like a
        // backup) -- exercises the "could not prune" tolerance (delete() returns false).
        File undeletable = new File(dir, DbBackup.backupFileName(PHENO, 1L));
        undeletable.mkdirs();
        new File(undeletable, "child").createNewFile();

        // Now write two fresh phenotype backups; with retention 1, prune targets the older
        // ones (including the undeletable dir) but must not throw and must spare the foreign one.
        b.backupBeforeEdit(PHENO);
        Thread.sleep(2);
        b.backupBeforeEdit(PHENO);

        assertTrue("foreign-db backup must survive this db's prune", foreign.exists());
        assertTrue("undeletable old backup tolerated (logged, not fatal)", undeletable.exists());

        // cleanup
        for (File f : dir.listFiles()) deleteTree(f);
    }

    private static void deleteTree(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteTree(k);
        }
        f.delete();
    }

    @Test
    public void backupBeforeEdit_copyOk_returnsTrue_andPrunesToRetention() {
        Context ctx = ApplicationProvider.getApplicationContext();
        DbBackup.setEnabled(ctx, true);
        RecordingCopier copier = new RecordingCopier(false);
        // Retention of 2: after several backups only the 2 newest survive in the dir.
        DbBackup b = new DbBackup(ctx, 2, copier);
        File dir = new File(ctx.getFilesDir(), DbBackup.BACKUP_DIR);
        // Clean any leftovers from other tests.
        if (dir.exists()) for (File f : dir.listFiles()) f.delete();

        for (int i = 0; i < 4; i++) {
            assertTrue(b.backupBeforeEdit(PHENO));
            // Ensure distinct millis-level timestamps so prune ranks them.
            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        }
        // 4 copies attempted ...
        assertEquals(4, copier.dests.size());
        // ... but only `retention` (2) phenotype backups remain on disk after pruning.
        int remaining = 0;
        for (String n : dir.list()) {
            if (DbBackup.isBackupOf("phenotype.db", n)) remaining++;
        }
        assertEquals("prune keeps only the newest `retention` backups", 2, remaining);
    }

    // --- defensive internals (white-box: real behavior of the private prune helper) ---

    /** A File whose listing returns null (missing dir) or throws, to drive prune's defenses. */
    private static final class ListingFile extends File {
        private final boolean throwOnList;
        ListingFile(String pathname, boolean throwOnList) {
            super(pathname);
            this.throwOnList = throwOnList;
        }
        @Override
        public String[] list() {
            if (throwOnList) throw new RuntimeException("simulated list() failure (e.g. SecurityException)");
            return null; // simulate "not a directory" / unreadable -> null listing
        }
    }

    @Test
    public void pruneOldBackups_nullListing_returnsWithoutDeleting() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        DbBackup b = new DbBackup(ctx, 1);
        Method prune = DbBackup.class.getDeclaredMethod("pruneOldBackups", File.class, String.class);
        prune.setAccessible(true);
        // dir.list() == null -> the method must early-return cleanly (no exception).
        prune.invoke(b, new ListingFile("/no/such/dir", false), "phenotype.db");
    }

    @Test
    public void pruneOldBackups_listingThrows_isSwallowed() throws Exception {
        // A dir.list() that throws (e.g. SecurityException) must be logged and swallowed so a
        // prune failure never undoes the just-written backup.
        Context ctx = ApplicationProvider.getApplicationContext();
        DbBackup b = new DbBackup(ctx, 1);
        Method prune = DbBackup.class.getDeclaredMethod("pruneOldBackups", File.class, String.class);
        prune.setAccessible(true);
        // Must not propagate the exception.
        prune.invoke(b, new ListingFile(System.getProperty("java.io.tmpdir"), true), "phenotype.db");
    }

    @Test
    public void tsOf_nonMatchingName_returnsEmptyString() throws Exception {
        Method tsOf = DbBackup.class.getDeclaredMethod("tsOf", String.class);
        tsOf.setAccessible(true);
        // A name that does not match the backup pattern yields "" (the defensive else branch).
        assertEquals("", tsOf.invoke(null, "not-a-backup-name"));
        // Sanity: a matching name yields its embedded timestamp.
        assertEquals("20210101T000000000Z",
                tsOf.invoke(null, "phenotype.db.20210101T000000000Z.bak"));
    }
}

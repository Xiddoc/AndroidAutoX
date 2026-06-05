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

    private static final String PHENO = PhixitEngine.PHENO_DB; // .../phenotype.db

    // --- filename / path construction ------------------------------------

    @Test
    public void timestamp_isUtcAndSortable() {
        // 2021-01-01T00:00:00Z == 1609459200000 ms.
        assertEquals("20210101T000000Z", DbBackup.timestamp(1609459200000L));
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
        assertEquals("phenotype.db.20210101T000000Z.bak",
                DbBackup.backupFileName(PHENO, 1609459200000L));
    }

    @Test
    public void backupDestPath_isUnderBackupDir() {
        File dir = new File("/tmp/files/db-backups");
        String dest = DbBackup.backupDestPath(dir, PHENO, 1609459200000L);
        assertEquals("/tmp/files/db-backups/phenotype.db.20210101T000000Z.bak", dest);
    }

    // --- backup-name recognition -----------------------------------------

    @Test
    public void isBackupOf_matchesOnlyOwnDb() {
        assertTrue(DbBackup.isBackupOf("phenotype.db", "phenotype.db.20210101T000000Z.bak"));
        // wrong db name
        assertFalse(DbBackup.isBackupOf("carservicedata.db", "phenotype.db.20210101T000000Z.bak"));
        // not a backup file
        assertFalse(DbBackup.isBackupOf("phenotype.db", "phenotype.db"));
        // missing timestamp / suffix
        assertFalse(DbBackup.isBackupOf("phenotype.db", "phenotype.db.bak"));
        assertFalse(DbBackup.isBackupOf("phenotype.db", "phenotype.db.20210101T000000Z"));
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
}

package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-logic tests for {@link HeterodyneSyncState}: the flag-set-change detector and the
 * committed-config invalidation SQL builder used to keep GMS's Phenotype Heterodyne
 * sync coherent after this app rewrites {@code param_partitions.flags_content}.
 */
public class HeterodyneSyncStateTest {

    private PhixitSnapshot.Flag flag(String name) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.numericName = false;
        f.type = PhixitSnapshot.TYPE_BOOL_TRUE;
        return f;
    }

    private PhixitSnapshot.Flag numericFlag(String name) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.numericName = true;
        f.type = PhixitSnapshot.TYPE_BOOL_TRUE;
        return f;
    }

    // --- flagSetChanged --------------------------------------------------------

    @Test
    public void flagSetChanged_detectsAddAndRemove() {
        List<PhixitSnapshot.Flag> one = Arrays.asList(flag("a"));
        List<PhixitSnapshot.Flag> two = Arrays.asList(flag("a"), flag("b"));
        // added a flag name -> changed
        assertTrue(HeterodyneSyncState.flagSetChanged(one, two));
        // removed a flag name -> changed
        assertTrue(HeterodyneSyncState.flagSetChanged(two, one));
    }

    @Test
    public void flagSetChanged_sameCountSwap_isTrue() {
        // remove flag A, add flag B in the same edit: net count unchanged but the flag
        // NAME set changed, which still trips Heterodyne. A bare count check would miss this.
        List<PhixitSnapshot.Flag> before = Arrays.asList(flag("a"));
        List<PhixitSnapshot.Flag> after = Arrays.asList(flag("b"));
        assertTrue(HeterodyneSyncState.flagSetChanged(before, after));
    }

    @Test
    public void flagSetChanged_valueOnlyEdit_isFalse() {
        // Same set of names (a, b) before and after -> a pure value edit, no set change.
        List<PhixitSnapshot.Flag> before = Arrays.asList(flag("a"), flag("b"));
        List<PhixitSnapshot.Flag> after = Arrays.asList(flag("b"), flag("a"));
        assertFalse(HeterodyneSyncState.flagSetChanged(before, after));
    }

    @Test
    public void flagSetChanged_numericNameDifferences_ignored() {
        // Numeric-named flags don't participate in the name set (mirroring applySpecToList /
        // findFlag), so adding/removing/swapping them must NOT register as a change.
        List<PhixitSnapshot.Flag> before = Arrays.asList(flag("a"), numericFlag("7"));
        List<PhixitSnapshot.Flag> after = Arrays.asList(flag("a"), numericFlag("99"));
        assertFalse(HeterodyneSyncState.flagSetChanged(before, after));

        // A real name change is still caught even when numeric noise is present.
        List<PhixitSnapshot.Flag> changed = Arrays.asList(flag("b"), numericFlag("99"));
        assertTrue(HeterodyneSyncState.flagSetChanged(before, changed));
    }

    @Test
    public void flagSetChanged_nullsTreatedAsEmpty() {
        assertFalse(HeterodyneSyncState.flagSetChanged(null, null));
        assertFalse(HeterodyneSyncState.flagSetChanged(null, new ArrayList<PhixitSnapshot.Flag>()));
        assertTrue(HeterodyneSyncState.flagSetChanged(null, Arrays.asList(flag("a"))));
        assertTrue(HeterodyneSyncState.flagSetChanged(Arrays.asList(flag("a")), null));
    }

    // --- clearSyncSql ----------------------------------------------------------

    @Test
    public void clearSyncSql_nullPackages_isEmpty() {
        assertTrue(HeterodyneSyncState.clearSyncSql(null).isEmpty());
    }

    @Test
    public void clearSyncSql_emptyAndOnlyNulls_isEmpty() {
        assertTrue(HeterodyneSyncState.clearSyncSql(new ArrayList<String>()).isEmpty());
        assertTrue(HeterodyneSyncState.clearSyncSql(Arrays.asList((String) null)).isEmpty());
    }

    @Test
    public void clearSyncSql_singlePackage_emitsThreeNonDestructiveDeletes() {
        List<String> sql = HeterodyneSyncState.clearSyncSql(
                Arrays.asList(FlagSpec.PKG_GEARHEAD));
        // The exact table/column names are inferred and expected to change after on-device
        // verification, so we assert the behavioural invariants rather than the literal SQL.
        assertEquals(3, sql.size());
        String lit = "'" + FlagSpec.PKG_GEARHEAD + "'";
        boolean referencesStaticConfigPackages = false;
        for (String s : sql) {
            assertTrue("each statement is a DELETE FROM: " + s, s.startsWith("DELETE FROM"));
            assertTrue("each statement embeds the quote-doubled package literal: " + s,
                    s.contains(lit));
            assertFalse("must not touch param_partitions: " + s, s.contains("param_partitions"));
            assertFalse("must not force a re-fetch: " + s, s.contains("last_fetch"));
            assertFalse("must not reset fetch timestamps: " + s, s.contains("last_update_time"));
            if (s.contains("static_config_packages")) referencesStaticConfigPackages = true;
        }
        // The modern-linkage statement is the one meaningful linkage (the table this app
        // already joins param_partitions through), so keep that single check.
        assertTrue("a statement references the static_config_packages linkage",
                referencesStaticConfigPackages);
    }

    @Test
    public void clearSyncSql_isNonDestructive_neverTouchesParamPartitionsOrForcesRefetch() {
        // Risk #1: must not overwrite our edit (param_partitions) nor force a server
        // re-fetch (last_fetch.last_update_time). Every statement is a committed-config
        // DELETE only.
        for (String s : HeterodyneSyncState.clearSyncSql(
                Arrays.asList(FlagSpec.PKG_GEARHEAD, FlagSpec.PKG_CAR))) {
            assertTrue("must be a DELETE: " + s, s.startsWith("DELETE FROM"));
            assertFalse("must not touch param_partitions: " + s, s.contains("param_partitions"));
            assertFalse("must not force a re-fetch: " + s, s.contains("last_fetch"));
            assertFalse("must not reset fetch timestamps: " + s, s.contains("last_update_time"));
        }
    }

    @Test
    public void clearSyncSql_dedupesAndSkipsNulls_preservingOrder() {
        List<String> sql = HeterodyneSyncState.clearSyncSql(Arrays.asList(
                FlagSpec.PKG_GEARHEAD, null, FlagSpec.PKG_GEARHEAD, FlagSpec.PKG_CAR));
        // two distinct packages * 3 statements each
        assertEquals(6, sql.size());
        // gearhead statements come first (insertion order), then car
        assertTrue(sql.get(2).contains(FlagSpec.PKG_GEARHEAD));
        assertTrue(sql.get(5).contains(FlagSpec.PKG_CAR));
    }
}

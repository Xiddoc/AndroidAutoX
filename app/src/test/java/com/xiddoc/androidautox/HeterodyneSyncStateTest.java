package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-logic tests for {@link HeterodyneSyncState}: the count-change detector and the
 * committed-config invalidation SQL builder used to keep GMS's Phenotype Heterodyne
 * sync coherent after this app rewrites {@code param_partitions.flags_content}.
 */
public class HeterodyneSyncStateTest {

    private PhixitSnapshot.Flag flag(String name) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.type = PhixitSnapshot.TYPE_BOOL_TRUE;
        return f;
    }

    // --- countChanged(int, int) ------------------------------------------------

    @Test
    public void countChanged_ints_trueWhenDifferent_falseWhenSame() {
        assertTrue(HeterodyneSyncState.countChanged(440, 453));
        assertTrue(HeterodyneSyncState.countChanged(453, 440));
        assertFalse(HeterodyneSyncState.countChanged(453, 453));
        assertFalse(HeterodyneSyncState.countChanged(0, 0));
    }

    // --- countChanged(List, List) ---------------------------------------------

    @Test
    public void countChanged_lists_detectsAddAndRemove_andEqualSize() {
        List<PhixitSnapshot.Flag> one = Arrays.asList(flag("a"));
        List<PhixitSnapshot.Flag> two = Arrays.asList(flag("a"), flag("b"));
        // added a flag -> changed
        assertTrue(HeterodyneSyncState.countChanged(one, two));
        // removed a flag -> changed
        assertTrue(HeterodyneSyncState.countChanged(two, one));
        // same size (value-only edit) -> unchanged
        assertFalse(HeterodyneSyncState.countChanged(one, Arrays.asList(flag("z"))));
    }

    @Test
    public void countChanged_lists_nullsTreatedAsEmpty() {
        assertFalse(HeterodyneSyncState.countChanged(null, null));
        assertFalse(HeterodyneSyncState.countChanged(null, new ArrayList<PhixitSnapshot.Flag>()));
        assertTrue(HeterodyneSyncState.countChanged(null, Arrays.asList(flag("a"))));
        assertTrue(HeterodyneSyncState.countChanged(Arrays.asList(flag("a")), null));
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
        assertEquals(3, sql.size());
        String lit = "'" + FlagSpec.PKG_GEARHEAD + "'";
        // Modern linkage (static_config_packages, the table param_partitions joins through).
        assertEquals("DELETE FROM committed_configurations WHERE static_config_package_id IN "
                        + "(SELECT static_config_package_id FROM static_config_packages WHERE name=" + lit + ")",
                sql.get(0));
        // Alternate linkage fallback.
        assertEquals("DELETE FROM committed_configurations WHERE config_package IN "
                        + "(SELECT id FROM config_packages WHERE name=" + lit + ")",
                sql.get(1));
        // Legacy flat table.
        assertEquals("DELETE FROM Configurations WHERE packageName=" + lit, sql.get(2));
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

    @Test
    public void sqlLiteral_doublesEmbeddedQuotes() {
        assertEquals("'plain'", HeterodyneSyncState.sqlLiteral("plain"));
        assertEquals("'O''Brien'", HeterodyneSyncState.sqlLiteral("O'Brien"));
    }
}

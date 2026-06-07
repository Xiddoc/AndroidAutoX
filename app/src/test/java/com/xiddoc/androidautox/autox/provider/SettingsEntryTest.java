package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tests for the pure shared {@link SettingsEntry}. */
public class SettingsEntryTest {

    @Test
    public void forAbsentKey_writeDefaultStrategy_revertsToDefault() {
        SettingsEntry e = SettingsEntry.forAbsentKey("k", 1);
        assertEquals("k", e.key);
        assertEquals(1, e.applyValue);
        assertEquals(SettingsEntry.RevertStrategy.WRITE_DEFAULT, e.revertStrategy);
        assertEquals(SettingsEntry.DEFAULT_REVERT_VALUE, e.revertValue());
        assertEquals(0, e.priorValue);
    }

    @Test
    public void forExistingKey_restorePriorStrategy_revertsToPrior() {
        SettingsEntry e = SettingsEntry.forExistingKey("k", 1, 5);
        assertEquals(SettingsEntry.RevertStrategy.RESTORE_PRIOR, e.revertStrategy);
        assertEquals(5, e.priorValue);
        assertEquals(5, e.revertValue());
    }

    @Test
    public void of_nullPrior_isAbsent() {
        SettingsEntry e = SettingsEntry.of("k", 1, null);
        assertEquals(SettingsEntry.RevertStrategy.WRITE_DEFAULT, e.revertStrategy);
    }

    @Test
    public void of_nonNullPrior_isExisting() {
        SettingsEntry e = SettingsEntry.of("k", 1, 3);
        assertEquals(SettingsEntry.RevertStrategy.RESTORE_PRIOR, e.revertStrategy);
        assertEquals(3, e.priorValue);
    }

    @Test
    public void asRevertEntry_writesRevertValueAsApply() {
        SettingsEntry absent = SettingsEntry.forAbsentKey("k", 1);
        SettingsEntry rev = absent.asRevertEntry();
        assertEquals("k", rev.key);
        assertEquals(SettingsEntry.DEFAULT_REVERT_VALUE, rev.applyValue);

        SettingsEntry existing = SettingsEntry.forExistingKey("k", 1, 7);
        assertEquals(7, existing.asRevertEntry().applyValue);
    }

    @Test(expected = IllegalArgumentException.class)
    public void forAbsentKey_nullKey_throws() {
        SettingsEntry.forAbsentKey(null, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void forAbsentKey_blankKey_throws() {
        SettingsEntry.forAbsentKey("  ", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void forExistingKey_negativeApply_throws() {
        SettingsEntry.forExistingKey("k", -1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void forAbsentKey_negativeApply_throws() {
        SettingsEntry.forAbsentKey("k", -2);
    }

    @Test
    public void equalsHashCodeToString() {
        SettingsEntry a = SettingsEntry.forExistingKey("k", 1, 2);
        SettingsEntry b = SettingsEntry.forExistingKey("k", 1, 2);
        SettingsEntry diffKey = SettingsEntry.forExistingKey("z", 1, 2);
        SettingsEntry diffApply = SettingsEntry.forExistingKey("k", 0, 2);
        SettingsEntry diffPrior = SettingsEntry.forExistingKey("k", 1, 9);
        // Same key, applyValue and priorValue(0) but different revertStrategy: exercises the
        // strategy branch of equals (forAbsentKey → WRITE_DEFAULT vs forExistingKey → RESTORE_PRIOR).
        SettingsEntry diffStrat = SettingsEntry.forExistingKey("k", 1, 0);
        SettingsEntry diffStratAbsent = SettingsEntry.forAbsentKey("k", 1);

        assertEquals(a, a);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diffKey);
        assertNotEquals(a, diffApply);
        assertNotEquals(a, diffPrior);
        assertNotEquals(diffStratAbsent, diffStrat);
        assertNotEquals(a, null);
        assertNotEquals(a, "not an entry");
        assertTrue(a.toString().contains("key='k'"));
        assertTrue(a.toString().contains("apply=1"));
    }
}

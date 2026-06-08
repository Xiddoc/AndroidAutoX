package com.xiddoc.androidautox.autox.ime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.xiddoc.androidautox.autox.provider.SettingsEntry;

import org.junit.Test;

import java.util.List;

/**
 * Plain-JUnit tests for the migrated {@link ImeDisplaySettingsSpec}, which now produces
 * shared {@link SettingsEntry} lists.
 */
public class ImeDisplaySettingsSpecTest {

    // Constants

    @Test
    public void constants() {
        assertEquals(1, ImeDisplaySettingsSpec.VALUE_ENABLED);
        assertEquals(0, ImeDisplaySettingsSpec.VALUE_DISABLED);
        assertEquals(-1, ImeDisplaySettingsSpec.VALUE_UNSET);
        assertTrue(ImeDisplaySettingsSpec.KEY_TEMPLATE_SHOULD_SHOW_IME.contains("%d"));
        assertTrue(ImeDisplaySettingsSpec.KEY_TEMPLATE_SHOULD_SHOW_SYSTEM_DECORS.contains("%d"));
    }

    // forDisplay validation

    @Test(expected = IllegalArgumentException.class)
    public void forDisplay_zero_throws() {
        ImeDisplaySettingsSpec.forDisplay(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void forDisplay_negative_throws() {
        ImeDisplaySettingsSpec.forDisplay(-1);
    }

    @Test
    public void forDisplay_positive_keysFormatted() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(7);
        assertEquals(7, spec.getDisplayId());
        assertEquals("display_should_show_ime_7", spec.imeKey());
        assertEquals("display_should_show_system_decors_7", spec.decorKey());
    }

    // prior-value getters

    @Test
    public void priorGetters_freshSpec_bothUnset() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(3);
        assertEquals(ImeDisplaySettingsSpec.VALUE_UNSET, spec.getPriorSystemDecors());
        assertEquals(ImeDisplaySettingsSpec.VALUE_UNSET, spec.getPriorIme());
    }

    @Test
    public void priorGetters_reflectWithPriorValues() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(3)
                .withPriorValues(ImeDisplaySettingsSpec.VALUE_ENABLED,
                        ImeDisplaySettingsSpec.VALUE_DISABLED);
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED, spec.getPriorSystemDecors());
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED, spec.getPriorIme());
    }

    // applyEntries

    @Test
    public void applyEntries_freshSpec_decorFirstThenIme_bothUnsetAbsent() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(2);
        List<SettingsEntry> apply = spec.applyEntries();
        assertEquals(2, apply.size());
        assertTrue(apply.get(0).key.contains("system_decors"));
        assertTrue(apply.get(1).key.contains("should_show_ime"));
        for (SettingsEntry e : apply) {
            assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED, e.applyValue);
            // Fresh = unset → WRITE_DEFAULT (reverts to disabled).
            assertEquals(SettingsEntry.RevertStrategy.WRITE_DEFAULT, e.revertStrategy);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void applyEntries_unmodifiable() {
        ImeDisplaySettingsSpec.forDisplay(2).applyEntries()
                .add(SettingsEntry.forAbsentKey("x", 1));
    }

    @Test
    public void applyEntries_withPriors_mapsToRestoreOrDefault() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(5)
                .withPriorValues(ImeDisplaySettingsSpec.VALUE_DISABLED,
                        ImeDisplaySettingsSpec.VALUE_ENABLED);
        List<SettingsEntry> apply = spec.applyEntries();
        // decor prior disabled (0) → RESTORE_PRIOR with prior 0
        assertEquals(SettingsEntry.RevertStrategy.RESTORE_PRIOR, apply.get(0).revertStrategy);
        assertEquals(0, apply.get(0).priorValue);
        assertEquals(0, apply.get(0).revertValue());
        // ime prior enabled (1) → RESTORE_PRIOR with prior 1
        assertEquals(SettingsEntry.RevertStrategy.RESTORE_PRIOR, apply.get(1).revertStrategy);
        assertEquals(1, apply.get(1).priorValue);
        assertEquals(1, apply.get(1).revertValue());
    }

    // revertEntries

    @Test
    public void revertEntries_reverseOrder_imeThenDecor() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(2);
        List<SettingsEntry> revert = spec.revertEntries();
        assertEquals(2, revert.size());
        assertTrue(revert.get(0).key.contains("should_show_ime"));
        assertTrue(revert.get(1).key.contains("system_decors"));
    }

    @Test
    public void revertEntries_unsetPriors_revertValueIsDisabled() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(2);
        for (SettingsEntry e : spec.revertEntries()) {
            assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED, e.revertValue());
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void revertEntries_unmodifiable() {
        ImeDisplaySettingsSpec.forDisplay(2).revertEntries()
                .add(SettingsEntry.forAbsentKey("x", 1));
    }

    // withPriorValues validation

    @Test
    public void withPriorValues_allValidCombinations() {
        ImeDisplaySettingsSpec.forDisplay(5).withPriorValues(
                ImeDisplaySettingsSpec.VALUE_ENABLED, ImeDisplaySettingsSpec.VALUE_DISABLED);
        ImeDisplaySettingsSpec.forDisplay(5).withPriorValues(
                ImeDisplaySettingsSpec.VALUE_UNSET, ImeDisplaySettingsSpec.VALUE_UNSET);
        // displayId preserved
        assertEquals(99, ImeDisplaySettingsSpec.forDisplay(99).withPriorValues(
                ImeDisplaySettingsSpec.VALUE_DISABLED,
                ImeDisplaySettingsSpec.VALUE_DISABLED).getDisplayId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void withPriorValues_invalidDecorPrior_throws() {
        ImeDisplaySettingsSpec.forDisplay(5)
                .withPriorValues(42, ImeDisplaySettingsSpec.VALUE_UNSET);
    }

    @Test(expected = IllegalArgumentException.class)
    public void withPriorValues_invalidImePrior_throws() {
        ImeDisplaySettingsSpec.forDisplay(5)
                .withPriorValues(ImeDisplaySettingsSpec.VALUE_UNSET, 42);
    }

    // equals / hashCode / toString

    @Test
    public void equalsHashCodeToString() {
        ImeDisplaySettingsSpec a = ImeDisplaySettingsSpec.forDisplay(3);
        ImeDisplaySettingsSpec b = ImeDisplaySettingsSpec.forDisplay(3);
        ImeDisplaySettingsSpec diffId = ImeDisplaySettingsSpec.forDisplay(4);
        ImeDisplaySettingsSpec diffDecorPrior = ImeDisplaySettingsSpec.forDisplay(3)
                .withPriorValues(ImeDisplaySettingsSpec.VALUE_ENABLED,
                        ImeDisplaySettingsSpec.VALUE_UNSET);
        // Same displayId and decor prior, but different IME prior: exercises the priorIme
        // branch of equals (which the decor-differing case short-circuits before).
        ImeDisplaySettingsSpec sameDecorDiffIme = ImeDisplaySettingsSpec.forDisplay(3)
                .withPriorValues(ImeDisplaySettingsSpec.VALUE_DISABLED,
                        ImeDisplaySettingsSpec.VALUE_ENABLED);
        ImeDisplaySettingsSpec sameDecorDiffIme2 = ImeDisplaySettingsSpec.forDisplay(3)
                .withPriorValues(ImeDisplaySettingsSpec.VALUE_DISABLED,
                        ImeDisplaySettingsSpec.VALUE_DISABLED);

        assertEquals(a, a);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diffId);
        assertNotEquals(a, diffDecorPrior);
        assertNotEquals(sameDecorDiffIme, sameDecorDiffIme2);
        assertFalse(a.equals(null));
        assertFalse(a.equals("not a spec"));
        assertNotNull(a.toString());
        assertTrue(a.toString().contains("displayId=3"));
    }
}

package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.xiddoc.androidautox.FakeSharedPreferences;
import com.xiddoc.androidautox.autox.ime.ImeDisplaySettingsSpec;

import org.junit.Test;

/**
 * Plain-JUnit (no Robolectric) coverage for {@link AutoXSettingsStore}, using
 * {@link FakeSharedPreferences} as the prefs double so the logic stays JaCoCo-visible.
 */
public class AutoXSettingsStoreTest {

    private SharedPreferences prefs() {
        return new FakeSharedPreferences();
    }

    // -------------------------------------------------------------------------
    // isEnabled — defaults
    // -------------------------------------------------------------------------

    @Test
    public void isEnabled_defaultsToFalse() {
        assertFalse(AutoXSettingsStore.isEnabled(prefs()));
    }

    // -------------------------------------------------------------------------
    // setEnabled / isEnabled round-trips
    // -------------------------------------------------------------------------

    @Test
    public void setEnabled_true_isReadBackAsTrue() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setEnabled(p, true);
        assertTrue(AutoXSettingsStore.isEnabled(p));
    }

    @Test
    public void setEnabled_false_isReadBackAsFalse() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setEnabled(p, true);
        AutoXSettingsStore.setEnabled(p, false);
        assertFalse(AutoXSettingsStore.isEnabled(p));
    }

    @Test
    public void setEnabled_persistsUnderCorrectKey() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setEnabled(p, true);
        assertTrue(p.getBoolean(AutoXSettingsStore.KEY_ENABLED, false));
    }

    @Test
    public void setEnabled_false_persistsFalseUnderCorrectKey() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setEnabled(p, true);
        AutoXSettingsStore.setEnabled(p, false);
        assertFalse(p.getBoolean(AutoXSettingsStore.KEY_ENABLED, true));
    }

    // -------------------------------------------------------------------------
    // toggleEnabled
    // -------------------------------------------------------------------------

    @Test
    public void toggleEnabled_flipsFromFalseToTrue() {
        SharedPreferences p = prefs();
        boolean result = AutoXSettingsStore.toggleEnabled(p);
        assertTrue(result);
        assertTrue(AutoXSettingsStore.isEnabled(p));
    }

    @Test
    public void toggleEnabled_flipsFromTrueToFalse() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setEnabled(p, true);
        boolean result = AutoXSettingsStore.toggleEnabled(p);
        assertFalse(result);
        assertFalse(AutoXSettingsStore.isEnabled(p));
    }

    @Test
    public void toggleEnabled_returnedValueMatchesPersistedValue() {
        SharedPreferences p = prefs();
        for (int i = 0; i < 3; i++) {
            boolean returned = AutoXSettingsStore.toggleEnabled(p);
            assertEquals(returned, AutoXSettingsStore.isEnabled(p));
        }
    }

    // -------------------------------------------------------------------------
    // getTargetPackage — defaults
    // -------------------------------------------------------------------------

    @Test
    public void getTargetPackage_defaultsToNull() {
        assertNull(AutoXSettingsStore.getTargetPackage(prefs()));
    }

    // -------------------------------------------------------------------------
    // setTargetPackage / getTargetPackage round-trips
    // -------------------------------------------------------------------------

    @Test
    public void setTargetPackage_value_isReadBack() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setTargetPackage(p, "com.example.app");
        assertEquals("com.example.app", AutoXSettingsStore.getTargetPackage(p));
    }

    @Test
    public void setTargetPackage_null_clearsValue() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setTargetPackage(p, "com.example.app");
        AutoXSettingsStore.setTargetPackage(p, null);
        assertNull(AutoXSettingsStore.getTargetPackage(p));
    }

    @Test
    public void setTargetPackage_emptyString_clearsValue() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setTargetPackage(p, "com.example.app");
        AutoXSettingsStore.setTargetPackage(p, "");
        assertNull(AutoXSettingsStore.getTargetPackage(p));
    }

    @Test
    public void setTargetPackage_persistsUnderCorrectKey() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setTargetPackage(p, "com.foo.bar");
        assertEquals("com.foo.bar", p.getString(AutoXSettingsStore.KEY_TARGET_PACKAGE, null));
    }

    // -------------------------------------------------------------------------
    // clearTargetPackage
    // -------------------------------------------------------------------------

    @Test
    public void clearTargetPackage_removesStoredValue() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setTargetPackage(p, "com.example.app");
        AutoXSettingsStore.clearTargetPackage(p);
        assertNull(AutoXSettingsStore.getTargetPackage(p));
    }

    @Test
    public void clearTargetPackage_onEmptyPrefs_isNoOp() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.clearTargetPackage(p);
        assertNull(AutoXSettingsStore.getTargetPackage(p));
    }

    // -------------------------------------------------------------------------
    // clear (bulk)
    // -------------------------------------------------------------------------

    @Test
    public void clear_resetsEnabledToDefault() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setEnabled(p, true);
        AutoXSettingsStore.clear(p);
        assertFalse(AutoXSettingsStore.isEnabled(p));
    }

    @Test
    public void clear_resetsTargetPackageToNull() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setTargetPackage(p, "com.example.app");
        AutoXSettingsStore.clear(p);
        assertNull(AutoXSettingsStore.getTargetPackage(p));
    }

    @Test
    public void clear_resetsBothFieldsAtOnce() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setEnabled(p, true);
        AutoXSettingsStore.setTargetPackage(p, "com.example.app");
        AutoXSettingsStore.clear(p);
        assertFalse(AutoXSettingsStore.isEnabled(p));
        assertNull(AutoXSettingsStore.getTargetPackage(p));
    }

    // -------------------------------------------------------------------------
    // getTargetPackage — treats a stored empty string as absent (null)
    // -------------------------------------------------------------------------

    /**
     * If a legacy/external path writes an empty string directly under KEY_TARGET_PACKAGE
     * (bypassing setTargetPackage), getTargetPackage must still treat it as absent.
     */
    @Test
    public void getTargetPackage_storedEmptyString_returnsNull() {
        SharedPreferences p = prefs();
        // Write an empty string directly — simulates a legacy write or edge case.
        p.edit().putString(AutoXSettingsStore.KEY_TARGET_PACKAGE, "").apply();
        assertNull(AutoXSettingsStore.getTargetPackage(p));
    }

    // -------------------------------------------------------------------------
    // Key constants sanity
    // -------------------------------------------------------------------------

    @Test
    public void keyConstants_areNonEmpty() {
        assertFalse(AutoXSettingsStore.KEY_ENABLED.isEmpty());
        assertFalse(AutoXSettingsStore.KEY_TARGET_PACKAGE.isEmpty());
    }

    @Test
    public void keyConstants_areDifferent() {
        assertFalse(AutoXSettingsStore.KEY_ENABLED.equals(AutoXSettingsStore.KEY_TARGET_PACKAGE));
    }

    // -------------------------------------------------------------------------
    // Prior force_resizable — absent / present / 0 / non-zero
    // -------------------------------------------------------------------------

    @Test
    public void priorForceResizable_defaultsToNull() {
        assertNull(AutoXSettingsStore.getPriorForceResizable(prefs()));
    }

    @Test
    public void priorForceResizable_present_nonZero_roundTrips() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorForceResizable(p, 1);
        assertEquals(Integer.valueOf(1), AutoXSettingsStore.getPriorForceResizable(p));
    }

    @Test
    public void priorForceResizable_present_zero_roundTrips_andIsNotNull() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorForceResizable(p, 0);
        Integer got = AutoXSettingsStore.getPriorForceResizable(p);
        assertNotNull(got);
        assertEquals(Integer.valueOf(0), got);
    }

    @Test
    public void priorForceResizable_setNull_clearsToAbsent() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorForceResizable(p, 1);
        AutoXSettingsStore.setPriorForceResizable(p, null);
        assertNull(AutoXSettingsStore.getPriorForceResizable(p));
    }

    @Test
    public void priorForceResizable_overwrite_keepsLatest() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorForceResizable(p, 1);
        AutoXSettingsStore.setPriorForceResizable(p, 0);
        assertEquals(Integer.valueOf(0), AutoXSettingsStore.getPriorForceResizable(p));
    }

    @Test
    public void priorForceResizable_persistsUnderCorrectKeys() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorForceResizable(p, 7);
        assertEquals(7, p.getInt(AutoXSettingsStore.KEY_PRIOR_FORCE_RESIZABLE, -99));
        assertTrue(p.getBoolean(
                AutoXSettingsStore.KEY_PRIOR_FORCE_RESIZABLE + AutoXSettingsStore.SUFFIX_PRESENT,
                false));
    }

    /** A stored 0 with the presence flag is distinct from absent (no presence flag). */
    @Test
    public void priorForceResizable_storedZeroVsAbsent_areDistinct() {
        SharedPreferences zeroPrefs = prefs();
        AutoXSettingsStore.setPriorForceResizable(zeroPrefs, 0);
        assertNotNull(AutoXSettingsStore.getPriorForceResizable(zeroPrefs));

        SharedPreferences absentPrefs = prefs();
        assertNull(AutoXSettingsStore.getPriorForceResizable(absentPrefs));
    }

    /**
     * A leftover int value without its presence companion (e.g. partial/legacy write) must
     * still read as absent.
     */
    @Test
    public void priorForceResizable_valueWithoutPresenceFlag_readsAsNull() {
        SharedPreferences p = prefs();
        p.edit().putInt(AutoXSettingsStore.KEY_PRIOR_FORCE_RESIZABLE, 5).apply();
        assertNull(AutoXSettingsStore.getPriorForceResizable(p));
    }

    // -------------------------------------------------------------------------
    // Prior enable_freeform — absent / present / 0 / non-zero
    // -------------------------------------------------------------------------

    @Test
    public void priorEnableFreeform_defaultsToNull() {
        assertNull(AutoXSettingsStore.getPriorEnableFreeform(prefs()));
    }

    @Test
    public void priorEnableFreeform_present_nonZero_roundTrips() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorEnableFreeform(p, 1);
        assertEquals(Integer.valueOf(1), AutoXSettingsStore.getPriorEnableFreeform(p));
    }

    @Test
    public void priorEnableFreeform_present_zero_isNotNull() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorEnableFreeform(p, 0);
        assertEquals(Integer.valueOf(0), AutoXSettingsStore.getPriorEnableFreeform(p));
    }

    @Test
    public void priorEnableFreeform_setNull_clearsToAbsent() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorEnableFreeform(p, 1);
        AutoXSettingsStore.setPriorEnableFreeform(p, null);
        assertNull(AutoXSettingsStore.getPriorEnableFreeform(p));
    }

    @Test
    public void priorEnableFreeform_independentOfForceResizable() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorEnableFreeform(p, 1);
        assertNull(AutoXSettingsStore.getPriorForceResizable(p));
        AutoXSettingsStore.setPriorForceResizable(p, 0);
        assertEquals(Integer.valueOf(1), AutoXSettingsStore.getPriorEnableFreeform(p));
        assertEquals(Integer.valueOf(0), AutoXSettingsStore.getPriorForceResizable(p));
    }

    // -------------------------------------------------------------------------
    // Prior per-display shouldShowSystemDecors
    // -------------------------------------------------------------------------

    @Test
    public void priorSystemDecors_defaultsToNull() {
        assertNull(AutoXSettingsStore.getPriorShouldShowSystemDecors(prefs(), 42));
    }

    @Test
    public void priorSystemDecors_present_zero_roundTrips() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 42, 0);
        assertEquals(Integer.valueOf(0),
                AutoXSettingsStore.getPriorShouldShowSystemDecors(p, 42));
    }

    @Test
    public void priorSystemDecors_present_nonZero_roundTrips() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 42, 1);
        assertEquals(Integer.valueOf(1),
                AutoXSettingsStore.getPriorShouldShowSystemDecors(p, 42));
    }

    @Test
    public void priorSystemDecors_setNull_clearsToAbsent() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 42, 1);
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 42, null);
        assertNull(AutoXSettingsStore.getPriorShouldShowSystemDecors(p, 42));
    }

    @Test
    public void priorSystemDecors_differentDisplaysDoNotCollide() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 42, 1);
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 7, 0);
        assertEquals(Integer.valueOf(1),
                AutoXSettingsStore.getPriorShouldShowSystemDecors(p, 42));
        assertEquals(Integer.valueOf(0),
                AutoXSettingsStore.getPriorShouldShowSystemDecors(p, 7));
        assertNull(AutoXSettingsStore.getPriorShouldShowSystemDecors(p, 99));
    }

    // -------------------------------------------------------------------------
    // Prior per-display shouldShowIme
    // -------------------------------------------------------------------------

    @Test
    public void priorShouldShowIme_defaultsToNull() {
        assertNull(AutoXSettingsStore.getPriorShouldShowIme(prefs(), 42));
    }

    @Test
    public void priorShouldShowIme_present_zero_roundTrips() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, 0);
        assertEquals(Integer.valueOf(0), AutoXSettingsStore.getPriorShouldShowIme(p, 42));
    }

    @Test
    public void priorShouldShowIme_present_nonZero_roundTrips() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, 1);
        assertEquals(Integer.valueOf(1), AutoXSettingsStore.getPriorShouldShowIme(p, 42));
    }

    @Test
    public void priorShouldShowIme_setNull_clearsToAbsent() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, 1);
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, null);
        assertNull(AutoXSettingsStore.getPriorShouldShowIme(p, 42));
    }

    @Test
    public void priorShouldShowIme_differentDisplaysDoNotCollide() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, 1);
        AutoXSettingsStore.setPriorShouldShowIme(p, 7, 0);
        assertEquals(Integer.valueOf(1), AutoXSettingsStore.getPriorShouldShowIme(p, 42));
        assertEquals(Integer.valueOf(0), AutoXSettingsStore.getPriorShouldShowIme(p, 7));
    }

    /** IME and system-decors priors for the same display must not collide with each other. */
    @Test
    public void priorImeAndSystemDecors_sameDisplay_doNotCollide() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, 1);
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 42, 0);
        assertEquals(Integer.valueOf(1), AutoXSettingsStore.getPriorShouldShowIme(p, 42));
        assertEquals(Integer.valueOf(0),
                AutoXSettingsStore.getPriorShouldShowSystemDecors(p, 42));
    }

    // -------------------------------------------------------------------------
    // clearPriorsForDisplay
    // -------------------------------------------------------------------------

    @Test
    public void clearPriorsForDisplay_wipesBothPerDisplayPriors() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, 1);
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 42, 0);
        AutoXSettingsStore.clearPriorsForDisplay(p, 42);
        assertNull(AutoXSettingsStore.getPriorShouldShowIme(p, 42));
        assertNull(AutoXSettingsStore.getPriorShouldShowSystemDecors(p, 42));
    }

    @Test
    public void clearPriorsForDisplay_leavesOtherDisplaysUntouched() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, 1);
        AutoXSettingsStore.setPriorShouldShowIme(p, 7, 0);
        AutoXSettingsStore.clearPriorsForDisplay(p, 42);
        assertNull(AutoXSettingsStore.getPriorShouldShowIme(p, 42));
        assertEquals(Integer.valueOf(0), AutoXSettingsStore.getPriorShouldShowIme(p, 7));
    }

    @Test
    public void clearPriorsForDisplay_onEmptyPrefs_isNoOp() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.clearPriorsForDisplay(p, 42);
        assertNull(AutoXSettingsStore.getPriorShouldShowIme(p, 42));
        assertNull(AutoXSettingsStore.getPriorShouldShowSystemDecors(p, 42));
    }

    // -------------------------------------------------------------------------
    // clearPriors (global)
    // -------------------------------------------------------------------------

    @Test
    public void clearPriors_wipesGlobalPriors() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorForceResizable(p, 1);
        AutoXSettingsStore.setPriorEnableFreeform(p, 0);
        AutoXSettingsStore.clearPriors(p);
        assertNull(AutoXSettingsStore.getPriorForceResizable(p));
        assertNull(AutoXSettingsStore.getPriorEnableFreeform(p));
    }

    @Test
    public void clearPriors_onEmptyPrefs_isNoOp() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.clearPriors(p);
        assertNull(AutoXSettingsStore.getPriorForceResizable(p));
        assertNull(AutoXSettingsStore.getPriorEnableFreeform(p));
    }

    // -------------------------------------------------------------------------
    // clear (bulk) also wipes global priors
    // -------------------------------------------------------------------------

    @Test
    public void clear_alsoWipesGlobalPriors() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setEnabled(p, true);
        AutoXSettingsStore.setPriorForceResizable(p, 1);
        AutoXSettingsStore.setPriorEnableFreeform(p, 0);
        AutoXSettingsStore.clear(p);
        assertFalse(AutoXSettingsStore.isEnabled(p));
        assertNull(AutoXSettingsStore.getPriorForceResizable(p));
        assertNull(AutoXSettingsStore.getPriorEnableFreeform(p));
    }

    // -------------------------------------------------------------------------
    // Prior keys flow into SettingsEntry revert strategy (absent → WRITE_DEFAULT,
    // present → RESTORE_PRIOR) via FreeformGlobalSettingsSpec.
    // -------------------------------------------------------------------------

    @Test
    public void priorForceResizable_absent_yieldsWriteDefaultRevert() {
        SharedPreferences p = prefs();
        Integer prior = AutoXSettingsStore.getPriorForceResizable(p);
        com.xiddoc.androidautox.autox.provider.SettingsEntry entry =
                com.xiddoc.androidautox.autox.provider.SettingsEntry.of(
                        FreeformGlobalSettingsSpec.KEY_FORCE_RESIZABLE,
                        FreeformGlobalSettingsSpec.ENABLED_VALUE, prior);
        assertEquals(
                com.xiddoc.androidautox.autox.provider.SettingsEntry.RevertStrategy.WRITE_DEFAULT,
                entry.revertStrategy);
    }

    @Test
    public void priorForceResizable_presentZero_yieldsRestorePriorRevert() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorForceResizable(p, 0);
        Integer prior = AutoXSettingsStore.getPriorForceResizable(p);
        com.xiddoc.androidautox.autox.provider.SettingsEntry entry =
                com.xiddoc.androidautox.autox.provider.SettingsEntry.of(
                        FreeformGlobalSettingsSpec.KEY_FORCE_RESIZABLE,
                        FreeformGlobalSettingsSpec.ENABLED_VALUE, prior);
        assertEquals(
                com.xiddoc.androidautox.autox.provider.SettingsEntry.RevertStrategy.RESTORE_PRIOR,
                entry.revertStrategy);
        assertEquals(0, entry.revertValue());
    }

    // -------------------------------------------------------------------------
    // Convenience getters: null -> ImeDisplaySettingsSpec.VALUE_UNSET (int form)
    // -------------------------------------------------------------------------

    @Test
    public void getPriorShouldShowImeOrUnset_absent_returnsValueUnset() {
        assertEquals(ImeDisplaySettingsSpec.VALUE_UNSET,
                AutoXSettingsStore.getPriorShouldShowImeOrUnset(prefs(), 42));
    }

    @Test
    public void getPriorShouldShowImeOrUnset_present_returnsStoredValue() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, 0);
        assertEquals(0, AutoXSettingsStore.getPriorShouldShowImeOrUnset(p, 42));
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, 1);
        assertEquals(1, AutoXSettingsStore.getPriorShouldShowImeOrUnset(p, 42));
    }

    @Test
    public void getPriorShouldShowSystemDecorsOrUnset_absent_returnsValueUnset() {
        assertEquals(ImeDisplaySettingsSpec.VALUE_UNSET,
                AutoXSettingsStore.getPriorShouldShowSystemDecorsOrUnset(prefs(), 42));
    }

    @Test
    public void getPriorShouldShowSystemDecorsOrUnset_present_returnsStoredValue() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 42, 0);
        assertEquals(0, AutoXSettingsStore.getPriorShouldShowSystemDecorsOrUnset(p, 42));
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 42, 1);
        assertEquals(1, AutoXSettingsStore.getPriorShouldShowSystemDecorsOrUnset(p, 42));
    }

    // -------------------------------------------------------------------------
    // End-to-end: store per-display priors flow into ImeDisplaySettingsSpec
    // (absent -> VALUE_UNSET -> forAbsentKey/WRITE_DEFAULT; present -> RESTORE_PRIOR)
    // -------------------------------------------------------------------------

    @Test
    public void perDisplayPriors_absent_flowIntoImeSpec_asUnsetRevertingToDisabled() {
        SharedPreferences p = prefs();
        int displayId = 42;
        // Nothing captured -> both convenience getters return VALUE_UNSET.
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(displayId)
                .withPriorValues(
                        AutoXSettingsStore.getPriorShouldShowSystemDecorsOrUnset(p, displayId),
                        AutoXSettingsStore.getPriorShouldShowImeOrUnset(p, displayId));
        // Revert order: IME first, then decors. Both were UNSET -> WRITE_DEFAULT (disabled).
        com.xiddoc.androidautox.autox.provider.SettingsEntry imeRevert = spec.revertEntries().get(0);
        com.xiddoc.androidautox.autox.provider.SettingsEntry decorRevert =
                spec.revertEntries().get(1);
        assertEquals(
                com.xiddoc.androidautox.autox.provider.SettingsEntry.RevertStrategy.WRITE_DEFAULT,
                imeRevert.revertStrategy);
        assertEquals(
                com.xiddoc.androidautox.autox.provider.SettingsEntry.RevertStrategy.WRITE_DEFAULT,
                decorRevert.revertStrategy);
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED, imeRevert.revertValue());
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED, decorRevert.revertValue());
    }

    @Test
    public void perDisplayPriors_present_flowIntoImeSpec_asRestorePrior() {
        SharedPreferences p = prefs();
        int displayId = 42;
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, displayId, 0);
        AutoXSettingsStore.setPriorShouldShowIme(p, displayId, 1);
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(displayId)
                .withPriorValues(
                        AutoXSettingsStore.getPriorShouldShowSystemDecorsOrUnset(p, displayId),
                        AutoXSettingsStore.getPriorShouldShowImeOrUnset(p, displayId));
        com.xiddoc.androidautox.autox.provider.SettingsEntry imeRevert = spec.revertEntries().get(0);
        com.xiddoc.androidautox.autox.provider.SettingsEntry decorRevert =
                spec.revertEntries().get(1);
        assertEquals(
                com.xiddoc.androidautox.autox.provider.SettingsEntry.RevertStrategy.RESTORE_PRIOR,
                imeRevert.revertStrategy);
        assertEquals(1, imeRevert.revertValue());
        assertEquals(
                com.xiddoc.androidautox.autox.provider.SettingsEntry.RevertStrategy.RESTORE_PRIOR,
                decorRevert.revertStrategy);
        assertEquals(0, decorRevert.revertValue());
    }

    // -------------------------------------------------------------------------
    // clear()/clearPriors() leave per-display priors stranded (locked contract)
    // -------------------------------------------------------------------------

    @Test
    public void clear_leavesPerDisplayPriorIntact() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowIme(p, 42, 1);
        AutoXSettingsStore.clear(p);
        // Per-display priors are NOT wiped by clear() — caller must use clearPriorsForDisplay.
        assertEquals(Integer.valueOf(1), AutoXSettingsStore.getPriorShouldShowIme(p, 42));
    }

    @Test
    public void clearPriors_leavesPerDisplayPriorIntact() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setPriorShouldShowSystemDecors(p, 42, 0);
        AutoXSettingsStore.clearPriors(p);
        assertEquals(Integer.valueOf(0),
                AutoXSettingsStore.getPriorShouldShowSystemDecors(p, 42));
    }

    // -------------------------------------------------------------------------
    // displayId <= 0 guard on the four per-display methods
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void setPriorShouldShowIme_rejectsNonPositiveDisplayId() {
        AutoXSettingsStore.setPriorShouldShowIme(prefs(), 0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getPriorShouldShowIme_rejectsNonPositiveDisplayId() {
        AutoXSettingsStore.getPriorShouldShowIme(prefs(), -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setPriorShouldShowSystemDecors_rejectsNonPositiveDisplayId() {
        AutoXSettingsStore.setPriorShouldShowSystemDecors(prefs(), 0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getPriorShouldShowSystemDecors_rejectsNonPositiveDisplayId() {
        AutoXSettingsStore.getPriorShouldShowSystemDecors(prefs(), -5);
    }

    // -------------------------------------------------------------------------
    // Defensive readPrior branch: presence flag true but value key absent
    // -------------------------------------------------------------------------

    /**
     * Corrupted/partially-written prefs: the presence companion is true but the int value key
     * is missing. The getter must return the neutral default ({@code 0}), not null.
     */
    @Test
    public void readPrior_presenceTrueButValueAbsent_returnsNeutralDefault() {
        SharedPreferences p = prefs();
        p.edit().putBoolean(
                AutoXSettingsStore.KEY_PRIOR_FORCE_RESIZABLE + AutoXSettingsStore.SUFFIX_PRESENT,
                true).apply();
        assertEquals(Integer.valueOf(0), AutoXSettingsStore.getPriorForceResizable(p));
    }

    // -------------------------------------------------------------------------
    // Per-display parity: value present without presence flag reads as null
    // -------------------------------------------------------------------------

    @Test
    public void priorShouldShowIme_valueWithoutPresenceFlag_readsAsNull() {
        SharedPreferences p = prefs();
        p.edit().putInt(
                String.format(AutoXSettingsStore.KEY_PRIOR_SHOULD_SHOW_IME_TEMPLATE, 42), 1)
                .apply();
        assertNull(AutoXSettingsStore.getPriorShouldShowIme(p, 42));
    }

    // -------------------------------------------------------------------------
    // WS6 audio-routing state
    // -------------------------------------------------------------------------

    @Test
    public void audioRouteState_defaultsToNull() {
        assertNull(AutoXSettingsStore.getAudioRouteState(prefs()));
    }

    @Test
    public void audioRouteState_roundTripsWithAddress() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.AudioRouteState state =
                new AutoXSettingsStore.AudioRouteState(10123, "BT_A2DP", "00:11:22:33:44:55");
        AutoXSettingsStore.setAudioRouteState(p, state);

        AutoXSettingsStore.AudioRouteState read = AutoXSettingsStore.getAudioRouteState(p);
        assertNotNull(read);
        assertEquals(10123, read.uid);
        assertEquals("BT_A2DP", read.deviceName);
        assertEquals("00:11:22:33:44:55", read.deviceAddress);
        assertEquals(state, read);
    }

    @Test
    public void audioRouteState_roundTripsWithNullAddress() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setAudioRouteState(p,
                new AutoXSettingsStore.AudioRouteState(10123, "USB", null));

        AutoXSettingsStore.AudioRouteState read = AutoXSettingsStore.getAudioRouteState(p);
        assertNotNull(read);
        assertEquals(10123, read.uid);
        assertEquals("USB", read.deviceName);
        assertNull(read.deviceAddress);
    }

    @Test
    public void audioRouteState_overwriteClearsStaleAddress() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setAudioRouteState(p,
                new AutoXSettingsStore.AudioRouteState(1, "BT_A2DP", "addr"));
        // Overwrite with a null-address state — the stale address must not survive.
        AutoXSettingsStore.setAudioRouteState(p,
                new AutoXSettingsStore.AudioRouteState(2, "USB", null));

        AutoXSettingsStore.AudioRouteState read = AutoXSettingsStore.getAudioRouteState(p);
        assertNotNull(read);
        assertEquals(2, read.uid);
        assertNull(read.deviceAddress);
    }

    @Test
    public void clearAudioRouteState_resetsToNull() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setAudioRouteState(p,
                new AutoXSettingsStore.AudioRouteState(1, "USB", "addr"));
        AutoXSettingsStore.clearAudioRouteState(p);
        assertNull(AutoXSettingsStore.getAudioRouteState(p));
    }

    @Test(expected = IllegalArgumentException.class)
    public void setAudioRouteState_rejectsNull() {
        AutoXSettingsStore.setAudioRouteState(prefs(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void audioRouteState_constructor_rejectsNullDeviceName() {
        new AutoXSettingsStore.AudioRouteState(1, null, "addr");
    }

    @Test
    public void getAudioRouteState_presentFlagButDeviceNameMissing_readsAsNull() {
        SharedPreferences p = prefs();
        // Presence flag set but the device-name key absent (corrupted/partial write).
        p.edit().putBoolean(AutoXSettingsStore.KEY_AUDIO_PRESENT, true)
                .putInt(AutoXSettingsStore.KEY_AUDIO_UID, 5).apply();
        assertNull(AutoXSettingsStore.getAudioRouteState(p));
    }

    @Test
    public void clear_alsoClearsAudioRouteState() {
        SharedPreferences p = prefs();
        AutoXSettingsStore.setAudioRouteState(p,
                new AutoXSettingsStore.AudioRouteState(1, "USB", "addr"));
        AutoXSettingsStore.clear(p);
        assertNull(AutoXSettingsStore.getAudioRouteState(p));
    }

    // -------------------------------------------------------------------------
    // AudioRouteState value semantics (equals / hashCode / toString)
    // -------------------------------------------------------------------------

    @Test
    public void audioRouteState_equalsAndHashCode() {
        AutoXSettingsStore.AudioRouteState a =
                new AutoXSettingsStore.AudioRouteState(1, "USB", "addr");
        AutoXSettingsStore.AudioRouteState b =
                new AutoXSettingsStore.AudioRouteState(1, "USB", "addr");
        AutoXSettingsStore.AudioRouteState diffUid =
                new AutoXSettingsStore.AudioRouteState(2, "USB", "addr");
        AutoXSettingsStore.AudioRouteState diffName =
                new AutoXSettingsStore.AudioRouteState(1, "BT_A2DP", "addr");
        AutoXSettingsStore.AudioRouteState diffAddr =
                new AutoXSettingsStore.AudioRouteState(1, "USB", "other");
        AutoXSettingsStore.AudioRouteState nullAddr =
                new AutoXSettingsStore.AudioRouteState(1, "USB", null);
        AutoXSettingsStore.AudioRouteState nullAddr2 =
                new AutoXSettingsStore.AudioRouteState(1, "USB", null);

        assertEquals(a, a);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(nullAddr, nullAddr2);
        assertEquals(nullAddr.hashCode(), nullAddr2.hashCode());

        org.junit.Assert.assertNotEquals(a, diffUid);
        org.junit.Assert.assertNotEquals(a, diffName);
        org.junit.Assert.assertNotEquals(a, diffAddr);
        org.junit.Assert.assertNotEquals(a, nullAddr);
        org.junit.Assert.assertNotEquals(nullAddr, a);
        org.junit.Assert.assertNotEquals(a, null);
        org.junit.Assert.assertNotEquals(a, "not a state");
        org.junit.Assert.assertNotNull(a.toString());
    }
}

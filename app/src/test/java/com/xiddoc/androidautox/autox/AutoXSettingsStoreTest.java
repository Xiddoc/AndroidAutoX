package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.xiddoc.androidautox.FakeSharedPreferences;

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
}

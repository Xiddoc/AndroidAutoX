package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;

/**
 * Plain-JUnit (no Robolectric) coverage for {@link DevModeStore}, using
 * {@link FakeSharedPreferences} as the prefs double so the logic stays JaCoCo-visible.
 */
public class DevModeStoreTest {

    private SharedPreferences prefs() {
        return new FakeSharedPreferences();
    }

    @Test
    public void isEnabled_defaultsToFalse() {
        assertFalse(DevModeStore.isEnabled(prefs()));
    }

    @Test
    public void setEnabled_thenIsEnabled_roundTrips() {
        SharedPreferences p = prefs();

        DevModeStore.setEnabled(p, true);
        assertTrue(DevModeStore.isEnabled(p));

        DevModeStore.setEnabled(p, false);
        assertFalse(DevModeStore.isEnabled(p));
    }

    @Test
    public void setEnabled_persistsUnderLegacyKey() {
        SharedPreferences p = prefs();
        DevModeStore.setEnabled(p, true);
        // Back-compat: must use the exact legacy key so existing prefs carry over.
        assertTrue(p.getBoolean("dev_mode_enabled", false));
    }

    @Test
    public void setEnabled_false_persistsFalseUnderLegacyKey() {
        SharedPreferences p = prefs();
        DevModeStore.setEnabled(p, true);
        DevModeStore.setEnabled(p, false);
        // Mirror the true-case: the false value must also land under the exact legacy key.
        assertFalse(p.getBoolean("dev_mode_enabled", true));
    }

    @Test
    public void toggle_flipsFromFalseToTrue() {
        SharedPreferences p = prefs();
        boolean result = DevModeStore.toggle(p);
        assertTrue(result);
        assertTrue(DevModeStore.isEnabled(p));
    }

    @Test
    public void toggle_flipsFromTrueToFalse() {
        SharedPreferences p = prefs();
        DevModeStore.setEnabled(p, true);

        boolean result = DevModeStore.toggle(p);
        assertFalse(result);
        assertFalse(DevModeStore.isEnabled(p));
    }

    @Test
    public void toggle_returnedValueMatchesPersistedValue() {
        SharedPreferences p = prefs();
        for (int i = 0; i < 3; i++) {
            boolean returned = DevModeStore.toggle(p);
            assertEquals(returned, DevModeStore.isEnabled(p));
        }
    }
}

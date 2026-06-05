package com.xiddoc.androidautox;

import android.content.SharedPreferences;

/**
 * Persistence for the "developer mode" flag.
 *
 * <p>Developer mode gates the dev/PoC settings entries (e.g. the Phixit apply test and
 * dump-all diagnostics) so they stay out of sight for normal users. The flag lives in the
 * shared app preferences ({@link PhixitEngine#PREFS}) under {@link #DEV_MODE_KEY}.
 *
 * <p>This class mirrors {@link DbBackup}'s plain-JUnit-testable style: every method takes
 * a {@link SharedPreferences} so the logic can be exercised on the plain JUnit classloader
 * (no Robolectric) with a fake prefs double, which keeps it visible to JaCoCo coverage.
 * The activity owner is responsible for resolving the prefs from a {@code Context} and
 * passing them in.
 */
public final class DevModeStore {

    /**
     * Preference key for the developer-mode flag. Kept identical to the legacy value that
     * lived as a static constant in {@code MainActivity} so existing users' dev-mode
     * preference carries over unchanged.
     */
    static final String DEV_MODE_KEY = "dev_mode_enabled";

    private DevModeStore() {
    }

    /** @return whether developer mode is currently enabled (defaults to {@code false}). */
    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(DEV_MODE_KEY, false);
    }

    /** Persists the developer-mode flag. */
    public static void setEnabled(SharedPreferences prefs, boolean enabled) {
        prefs.edit().putBoolean(DEV_MODE_KEY, enabled).apply();
    }

    /**
     * Flips the developer-mode flag and persists + returns the new value, for the UI's
     * flip-and-report toggle.
     *
     * @return the new (post-flip) enabled state.
     */
    public static boolean toggle(SharedPreferences prefs) {
        boolean next = !isEnabled(prefs);
        setEnabled(prefs, next);
        return next;
    }
}

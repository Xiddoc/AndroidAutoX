package com.xiddoc.androidautox.autox;

import android.content.SharedPreferences;

/**
 * Persistence for AutoX virtual-display settings.
 *
 * <p>Stores two values in the app's shared preferences:
 * <ul>
 *   <li>{@link #KEY_ENABLED} — whether the AutoX feature is active (boolean, default
 *       {@code false}).</li>
 *   <li>{@link #KEY_TARGET_PACKAGE} — the Android package name of the app to project
 *       onto the virtual display (String, default {@code null} / empty).</li>
 * </ul>
 *
 * <p>This class mirrors {@link com.xiddoc.androidautox.DevModeStore}'s plain-JUnit-testable
 * style: every method takes a {@link SharedPreferences} so the logic can be exercised on the
 * plain JUnit classloader (no Robolectric) with a {@code FakeSharedPreferences} double, keeping
 * it visible to JaCoCo coverage. The activity or service owner is responsible for resolving the
 * prefs from a {@code Context} and passing them in.
 */
public final class AutoXSettingsStore {

    /** Preference key for the AutoX-enabled flag. */
    static final String KEY_ENABLED = "autox_enabled";

    /** Preference key for the target package name. */
    static final String KEY_TARGET_PACKAGE = "autox_target_package";

    private AutoXSettingsStore() {
    }

    // -------------------------------------------------------------------------
    // Enabled flag
    // -------------------------------------------------------------------------

    /**
     * Returns whether the AutoX feature is currently enabled.
     *
     * @param prefs the shared preferences to read from; must not be null.
     * @return {@code true} if AutoX is enabled; {@code false} by default.
     */
    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    /**
     * Persists the AutoX-enabled flag.
     *
     * @param prefs   the shared preferences to write to; must not be null.
     * @param enabled {@code true} to enable AutoX.
     */
    public static void setEnabled(SharedPreferences prefs, boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /**
     * Flips the AutoX-enabled flag and persists + returns the new value.
     *
     * @param prefs the shared preferences to read from and write to; must not be null.
     * @return the new (post-flip) enabled state.
     */
    public static boolean toggleEnabled(SharedPreferences prefs) {
        boolean next = !isEnabled(prefs);
        setEnabled(prefs, next);
        return next;
    }

    // -------------------------------------------------------------------------
    // Target package
    // -------------------------------------------------------------------------

    /**
     * Returns the target package name, or {@code null} if none has been set.
     *
     * @param prefs the shared preferences to read from; must not be null.
     * @return the stored package name, or {@code null} if no value exists.
     */
    public static String getTargetPackage(SharedPreferences prefs) {
        String value = prefs.getString(KEY_TARGET_PACKAGE, null);
        return (value == null || value.isEmpty()) ? null : value;
    }

    /**
     * Persists the target package name.
     *
     * <p>Pass {@code null} or an empty string to clear the selection (equivalent to
     * {@link #clearTargetPackage(SharedPreferences)}).
     *
     * @param prefs       the shared preferences to write to; must not be null.
     * @param packageName the package name to store, or {@code null} to clear.
     */
    public static void setTargetPackage(SharedPreferences prefs, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            prefs.edit().remove(KEY_TARGET_PACKAGE).apply();
        } else {
            prefs.edit().putString(KEY_TARGET_PACKAGE, packageName).apply();
        }
    }

    /**
     * Clears the stored target package name, resetting it to the default (absent / null).
     *
     * @param prefs the shared preferences to write to; must not be null.
     */
    public static void clearTargetPackage(SharedPreferences prefs) {
        prefs.edit().remove(KEY_TARGET_PACKAGE).apply();
    }

    // -------------------------------------------------------------------------
    // Bulk clear
    // -------------------------------------------------------------------------

    /**
     * Clears both the enabled flag and the target-package selection, resetting all AutoX
     * settings to their defaults.
     *
     * @param prefs the shared preferences to write to; must not be null.
     */
    public static void clear(SharedPreferences prefs) {
        prefs.edit()
                .remove(KEY_ENABLED)
                .remove(KEY_TARGET_PACKAGE)
                .apply();
    }
}

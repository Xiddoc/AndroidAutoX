package com.xiddoc.androidautox.autox;

import android.content.SharedPreferences;

import com.xiddoc.androidautox.autox.provider.SettingsEntry;

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

    // -------------------------------------------------------------------------
    // Prior-value keys (captured at apply-time so revert survives process death)
    //
    // SharedPreferences cannot store a "null int", so absent-vs-present is encoded
    // explicitly: each int value lives under KEY_PRIOR_* and a companion boolean
    // KEY_PRIOR_*__present records whether a value was captured at all. A missing
    // (or false) presence flag means "the key was absent" → getter returns null →
    // the revert maps to WRITE_DEFAULT. A true presence flag means "the key had this
    // value (possibly 0)" → getter returns the boxed int → revert maps to
    // RESTORE_PRIOR. This faithfully distinguishes absent / present-0 / present-nonzero.
    // -------------------------------------------------------------------------

    /** Suffix appended to a prior-value key to record absent-vs-present. */
    static final String SUFFIX_PRESENT = "__present";

    /** Prior value of the global {@code force_resizable_activities} flag. */
    static final String KEY_PRIOR_FORCE_RESIZABLE = "autox_prior_force_resizable";

    /** Prior value of the global {@code enable_freeform_support} flag. */
    static final String KEY_PRIOR_ENABLE_FREEFORM = "autox_prior_enable_freeform";

    /** Per-display key template for the prior {@code shouldShowSystemDecors} value. */
    static final String KEY_PRIOR_SYSTEM_DECORS_TEMPLATE = "autox_prior_system_decors_%d";

    /** Per-display key template for the prior {@code shouldShowIme} value. */
    static final String KEY_PRIOR_SHOULD_SHOW_IME_TEMPLATE = "autox_prior_should_show_ime_%d";

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
    // Prior values — global single-value flags
    // -------------------------------------------------------------------------

    /**
     * Persists the prior value of {@code force_resizable_activities} captured at apply-time.
     *
     * @param prefs the shared preferences to write to; must not be null.
     * @param prior the captured prior value, or {@code null} if the key was absent.
     */
    public static void setPriorForceResizable(SharedPreferences prefs, Integer prior) {
        writePrior(prefs, KEY_PRIOR_FORCE_RESIZABLE, prior);
    }

    /**
     * Returns the persisted prior value of {@code force_resizable_activities}.
     *
     * @param prefs the shared preferences to read from; must not be null.
     * @return the captured prior int, or {@code null} if it was absent / never captured.
     */
    public static Integer getPriorForceResizable(SharedPreferences prefs) {
        return readPrior(prefs, KEY_PRIOR_FORCE_RESIZABLE);
    }

    /**
     * Persists the prior value of {@code enable_freeform_support} captured at apply-time.
     *
     * @param prefs the shared preferences to write to; must not be null.
     * @param prior the captured prior value, or {@code null} if the key was absent.
     */
    public static void setPriorEnableFreeform(SharedPreferences prefs, Integer prior) {
        writePrior(prefs, KEY_PRIOR_ENABLE_FREEFORM, prior);
    }

    /**
     * Returns the persisted prior value of {@code enable_freeform_support}.
     *
     * @param prefs the shared preferences to read from; must not be null.
     * @return the captured prior int, or {@code null} if it was absent / never captured.
     */
    public static Integer getPriorEnableFreeform(SharedPreferences prefs) {
        return readPrior(prefs, KEY_PRIOR_ENABLE_FREEFORM);
    }

    // -------------------------------------------------------------------------
    // Prior values — per-display flags
    // -------------------------------------------------------------------------

    /**
     * Persists the prior per-display {@code shouldShowSystemDecors} value captured at
     * apply-time, keyed by {@code displayId}.
     *
     * @param prefs     the shared preferences to write to; must not be null.
     * @param displayId the virtual display ID.
     * @param prior     the captured prior value, or {@code null} if the key was absent.
     */
    public static void setPriorShouldShowSystemDecors(SharedPreferences prefs, int displayId,
                                                      Integer prior) {
        writePrior(prefs, systemDecorsKey(displayId), prior);
    }

    /**
     * Returns the persisted prior per-display {@code shouldShowSystemDecors} value for
     * {@code displayId}.
     *
     * @param prefs     the shared preferences to read from; must not be null.
     * @param displayId the virtual display ID.
     * @return the captured prior int, or {@code null} if it was absent / never captured.
     */
    public static Integer getPriorShouldShowSystemDecors(SharedPreferences prefs, int displayId) {
        return readPrior(prefs, systemDecorsKey(displayId));
    }

    /**
     * Persists the prior per-display {@code shouldShowIme} value captured at apply-time,
     * keyed by {@code displayId}.
     *
     * @param prefs     the shared preferences to write to; must not be null.
     * @param displayId the virtual display ID.
     * @param prior     the captured prior value, or {@code null} if the key was absent.
     */
    public static void setPriorShouldShowIme(SharedPreferences prefs, int displayId,
                                             Integer prior) {
        writePrior(prefs, shouldShowImeKey(displayId), prior);
    }

    /**
     * Returns the persisted prior per-display {@code shouldShowIme} value for
     * {@code displayId}.
     *
     * @param prefs     the shared preferences to read from; must not be null.
     * @param displayId the virtual display ID.
     * @return the captured prior int, or {@code null} if it was absent / never captured.
     */
    public static Integer getPriorShouldShowIme(SharedPreferences prefs, int displayId) {
        return readPrior(prefs, shouldShowImeKey(displayId));
    }

    /**
     * Clears the persisted per-display priors ({@code shouldShowSystemDecors} and
     * {@code shouldShowIme}) for a single {@code displayId}.
     *
     * @param prefs     the shared preferences to write to; must not be null.
     * @param displayId the virtual display ID whose priors to wipe.
     */
    public static void clearPriorsForDisplay(SharedPreferences prefs, int displayId) {
        removePrior(prefs.edit(), systemDecorsKey(displayId))
                .remove(shouldShowImeKey(displayId))
                .remove(shouldShowImeKey(displayId) + SUFFIX_PRESENT)
                .apply();
    }

    // -------------------------------------------------------------------------
    // Absent-vs-present encode/decode (presence companion key)
    // -------------------------------------------------------------------------

    private static void writePrior(SharedPreferences prefs, String key, Integer prior) {
        SharedPreferences.Editor editor = prefs.edit();
        if (prior == null) {
            // Absent: drop both the value and the presence flag so the getter returns null.
            editor.remove(key).remove(key + SUFFIX_PRESENT);
        } else {
            editor.putInt(key, prior).putBoolean(key + SUFFIX_PRESENT, true);
        }
        editor.apply();
    }

    private static Integer readPrior(SharedPreferences prefs, String key) {
        if (!prefs.getBoolean(key + SUFFIX_PRESENT, false)) {
            return null;
        }
        return prefs.getInt(key, SettingsEntry.DEFAULT_REVERT_VALUE);
    }

    private static SharedPreferences.Editor removePrior(SharedPreferences.Editor editor,
                                                        String key) {
        return editor.remove(key).remove(key + SUFFIX_PRESENT);
    }

    private static String systemDecorsKey(int displayId) {
        return String.format(KEY_PRIOR_SYSTEM_DECORS_TEMPLATE, displayId);
    }

    private static String shouldShowImeKey(int displayId) {
        return String.format(KEY_PRIOR_SHOULD_SHOW_IME_TEMPLATE, displayId);
    }

    // -------------------------------------------------------------------------
    // Bulk clear
    // -------------------------------------------------------------------------

    /**
     * Clears the global prior-value flags ({@code force_resizable_activities} and
     * {@code enable_freeform_support}) and their presence companions. Per-display priors are
     * keyed by display ID; wipe those with {@link #clearPriorsForDisplay}.
     *
     * @param prefs the shared preferences to write to; must not be null.
     */
    public static void clearPriors(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        removePrior(editor, KEY_PRIOR_FORCE_RESIZABLE);
        removePrior(editor, KEY_PRIOR_ENABLE_FREEFORM);
        editor.apply();
    }

    /**
     * Clears the enabled flag, the target-package selection, and the global prior-value flags,
     * resetting AutoX's global settings to their defaults. Per-display priors are keyed by
     * display ID and are wiped via {@link #clearPriorsForDisplay}.
     *
     * @param prefs the shared preferences to write to; must not be null.
     */
    public static void clear(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit()
                .remove(KEY_ENABLED)
                .remove(KEY_TARGET_PACKAGE);
        removePrior(editor, KEY_PRIOR_FORCE_RESIZABLE);
        removePrior(editor, KEY_PRIOR_ENABLE_FREEFORM);
        editor.apply();
    }
}

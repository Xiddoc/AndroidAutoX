package com.xiddoc.androidautox;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Objects;

/**
 * Thin persistence wrapper for per-tweak enable/disable state and the
 * "reboot pending" marker.
 *
 * <h3>Shared-preferences file</h3>
 * <p>All data is stored in the same {@link SharedPreferences} file already used by
 * the rest of the app — {@value PhixitEngine#PREFS} — opened via
 * {@code context.getSharedPreferences(PhixitEngine.PREFS, MODE_PRIVATE)}.
 *
 * <p>Why the same file? {@code Activity.getPreferences()} (used in legacy code) is
 * shorthand for {@code getSharedPreferences(activity.getLocalClassName(), …)}, and
 * {@code MainActivity}'s local class name is literally {@code "MainActivity"}, which
 * is the value of {@link PhixitEngine#PREFS}.  Sharing the file means this class
 * is compatible with booleans already written by the existing {@code MainActivity}
 * {@code save(key, boolean)} / {@code load(key)} helpers without any migration.
 *
 * <h3>Key scheme</h3>
 * <ul>
 *   <li><b>Enabled flag</b>: stored under the bare tweak key (e.g. {@code "aa_six_tap"}).
 *       This is identical to the key used by the existing {@code save}/{@code load} path,
 *       so the two are fully compatible.</li>
 *   <li><b>Reboot-pending marker</b>: stored under {@code key + "__reboot_pending"}.
 *       The double-underscore infix is chosen so it cannot collide with a real tweak key
 *       that happens to share the suffix (tweak keys never contain {@code "__"}).</li>
 * </ul>
 */
public final class TweakStateStore {

    /** Suffix appended to a tweak key to form its reboot-pending storage key. */
    static final String REBOOT_PENDING_SUFFIX = "__reboot_pending";

    private final SharedPreferences prefs;

    /**
     * Constructs a store backed by the application's {@link PhixitEngine#PREFS} file.
     *
     * @param context any {@link Context}; {@code getApplicationContext()} is called
     *                internally so the store does not accidentally hold an Activity.
     * @throws NullPointerException if {@code context} is null
     */
    public TweakStateStore(Context context) {
        Objects.requireNonNull(context, "context");
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE);
    }

    // -------------------------------------------------------------------------
    // Enabled / disabled
    // -------------------------------------------------------------------------

    /**
     * Returns whether the tweak identified by {@code key} is marked as enabled.
     * Defaults to {@code false} if no value has been written yet (matches the
     * behaviour of the existing {@code MainActivity.load(key)} helper).
     *
     * @param key the tweak key, e.g. {@code "aa_six_tap"}
     * @throws IllegalArgumentException if {@code key} is null or empty
     */
    public boolean isEnabled(String key) {
        checkKey(key);
        return prefs.getBoolean(key, false);
    }

    /**
     * Persists the enabled/disabled state for {@code key}.
     *
     * <p>Writing {@code true} under the bare key is exactly what the existing
     * {@code MainActivity.save(key, true)} does, so data written here is readable
     * by the legacy path and vice-versa.
     *
     * <p>Writes are async via {@link SharedPreferences.Editor#apply()}; this is safe
     * because the root service does not read this file.
     *
     * @param key   the tweak key
     * @param value {@code true} to mark the tweak as enabled
     * @throws IllegalArgumentException if {@code key} is null or empty
     */
    public void setEnabled(String key, boolean value) {
        checkKey(key);
        prefs.edit().putBoolean(key, value).apply();
    }

    // -------------------------------------------------------------------------
    // Reboot-pending marker
    // -------------------------------------------------------------------------

    /**
     * Returns whether a "reboot pending" marker is set for {@code key}.
     * Defaults to {@code false} when no marker exists.
     *
     * @param key the tweak key
     * @throws IllegalArgumentException if {@code key} is null or empty
     */
    public boolean isRebootPending(String key) {
        checkKey(key);
        return prefs.getBoolean(rebootPendingKey(key), false);
    }

    /**
     * Sets or clears the "reboot pending" marker for {@code key}.
     *
     * <p>Writes are async via {@link SharedPreferences.Editor#apply()}; this is safe
     * because the root service does not read this file.
     *
     * @param key   the tweak key
     * @param value {@code true} to signal that the tweak has been applied but a
     *              reboot/confirmation is still needed; {@code false} to clear it
     * @throws IllegalArgumentException if {@code key} is null or empty
     */
    public void setRebootPending(String key, boolean value) {
        checkKey(key);
        prefs.edit().putBoolean(rebootPendingKey(key), value).apply();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Guards all public key-accepting methods against null or empty keys.
     *
     * @param key the tweak key to validate
     * @throws IllegalArgumentException if {@code key} is null or empty
     */
    private void checkKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key must not be null or empty");
        }
    }

    /**
     * Derives the storage key for the reboot-pending marker from the tweak key.
     * Package-private so tests can verify the scheme.
     */
    static String rebootPendingKey(String key) {
        return key + REBOOT_PENDING_SUFFIX;
    }
}

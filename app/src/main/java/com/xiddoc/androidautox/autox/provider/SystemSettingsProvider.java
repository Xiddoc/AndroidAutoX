package com.xiddoc.androidautox.autox.provider;

/**
 * Privileged-provider seam for reading and writing {@code Settings.Global} and
 * {@code Settings.Secure} integer keys.
 *
 * <p>Writing protected (e.g. {@code @SystemApi}) settings keys requires either
 * {@code WRITE_SECURE_SETTINGS}/{@code WRITE_GLOBAL_SETTINGS} (signature permissions),
 * or a privileged path (root reflection, or an LSPosed hook that relaxes the permission
 * check in {@code system_server}). This interface hides that mechanism so later
 * workstreams (WS3 input-mode / WS5 IME &amp; system-decor toggles) depend only on the
 * seam, not on how the bytes get written.
 *
 * <p>All methods are <b>best-effort and non-throwing</b>: instead of raising a
 * {@link SecurityException} they return a {@link SettingsResult} carrying the outcome
 * ({@code OK} / {@code DENIED} / {@code NOT_FOUND}). This makes capability detection a
 * data-flow concern rather than an exception-handling one.
 */
public interface SystemSettingsProvider {

    /**
     * Writes an integer into {@code Settings.Global}.
     *
     * @param key   the global settings key; must not be null/blank
     * @param value the integer value to store
     * @return {@link SettingsResult#ok()} on success, or {@link SettingsResult#denied()}
     *         if the provider lacked the privilege
     */
    SettingsResult putGlobalInt(String key, int value);

    /**
     * Reads an integer from {@code Settings.Global}.
     *
     * @param key the global settings key; must not be null/blank
     * @return {@link SettingsResult#ok(int)} carrying the value, {@link SettingsResult#notFound()}
     *         if the key is absent, or {@link SettingsResult#denied()} if unreadable
     */
    SettingsResult getGlobalInt(String key);

    /**
     * Writes an integer into {@code Settings.Secure}.
     *
     * @param key   the secure settings key; must not be null/blank
     * @param value the integer value to store
     * @return {@link SettingsResult#ok()} on success, or {@link SettingsResult#denied()}
     *         if the provider lacked the privilege
     */
    SettingsResult putSecureInt(String key, int value);

    /**
     * Reads an integer from {@code Settings.Secure}.
     *
     * @param key the secure settings key; must not be null/blank
     * @return {@link SettingsResult#ok(int)} carrying the value, {@link SettingsResult#notFound()}
     *         if the key is absent, or {@link SettingsResult#denied()} if unreadable
     */
    SettingsResult getSecureInt(String key);
}

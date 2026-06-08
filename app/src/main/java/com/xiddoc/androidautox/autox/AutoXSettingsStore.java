package com.xiddoc.androidautox.autox;

import android.content.SharedPreferences;

import com.xiddoc.androidautox.autox.ime.ImeDisplaySettingsSpec;

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

    // NOTE: these are deliberately app-local SharedPreferences keys (the "autox_prior_*"
    // namespace), NOT the Settings.Secure keys that ImeDisplaySettingsSpec writes
    // ("display_should_show_ime_<id>" / "display_should_show_system_decors_<id>"). They merely
    // remember the prior values we captured; the "system_decors" abbreviation drift from the
    // spec's "should_show_system_decors" is intentional and harmless for a private prefs key.

    /** Per-display key template for the prior {@code shouldShowSystemDecors} value. */
    static final String KEY_PRIOR_SYSTEM_DECORS_TEMPLATE = "autox_prior_system_decors_%d";

    /** Per-display key template for the prior {@code shouldShowIme} value. */
    static final String KEY_PRIOR_SHOULD_SHOW_IME_TEMPLATE = "autox_prior_should_show_ime_%d";

    // -------------------------------------------------------------------------
    // WS6 audio-routing state (persisted so revert survives process death)
    //
    // The per-UID audio affinity AutoX applies at session start must be cleared on
    // revert. The transient RouteDecision in AutoXScreen is lost on process death, so
    // the data needed to reconstruct a ClearAffinity revert is persisted here: the
    // guest UID and the device type/address that were routed. Absent is modeled with a
    // presence companion flag, identical to the prior-value encoding above.
    // -------------------------------------------------------------------------

    /** UID of the guest app whose audio was routed for the active session. */
    static final String KEY_AUDIO_UID = "autox_audio_uid";

    /** Car audio device type (the {@code CarAudioDevice} enum name) used for the route. */
    static final String KEY_AUDIO_DEVICE = "autox_audio_device";

    /** Car audio device address used for the route. */
    static final String KEY_AUDIO_ADDRESS = "autox_audio_address";

    /** Presence companion for the persisted audio-routing state. */
    static final String KEY_AUDIO_PRESENT = "autox_audio__present";

    /**
     * Neutral fallback returned by {@link #readPrior} for the unreachable case of a present
     * flag set true but the int value key missing (corrupted/partially-written prefs). It is
     * deliberately a local {@code 0}, not {@code SettingsEntry.DEFAULT_REVERT_VALUE}: the store
     * does not model a revert here, so there is no semantic link to that constant.
     */
    private static final int NEUTRAL_DEFAULT = 0;

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
        requireValidDisplayId(displayId);
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
        requireValidDisplayId(displayId);
        return readPrior(prefs, systemDecorsKey(displayId));
    }

    /**
     * Returns the persisted prior per-display {@code shouldShowSystemDecors} value for
     * {@code displayId} in the {@code int}/sentinel form consumed by
     * {@link ImeDisplaySettingsSpec#withPriorValues(int, int)}: an absent prior maps to
     * {@link ImeDisplaySettingsSpec#VALUE_UNSET} rather than {@code null}.
     *
     * <p>This is a convenience over {@link #getPriorShouldShowSystemDecors} so Wave-2
     * call-sites can feed {@code ImeDisplaySettingsSpec} directly without hand-rolling a
     * {@code x == null ? VALUE_UNSET : x} mapping.
     *
     * @param prefs     the shared preferences to read from; must not be null.
     * @param displayId the virtual display ID; must be &gt; 0.
     * @return the captured prior int, or {@link ImeDisplaySettingsSpec#VALUE_UNSET} if absent.
     */
    public static int getPriorShouldShowSystemDecorsOrUnset(SharedPreferences prefs,
                                                            int displayId) {
        Integer prior = getPriorShouldShowSystemDecors(prefs, displayId);
        return prior == null ? ImeDisplaySettingsSpec.VALUE_UNSET : prior;
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
        requireValidDisplayId(displayId);
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
        requireValidDisplayId(displayId);
        return readPrior(prefs, shouldShowImeKey(displayId));
    }

    /**
     * Returns the persisted prior per-display {@code shouldShowIme} value for {@code displayId}
     * in the {@code int}/sentinel form consumed by
     * {@link ImeDisplaySettingsSpec#withPriorValues(int, int)}: an absent prior maps to
     * {@link ImeDisplaySettingsSpec#VALUE_UNSET} rather than {@code null}.
     *
     * <p>This is a convenience over {@link #getPriorShouldShowIme} so Wave-2 call-sites can
     * feed {@code ImeDisplaySettingsSpec} directly without hand-rolling a
     * {@code x == null ? VALUE_UNSET : x} mapping.
     *
     * @param prefs     the shared preferences to read from; must not be null.
     * @param displayId the virtual display ID; must be &gt; 0.
     * @return the captured prior int, or {@link ImeDisplaySettingsSpec#VALUE_UNSET} if absent.
     */
    public static int getPriorShouldShowImeOrUnset(SharedPreferences prefs, int displayId) {
        Integer prior = getPriorShouldShowIme(prefs, displayId);
        return prior == null ? ImeDisplaySettingsSpec.VALUE_UNSET : prior;
    }

    /**
     * Clears the persisted per-display priors ({@code shouldShowSystemDecors} and
     * {@code shouldShowIme}) for a single {@code displayId}.
     *
     * @param prefs     the shared preferences to write to; must not be null.
     * @param displayId the virtual display ID whose priors to wipe.
     */
    public static void clearPriorsForDisplay(SharedPreferences prefs, int displayId) {
        SharedPreferences.Editor editor = prefs.edit();
        removePrior(editor, systemDecorsKey(displayId));
        removePrior(editor, shouldShowImeKey(displayId));
        editor.apply();
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
        return prefs.getInt(key, NEUTRAL_DEFAULT);
    }

    private static SharedPreferences.Editor removePrior(SharedPreferences.Editor editor,
                                                        String key) {
        return editor.remove(key).remove(key + SUFFIX_PRESENT);
    }

    /**
     * Rejects non-positive display IDs at capture time, mirroring
     * {@link ImeDisplaySettingsSpec#forDisplay(int)}'s {@code displayId > 0} contract so a bad
     * ID fails fast here instead of silently keying a prior under a nonsensical display.
     */
    private static void requireValidDisplayId(int displayId) {
        if (displayId <= 0) {
            throw new IllegalArgumentException("displayId must be > 0, got " + displayId);
        }
    }

    private static String systemDecorsKey(int displayId) {
        return String.format(KEY_PRIOR_SYSTEM_DECORS_TEMPLATE, displayId);
    }

    private static String shouldShowImeKey(int displayId) {
        return String.format(KEY_PRIOR_SHOULD_SHOW_IME_TEMPLATE, displayId);
    }

    // -------------------------------------------------------------------------
    // WS6 audio-routing state
    // -------------------------------------------------------------------------

    /**
     * Immutable snapshot of the audio routing applied for a session, persisted so a revert
     * can be reconstructed after process death. {@code deviceAddress} may be {@code null}
     * (e.g. an unusable/absent address), which is faithfully round-tripped.
     */
    public static final class AudioRouteState {
        /** The guest app UID that was routed. */
        public final int uid;
        /** The {@code CarAudioDevice} enum name routed to. Never null. */
        public final String deviceName;
        /** The device address routed to; may be {@code null}. */
        public final String deviceAddress;

        /**
         * @param uid           the guest app UID that was routed
         * @param deviceName    the {@code CarAudioDevice} enum name; must not be null
         * @param deviceAddress the device address; may be null
         */
        public AudioRouteState(int uid, String deviceName, String deviceAddress) {
            if (deviceName == null) {
                throw new IllegalArgumentException("deviceName must not be null");
            }
            this.uid = uid;
            this.deviceName = deviceName;
            this.deviceAddress = deviceAddress;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AudioRouteState)) return false;
            AudioRouteState s = (AudioRouteState) o;
            return uid == s.uid
                    && deviceName.equals(s.deviceName)
                    && (deviceAddress == null
                            ? s.deviceAddress == null
                            : deviceAddress.equals(s.deviceAddress));
        }

        @Override
        public int hashCode() {
            int h = uid;
            h = 31 * h + deviceName.hashCode();
            h = 31 * h + (deviceAddress == null ? 0 : deviceAddress.hashCode());
            return h;
        }

        @Override
        public String toString() {
            return "AudioRouteState{uid=" + uid + ", deviceName='" + deviceName
                    + "', deviceAddress='" + deviceAddress + "'}";
        }
    }

    /**
     * Persists the audio routing applied for the active session so {@code revertAudioRouting}
     * can reconstruct a clear after process death.
     *
     * @param prefs the shared preferences to write to; must not be null.
     * @param state the routing state to persist; must not be null.
     */
    public static void setAudioRouteState(SharedPreferences prefs, AudioRouteState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(KEY_AUDIO_UID, state.uid)
                .putString(KEY_AUDIO_DEVICE, state.deviceName)
                .putBoolean(KEY_AUDIO_PRESENT, true);
        if (state.deviceAddress == null) {
            editor.remove(KEY_AUDIO_ADDRESS);
        } else {
            editor.putString(KEY_AUDIO_ADDRESS, state.deviceAddress);
        }
        editor.apply();
    }

    /**
     * Returns the persisted audio routing state, or {@code null} if none was captured.
     *
     * @param prefs the shared preferences to read from; must not be null.
     * @return the persisted {@link AudioRouteState}, or {@code null} if absent.
     */
    public static AudioRouteState getAudioRouteState(SharedPreferences prefs) {
        if (!prefs.getBoolean(KEY_AUDIO_PRESENT, false)) {
            return null;
        }
        int uid = prefs.getInt(KEY_AUDIO_UID, -1);
        String deviceName = prefs.getString(KEY_AUDIO_DEVICE, null);
        if (deviceName == null) {
            // Corrupted/partially-written state: treat as absent.
            return null;
        }
        String address = prefs.getString(KEY_AUDIO_ADDRESS, null);
        return new AudioRouteState(uid, deviceName, address);
    }

    /**
     * Clears the persisted audio routing state.
     *
     * @param prefs the shared preferences to write to; must not be null.
     */
    public static void clearAudioRouteState(SharedPreferences prefs) {
        prefs.edit()
                .remove(KEY_AUDIO_UID)
                .remove(KEY_AUDIO_DEVICE)
                .remove(KEY_AUDIO_ADDRESS)
                .remove(KEY_AUDIO_PRESENT)
                .apply();
    }

    // -------------------------------------------------------------------------
    // Bulk clear
    // -------------------------------------------------------------------------

    /**
     * Clears the global prior-value flags ({@code force_resizable_activities} and
     * {@code enable_freeform_support}) and their presence companions.
     *
     * <p><strong>WARNING — this does NOT touch per-display priors.</strong> Per-display
     * entries are keyed by display ID, and the store cannot enumerate which display IDs were
     * ever captured, so they are intentionally left untouched here. The caller MUST invoke
     * {@link #clearPriorsForDisplay(SharedPreferences, int)} once per torn-down display;
     * forgetting to do so leaks per-display prior entries across enable/disable cycles.
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
     * resetting AutoX's global settings to their defaults.
     *
     * <p><strong>WARNING — this does NOT touch per-display priors.</strong> Per-display
     * entries are keyed by display ID, and the store cannot enumerate which display IDs were
     * ever captured, so a previously-set per-display prior SURVIVES this call. The caller MUST
     * invoke {@link #clearPriorsForDisplay(SharedPreferences, int)} once per torn-down display;
     * forgetting to do so leaks per-display prior entries across enable/disable cycles.
     *
     * @param prefs the shared preferences to write to; must not be null.
     */
    public static void clear(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit()
                .remove(KEY_ENABLED)
                .remove(KEY_TARGET_PACKAGE)
                .remove(KEY_AUDIO_UID)
                .remove(KEY_AUDIO_DEVICE)
                .remove(KEY_AUDIO_ADDRESS)
                .remove(KEY_AUDIO_PRESENT);
        removePrior(editor, KEY_PRIOR_FORCE_RESIZABLE);
        removePrior(editor, KEY_PRIOR_ENABLE_FREEFORM);
        editor.apply();
    }
}

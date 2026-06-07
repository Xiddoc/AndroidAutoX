package com.xiddoc.androidautox.autox.ime;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure-logic (no Android imports) specification of the per-display settings AutoX must
 * configure so that a projected app can raise Android Auto's software keyboard (IME) and
 * receive speech-to-text (STT) input on the virtual display.
 *
 * <h2>Background — per-display IME and system-decor flags</h2>
 * <p>Android's {@code WindowManagerService} enforces two per-display flags before it will
 * show an IME or system decorations on a virtual display:
 * <ol>
 *   <li><b>{@code shouldShowIme}</b> — stored as an integer (1=enabled, 0=disabled) under
 *       the key {@code "display_should_show_ime_<displayId>"} in {@code Settings.Secure}.
 *       When 1, WMS routes IME windows to this display and forwards input-connection
 *       events (including STT text injection) to the focused window there.</li>
 *   <li><b>{@code shouldShowSystemDecors}</b> — stored as an integer under the key
 *       {@code "display_should_show_system_decors_<displayId>"} in {@code Settings.Secure}.
 *       When 1, the system renders status-bar, navigation-bar, and other system-UI layers
 *       onto the virtual display. AutoX requires this so the IME frame (which is itself a
 *       system-UI layer) is admitted to the display.</li>
 * </ol>
 * <p>Both keys are protected by {@code WRITE_SECURE_SETTINGS}; they can only be written via
 * the privileged {@link com.xiddoc.androidautox.autox.provider.SystemSettingsProvider} seam
 * (root reflection or LSPosed hook) introduced by WS4.
 *
 * <h2>Trust requirement</h2>
 * <p>The virtual display must be created with
 * {@link com.xiddoc.androidautox.autox.VirtualDisplayConfig#FLAG_TRUSTED} so the
 * framework honours the per-display IME/decor flags in the first place. An untrusted
 * display ignores both settings regardless of their stored value.
 *
 * <h2>This class</h2>
 * <p>Models the pair of settings as an ordered, immutable list of {@link Entry} values, each
 * capturing: the settings key, the required (apply) value, and the prior (revert) value.
 * Callers obtain a spec via {@link #forDisplay(int)} and then pass it to
 * {@link ImeDisplaySettingsApplier} to actually write the settings.
 *
 * <p>This class has <strong>no Android imports</strong> and is 100% unit-testable with
 * plain JUnit.
 */
public final class ImeDisplaySettingsSpec {

    // -------------------------------------------------------------------------
    // Settings key templates (Secure namespace)
    // -------------------------------------------------------------------------

    /**
     * Template for {@code Settings.Secure} key that enables IME on a specific display.
     * The placeholder {@code %d} is replaced with the display ID.
     *
     * <p>The full key for display 42 is {@code "display_should_show_ime_42"}.
     */
    static final String KEY_TEMPLATE_SHOULD_SHOW_IME =
            "display_should_show_ime_%d";

    /**
     * Template for {@code Settings.Secure} key that enables system decorations (including
     * the IME frame layer) on a specific display.
     * The placeholder {@code %d} is replaced with the display ID.
     *
     * <p>The full key for display 42 is {@code "display_should_show_system_decors_42"}.
     */
    static final String KEY_TEMPLATE_SHOULD_SHOW_SYSTEM_DECORS =
            "display_should_show_system_decors_%d";

    // -------------------------------------------------------------------------
    // Integer flag values
    // -------------------------------------------------------------------------

    /** Integer value that enables a per-display flag. */
    public static final int VALUE_ENABLED = 1;

    /** Integer value that disables a per-display flag. */
    public static final int VALUE_DISABLED = 0;

    /**
     * Sentinel indicating a setting was absent (not set) before AutoX touched it.
     * When reverting an entry whose {@code priorValue} is {@code VALUE_UNSET},
     * the applier writes {@link #VALUE_DISABLED} rather than restoring a numeric value
     * that never existed.
     */
    public static final int VALUE_UNSET = -1;

    // -------------------------------------------------------------------------
    // Entry — one setting within the spec
    // -------------------------------------------------------------------------

    /**
     * One setting within the spec: a key, the required (apply) value, and the prior
     * (revert) value captured before AutoX writes to it.
     *
     * <p>When the prior value is {@link ImeDisplaySettingsSpec#VALUE_UNSET} it means the
     * key was absent in the settings store before AutoX set it, and the correct revert
     * action is to write {@link ImeDisplaySettingsSpec#VALUE_DISABLED} rather than
     * restore a numeric value that never existed.
     */
    public static final class Entry {

        /** The {@code Settings.Secure} key for this entry. Never null or blank. */
        public final String key;

        /**
         * The value to write when applying. Always {@link ImeDisplaySettingsSpec#VALUE_ENABLED}.
         */
        public final int applyValue;

        /**
         * The prior value that was read before applying. One of:
         * <ul>
         *   <li>{@link ImeDisplaySettingsSpec#VALUE_ENABLED} — already set to 1</li>
         *   <li>{@link ImeDisplaySettingsSpec#VALUE_DISABLED} — was explicitly 0</li>
         *   <li>{@link ImeDisplaySettingsSpec#VALUE_UNSET} — key was absent</li>
         * </ul>
         * Used by {@link ImeDisplaySettingsApplier#revert} to restore the original state.
         */
        public final int priorValue;

        private Entry(String key, int applyValue, int priorValue) {
            this.key = key;
            this.applyValue = applyValue;
            this.priorValue = priorValue;
        }

        /**
         * Creates an entry with the given key and prior value; the apply value is always
         * {@link ImeDisplaySettingsSpec#VALUE_ENABLED}.
         *
         * @param key        settings key; must not be null or blank
         * @param priorValue the value read before applying ({@code VALUE_ENABLED},
         *                   {@code VALUE_DISABLED}, or {@code VALUE_UNSET})
         * @throws IllegalArgumentException if key is null or blank, or if priorValue is
         *                                  not one of the defined constants
         */
        public static Entry of(String key, int priorValue) {
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("key must not be null or blank");
            }
            if (priorValue != VALUE_ENABLED
                    && priorValue != VALUE_DISABLED
                    && priorValue != VALUE_UNSET) {
                throw new IllegalArgumentException(
                        "priorValue must be VALUE_ENABLED, VALUE_DISABLED, or VALUE_UNSET; got "
                                + priorValue);
            }
            return new Entry(key, VALUE_ENABLED, priorValue);
        }

        /**
         * Package-private factory that sets an explicit {@code applyValue}.
         * Used only in tests to exercise the defensive branches of
         * {@link ImeDisplaySettingsSpec#isValid()}.
         */
        static Entry ofWithApplyValue(String key, int applyValue, int priorValue) {
            return new Entry(key, applyValue, priorValue);
        }

        /**
         * Returns {@code true} if the key was absent before AutoX set it.
         * The correct revert action is to write {@link ImeDisplaySettingsSpec#VALUE_DISABLED}
         * rather than restore a value that never existed.
         */
        public boolean wasUnset() {
            return priorValue == VALUE_UNSET;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry)) return false;
            Entry e = (Entry) o;
            return applyValue == e.applyValue
                    && priorValue == e.priorValue
                    && key.equals(e.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, applyValue, priorValue);
        }

        @Override
        public String toString() {
            return "Entry{key='" + key + "', apply=" + applyValue
                    + ", prior=" + priorValue + '}';
        }
    }

    // -------------------------------------------------------------------------
    // ImeDisplaySettingsSpec
    // -------------------------------------------------------------------------

    private final int displayId;
    private final List<Entry> applyList;
    private final List<Entry> revertList;

    private ImeDisplaySettingsSpec(int displayId, List<Entry> applyList) {
        this.displayId = displayId;
        // Apply order: system-decors first (so the IME frame layer is admitted before
        // the IME entry itself is enabled), then IME.
        this.applyList = Collections.unmodifiableList(new java.util.ArrayList<>(applyList));
        // Revert in reverse apply order: IME first, then system-decors.
        java.util.ArrayList<Entry> rev = new java.util.ArrayList<>(applyList);
        Collections.reverse(rev);
        this.revertList = Collections.unmodifiableList(rev);
    }

    /**
     * Builds a spec for {@code displayId} with both prior values set to
     * {@link #VALUE_UNSET} (i.e. no prior value known — keys were absent).
     *
     * <p>This is the starting-point constructor; use
     * {@link #withPriorValues(int, int)} after reading the current settings store
     * values (just before applying) so {@link ImeDisplaySettingsApplier} can revert.
     *
     * @param displayId the virtual display ID; must be &gt; 0
     * @throws IllegalArgumentException if {@code displayId} &le; 0
     */
    public static ImeDisplaySettingsSpec forDisplay(int displayId) {
        if (displayId <= 0) {
            throw new IllegalArgumentException(
                    "displayId must be > 0, got " + displayId);
        }
        String decorKey = String.format(KEY_TEMPLATE_SHOULD_SHOW_SYSTEM_DECORS, displayId);
        String imeKey = String.format(KEY_TEMPLATE_SHOULD_SHOW_IME, displayId);
        return new ImeDisplaySettingsSpec(displayId, Arrays.asList(
                Entry.of(decorKey, VALUE_UNSET),
                Entry.of(imeKey, VALUE_UNSET)
        ));
    }

    /**
     * Returns a new spec with the recorded prior values replaced.
     *
     * <p>Call this after reading the current settings store values (just before
     * {@link ImeDisplaySettingsApplier#apply}) so revert can correctly restore the
     * original state.
     *
     * @param priorSystemDecors the prior value for the system-decors key; must be
     *                          {@link #VALUE_ENABLED}, {@link #VALUE_DISABLED}, or
     *                          {@link #VALUE_UNSET}
     * @param priorIme          the prior value for the IME key; same constraints
     * @throws IllegalArgumentException if either prior value is out of range
     */
    public ImeDisplaySettingsSpec withPriorValues(int priorSystemDecors, int priorIme) {
        String decorKey = String.format(KEY_TEMPLATE_SHOULD_SHOW_SYSTEM_DECORS, displayId);
        String imeKey = String.format(KEY_TEMPLATE_SHOULD_SHOW_IME, displayId);
        return new ImeDisplaySettingsSpec(displayId, Arrays.asList(
                Entry.of(decorKey, priorSystemDecors),
                Entry.of(imeKey, priorIme)
        ));
    }

    /**
     * Package-private factory that builds a spec with the given raw entry list.
     * Used only in tests to exercise the defensive branches of {@link #isValid()}.
     */
    static ImeDisplaySettingsSpec withEntries(int displayId, java.util.List<Entry> entries) {
        return new ImeDisplaySettingsSpec(displayId, entries);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the virtual display ID this spec targets. */
    public int getDisplayId() {
        return displayId;
    }

    /**
     * Returns the ordered list of entries to write when enabling IME on the display.
     * Apply order: system-decors entry first, then IME entry.
     */
    public List<Entry> getApplyList() {
        return applyList;
    }

    /**
     * Returns the ordered list of entries to write when reverting (restoring prior
     * settings). Revert order is the reverse of apply order: IME entry first, then
     * system-decors entry.
     */
    public List<Entry> getRevertList() {
        return revertList;
    }

    /** Returns the {@code Settings.Secure} key for the {@code shouldShowIme} flag. */
    public String imeKey() {
        return String.format(KEY_TEMPLATE_SHOULD_SHOW_IME, displayId);
    }

    /** Returns the {@code Settings.Secure} key for the {@code shouldShowSystemDecors} flag. */
    public String decorKey() {
        return String.format(KEY_TEMPLATE_SHOULD_SHOW_SYSTEM_DECORS, displayId);
    }

    /**
     * Validates that the spec is internally consistent: both entries must target this
     * display's keys, and both apply values must be {@link #VALUE_ENABLED}.
     *
     * @return {@code true} if the spec is valid
     */
    public boolean isValid() {
        if (applyList.size() != 2) return false;
        for (Entry e : applyList) {
            if (e.applyValue != VALUE_ENABLED) return false;
            if (!e.key.endsWith(String.valueOf(displayId))) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImeDisplaySettingsSpec)) return false;
        ImeDisplaySettingsSpec s = (ImeDisplaySettingsSpec) o;
        return displayId == s.displayId && applyList.equals(s.applyList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayId, applyList);
    }

    @Override
    public String toString() {
        return "ImeDisplaySettingsSpec{displayId=" + displayId
                + ", apply=" + applyList + '}';
    }
}

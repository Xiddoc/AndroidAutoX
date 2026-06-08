package com.xiddoc.androidautox.autox.ime;

import com.xiddoc.androidautox.autox.provider.SettingsEntry;

import java.util.ArrayList;
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
 *   <li><b>{@code shouldShowIme}</b> — integer (1=enabled, 0=disabled) under the key
 *       {@code "display_should_show_ime_<displayId>"} in {@code Settings.Secure}.</li>
 *   <li><b>{@code shouldShowSystemDecors}</b> — integer under the key
 *       {@code "display_should_show_system_decors_<displayId>"} in {@code Settings.Secure}.</li>
 * </ol>
 * <p>Both keys are protected by {@code WRITE_SECURE_SETTINGS}; they can only be written via
 * the privileged {@link com.xiddoc.androidautox.autox.provider.SystemSettingsProvider} seam.
 *
 * <h2>Shared entry model</h2>
 * <p>This spec now produces shared {@link SettingsEntry} lists (the same model used by the
 * freeform/global spec) that are written by the shared
 * {@link com.xiddoc.androidautox.autox.provider.SettingsApplier} constructed with
 * {@code Namespace.SECURE}. A prior value of {@link #VALUE_UNSET} maps to an absent-key
 * entry ({@link SettingsEntry#forAbsentKey}, reverts to disabled); a prior value of 0/1 maps
 * to an existing-key entry ({@link SettingsEntry#forExistingKey}, restores that value).
 *
 * <p>This class has <strong>no Android imports</strong> and is 100% unit-testable.
 */
public final class ImeDisplaySettingsSpec {

    /**
     * Template for the {@code Settings.Secure} key that enables IME on a specific display;
     * {@code %d} is the display ID. The full key for display 42 is
     * {@code "display_should_show_ime_42"}.
     */
    static final String KEY_TEMPLATE_SHOULD_SHOW_IME = "display_should_show_ime_%d";

    /**
     * Template for the {@code Settings.Secure} key that enables system decorations on a
     * specific display; {@code %d} is the display ID.
     */
    static final String KEY_TEMPLATE_SHOULD_SHOW_SYSTEM_DECORS =
            "display_should_show_system_decors_%d";

    /** Integer value that enables a per-display flag. */
    public static final int VALUE_ENABLED = 1;

    /** Integer value that disables a per-display flag. */
    public static final int VALUE_DISABLED = 0;

    /** Sentinel indicating a setting was absent (not set) before AutoX touched it. */
    public static final int VALUE_UNSET = -1;

    private final int displayId;
    private final int priorSystemDecors;
    private final int priorIme;

    private ImeDisplaySettingsSpec(int displayId, int priorSystemDecors, int priorIme) {
        this.displayId = displayId;
        this.priorSystemDecors = priorSystemDecors;
        this.priorIme = priorIme;
    }

    /**
     * Builds a spec for {@code displayId} with both prior values {@link #VALUE_UNSET}.
     *
     * @param displayId the virtual display ID; must be &gt; 0
     * @throws IllegalArgumentException if {@code displayId} &le; 0
     */
    public static ImeDisplaySettingsSpec forDisplay(int displayId) {
        if (displayId <= 0) {
            throw new IllegalArgumentException("displayId must be > 0, got " + displayId);
        }
        return new ImeDisplaySettingsSpec(displayId, VALUE_UNSET, VALUE_UNSET);
    }

    /**
     * Returns a new spec with the recorded prior values replaced.
     *
     * @param priorSystemDecors prior value for the system-decors key; must be
     *                          {@link #VALUE_ENABLED}, {@link #VALUE_DISABLED}, or
     *                          {@link #VALUE_UNSET}
     * @param priorIme          prior value for the IME key; same constraints
     * @throws IllegalArgumentException if either prior value is out of range
     */
    public ImeDisplaySettingsSpec withPriorValues(int priorSystemDecors, int priorIme) {
        validatePrior(priorSystemDecors);
        validatePrior(priorIme);
        return new ImeDisplaySettingsSpec(displayId, priorSystemDecors, priorIme);
    }

    private static void validatePrior(int v) {
        if (v != VALUE_ENABLED && v != VALUE_DISABLED && v != VALUE_UNSET) {
            throw new IllegalArgumentException(
                    "priorValue must be VALUE_ENABLED, VALUE_DISABLED, or VALUE_UNSET; got " + v);
        }
    }

    /** Returns the virtual display ID this spec targets. */
    public int getDisplayId() {
        return displayId;
    }

    /**
     * Returns the recorded prior value for the system-decors key: {@link #VALUE_ENABLED},
     * {@link #VALUE_DISABLED}, or {@link #VALUE_UNSET} when the key was absent before AutoX
     * touched it.
     */
    public int getPriorSystemDecors() {
        return priorSystemDecors;
    }

    /**
     * Returns the recorded prior value for the IME key: {@link #VALUE_ENABLED},
     * {@link #VALUE_DISABLED}, or {@link #VALUE_UNSET} when the key was absent before AutoX
     * touched it.
     */
    public int getPriorIme() {
        return priorIme;
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
     * Returns the ordered {@link SettingsEntry} list to write when enabling IME on the
     * display. Apply order: system-decors first (so the IME frame layer is admitted before
     * the IME entry itself is enabled), then IME.
     */
    public List<SettingsEntry> applyEntries() {
        List<SettingsEntry> list = new ArrayList<>(2);
        list.add(entryFor(decorKey(), priorSystemDecors));
        list.add(entryFor(imeKey(), priorIme));
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns the ordered {@link SettingsEntry} list to write when reverting. Revert order is
     * the reverse of apply order: IME first, then system-decors. Each entry's
     * {@link SettingsEntry#revertValue()} restores the prior value (or
     * {@link #VALUE_DISABLED} when the key was unset).
     */
    public List<SettingsEntry> revertEntries() {
        List<SettingsEntry> list = new ArrayList<>(2);
        list.add(entryFor(imeKey(), priorIme));
        list.add(entryFor(decorKey(), priorSystemDecors));
        return Collections.unmodifiableList(list);
    }

    /**
     * Maps a per-display key + prior value onto a shared {@link SettingsEntry}. A
     * {@link #VALUE_UNSET} prior becomes an absent-key entry (revert → disabled); a
     * known 0/1 prior becomes an existing-key entry (revert → that prior value). The apply
     * value is always {@link #VALUE_ENABLED}.
     */
    private static SettingsEntry entryFor(String key, int prior) {
        if (prior == VALUE_UNSET) {
            return SettingsEntry.forAbsentKey(key, VALUE_ENABLED);
        }
        return SettingsEntry.forExistingKey(key, VALUE_ENABLED, prior);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImeDisplaySettingsSpec)) return false;
        ImeDisplaySettingsSpec s = (ImeDisplaySettingsSpec) o;
        return displayId == s.displayId
                && priorSystemDecors == s.priorSystemDecors
                && priorIme == s.priorIme;
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayId, priorSystemDecors, priorIme);
    }

    @Override
    public String toString() {
        return "ImeDisplaySettingsSpec{displayId=" + displayId
                + ", priorDecor=" + priorSystemDecors + ", priorIme=" + priorIme + '}';
    }
}

package com.xiddoc.androidautox.autox.provider;

import java.util.Objects;

/**
 * Pure, immutable description of one settings key that AutoX writes and later reverts.
 *
 * <p>Shared by every AutoX settings feature (freeform/global launch enablement,
 * per-display IME/system-decor toggles) so there is exactly one entry model and one
 * {@link SettingsApplier} instead of a per-feature duplicate. No Android imports — 100%
 * unit-testable and not excluded from the coverage gate.
 *
 * <p>An entry carries:
 * <ul>
 *   <li>{@link #key} — the settings key to write.</li>
 *   <li>{@link #applyValue} — the value written on the apply pass.</li>
 *   <li>{@link #revertStrategy} — how to revert: restore the captured prior value, or write
 *       a hard default ({@link #DEFAULT_REVERT_VALUE}) because the key was absent.</li>
 *   <li>{@link #priorValue} — the value to restore when the strategy is
 *       {@link RevertStrategy#RESTORE_PRIOR} (ignored otherwise).</li>
 * </ul>
 */
public final class SettingsEntry {

    /**
     * Value written on revert when a key was absent before AutoX touched it: {@code 0}
     * (explicitly disable the feature rather than leave it ambiguously absent).
     */
    public static final int DEFAULT_REVERT_VALUE = 0;

    /** How to revert a single {@link SettingsEntry} when AutoX is disabled. */
    public enum RevertStrategy {
        /** The key had a value before AutoX wrote it; restore that captured prior value. */
        RESTORE_PRIOR,
        /** The key was absent; write {@link SettingsEntry#DEFAULT_REVERT_VALUE} on revert. */
        WRITE_DEFAULT
    }

    /** The settings key this entry manages. Never null or blank. */
    public final String key;
    /** The value written when applying. */
    public final int applyValue;
    /** The revert strategy for this entry. Never null. */
    public final RevertStrategy revertStrategy;
    /** The value restored when {@link #revertStrategy} is {@link RevertStrategy#RESTORE_PRIOR}. */
    public final int priorValue;

    private SettingsEntry(String key, int applyValue, RevertStrategy revertStrategy,
                          int priorValue) {
        this.key = key;
        this.applyValue = applyValue;
        this.revertStrategy = revertStrategy;
        this.priorValue = priorValue;
    }

    /**
     * Builds an entry for a key that was <em>absent</em> before AutoX touched it; revert
     * writes {@link #DEFAULT_REVERT_VALUE}.
     *
     * @param key        the settings key; must be non-null and non-blank
     * @param applyValue the value to write when enabling; must be &ge; 0
     * @throws IllegalArgumentException if {@code key} is null/blank or {@code applyValue} &lt; 0
     */
    public static SettingsEntry forAbsentKey(String key, int applyValue) {
        validateKey(key);
        validateApplyValue(applyValue);
        return new SettingsEntry(key, applyValue, RevertStrategy.WRITE_DEFAULT, 0);
    }

    /**
     * Builds an entry for a key that <em>had a prior value</em> before AutoX touched it;
     * revert restores that prior value.
     *
     * @param key        the settings key; must be non-null and non-blank
     * @param applyValue the value to write when enabling; must be &ge; 0
     * @param priorValue the value to restore on revert
     * @throws IllegalArgumentException if {@code key} is null/blank or {@code applyValue} &lt; 0
     */
    public static SettingsEntry forExistingKey(String key, int applyValue, int priorValue) {
        validateKey(key);
        validateApplyValue(applyValue);
        return new SettingsEntry(key, applyValue, RevertStrategy.RESTORE_PRIOR, priorValue);
    }

    /**
     * Convenience factory: builds an entry from a boxed prior value, choosing
     * {@link #forAbsentKey} when {@code priorValue} is {@code null} or
     * {@link #forExistingKey} otherwise.
     *
     * @param key        the settings key; must be non-null and non-blank
     * @param applyValue the value to write when enabling; must be &ge; 0
     * @param priorValue the captured prior value, or {@code null} if the key was absent
     */
    public static SettingsEntry of(String key, int applyValue, Integer priorValue) {
        if (priorValue == null) {
            return forAbsentKey(key, applyValue);
        }
        return forExistingKey(key, applyValue, priorValue);
    }

    /**
     * The concrete value a revert operation should write:
     * {@link #priorValue} for {@link RevertStrategy#RESTORE_PRIOR}, otherwise
     * {@link #DEFAULT_REVERT_VALUE}.
     */
    public int revertValue() {
        return revertStrategy == RevertStrategy.RESTORE_PRIOR ? priorValue : DEFAULT_REVERT_VALUE;
    }

    /** @return a copy of this entry with the apply/revert roles swapped, for revert passes. */
    public SettingsEntry asRevertEntry() {
        // A revert pass writes revertValue() as the value; encode that as the applyValue of a
        // RESTORE_PRIOR entry so the same SettingsApplier writes it.
        return new SettingsEntry(key, revertValue(), RevertStrategy.RESTORE_PRIOR, revertValue());
    }

    private static void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key must be non-null and non-blank, got: " + key);
        }
    }

    private static void validateApplyValue(int applyValue) {
        if (applyValue < 0) {
            throw new IllegalArgumentException("applyValue must be >= 0, got: " + applyValue);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SettingsEntry)) return false;
        SettingsEntry that = (SettingsEntry) o;
        return applyValue == that.applyValue
                && priorValue == that.priorValue
                && revertStrategy == that.revertStrategy
                && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, applyValue, revertStrategy, priorValue);
    }

    @Override
    public String toString() {
        return "SettingsEntry{key='" + key + "', apply=" + applyValue
                + ", revert=" + revertStrategy + ", prior=" + priorValue + '}';
    }
}

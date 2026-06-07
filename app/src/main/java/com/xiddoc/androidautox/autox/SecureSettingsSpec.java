package com.xiddoc.androidautox.autox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure, immutable specification of every {@code Settings.Global} key that AutoX must write
 * to enable arbitrary-app launch on its virtual display, and how to revert each key when
 * AutoX is disabled.
 *
 * <h2>Keys managed</h2>
 * <ul>
 *   <li>{@code force_resizable_activities} = 1 — tells the activity manager to treat every
 *       activity as resizable regardless of its manifest declaration.  Without this flag,
 *       many non-game apps declare {@code resizeableActivity="false"} and the system refuses
 *       to launch them onto a secondary virtual display.
 *   <li>{@code enable_freeform_support} = 1 — enables the freeform windowing mode, which is
 *       a prerequisite for Android to honor
 *       {@code ActivityOptions.setLaunchBounds}/{@code setLaunchDisplayId} on devices
 *       that ship without freeform enabled by default (i.e. most non-desktop Android builds).
 * </ul>
 *
 * <h2>Revert strategy</h2>
 * <p>Before applying, the caller is expected to read the current value of each key via
 * {@link com.xiddoc.androidautox.autox.provider.SystemSettingsProvider#getGlobalInt}.
 * Each {@link Entry} records the {@link RevertStrategy}:
 * <ul>
 *   <li>{@link RevertStrategy#RESTORE_PRIOR} — the key had a value before AutoX touched it;
 *       revert by writing that prior value back.
 *   <li>{@link RevertStrategy#WRITE_DEFAULT} — the key was absent; revert by writing the
 *       {@link #DEFAULT_REVERT_VALUE} (always {@code 0}), which disables the feature.
 * </ul>
 *
 * <h2>No Android imports</h2>
 * <p>This class is deliberately framework-free so it can be exercised by plain JUnit tests
 * on the JVM without Robolectric.  It is <em>not</em> in {@code jacocoExclusions} and must
 * remain at 100% line + branch coverage.
 */
public final class SecureSettingsSpec {

    // -----------------------------------------------------------------------
    // Public constants — the Settings.Global keys AutoX manages
    // -----------------------------------------------------------------------

    /**
     * {@code Settings.Global} key that forces every activity to be treated as
     * resizable by the system, regardless of its manifest declaration.
     */
    public static final String KEY_FORCE_RESIZABLE = "force_resizable_activities";

    /**
     * {@code Settings.Global} key that enables freeform windowing mode, which is
     * required for {@code ActivityOptions.setLaunchBounds} and
     * {@code ActivityOptions.setLaunchDisplayId} to be honored on most devices.
     */
    public static final String KEY_ENABLE_FREEFORM = "enable_freeform_support";

    /**
     * The value written to each key when AutoX is enabled: {@code 1} (feature on).
     */
    public static final int ENABLED_VALUE = 1;

    /**
     * The default revert value used when a key was absent before AutoX enabled it.
     * Writing {@code 0} explicitly disables the feature rather than leaving the key
     * in an ambiguous absent state.
     */
    public static final int DEFAULT_REVERT_VALUE = 0;

    // -----------------------------------------------------------------------
    // RevertStrategy
    // -----------------------------------------------------------------------

    /**
     * Describes how to revert a single {@link Entry} when AutoX is disabled.
     */
    public enum RevertStrategy {
        /**
         * The key had a value before AutoX wrote to it.  Revert by writing the
         * captured prior value back via the settings provider.
         */
        RESTORE_PRIOR,

        /**
         * The key was absent when AutoX first enabled it.  Revert by writing
         * {@link SecureSettingsSpec#DEFAULT_REVERT_VALUE} (0) to explicitly
         * disable the feature.
         */
        WRITE_DEFAULT,
    }

    // -----------------------------------------------------------------------
    // Entry — one managed key
    // -----------------------------------------------------------------------

    /**
     * Immutable record describing a single {@code Settings.Global} key that AutoX
     * manages, together with the value to write and the revert strategy.
     */
    public static final class Entry {

        private final String key;
        private final int enabledValue;
        private final RevertStrategy revertStrategy;
        private final int priorValue; // meaningful only when strategy == RESTORE_PRIOR

        private Entry(String key, int enabledValue, RevertStrategy revertStrategy,
                      int priorValue) {
            this.key = key;
            this.enabledValue = enabledValue;
            this.revertStrategy = revertStrategy;
            this.priorValue = priorValue;
        }

        /**
         * Constructs an {@link Entry} for a key that was <em>absent</em> before
         * AutoX touched it.  Revert will write {@link SecureSettingsSpec#DEFAULT_REVERT_VALUE}.
         *
         * @param key          the {@code Settings.Global} key; must be non-null and non-blank
         * @param enabledValue the value to write when enabling; must be &ge; 0
         * @throws IllegalArgumentException if {@code key} is null or blank, or if
         *                                  {@code enabledValue} is negative
         */
        public static Entry forAbsentKey(String key, int enabledValue) {
            validateKey(key);
            validateEnabledValue(enabledValue);
            return new Entry(key, enabledValue, RevertStrategy.WRITE_DEFAULT, 0);
        }

        /**
         * Constructs an {@link Entry} for a key that <em>had a prior value</em> before
         * AutoX touched it.  Revert will restore that captured prior value.
         *
         * @param key          the {@code Settings.Global} key; must be non-null and non-blank
         * @param enabledValue the value to write when enabling; must be &ge; 0
         * @param priorValue   the value to restore on revert
         * @throws IllegalArgumentException if {@code key} is null or blank, or if
         *                                  {@code enabledValue} is negative
         */
        public static Entry forExistingKey(String key, int enabledValue, int priorValue) {
            validateKey(key);
            validateEnabledValue(enabledValue);
            return new Entry(key, enabledValue, RevertStrategy.RESTORE_PRIOR, priorValue);
        }

        /**
         * The {@code Settings.Global} key this entry manages.  Never null or blank.
         */
        public String key() {
            return key;
        }

        /**
         * The integer value to write when AutoX enables this setting.
         */
        public int enabledValue() {
            return enabledValue;
        }

        /**
         * The revert strategy for this entry: restore the prior value or write the
         * hard-coded default.
         */
        public RevertStrategy revertStrategy() {
            return revertStrategy;
        }

        /**
         * The value recorded when this entry was created with
         * {@link #forExistingKey}; it is the value to restore on revert when the
         * strategy is {@link RevertStrategy#RESTORE_PRIOR}.  Conventionally
         * {@code 0} (and meaningless) when the strategy is
         * {@link RevertStrategy#WRITE_DEFAULT}.
         */
        public int priorValue() {
            return priorValue;
        }

        /**
         * Computes the concrete integer that should be written by a revert operation.
         *
         * <ul>
         *   <li>If {@link #revertStrategy()} is {@link RevertStrategy#RESTORE_PRIOR},
         *       returns {@link #priorValue()}.
         *   <li>If {@link #revertStrategy()} is {@link RevertStrategy#WRITE_DEFAULT},
         *       returns {@link SecureSettingsSpec#DEFAULT_REVERT_VALUE}.
         * </ul>
         *
         * @return the integer to pass to the settings provider on revert
         */
        public int revertValue() {
            if (revertStrategy == RevertStrategy.RESTORE_PRIOR) {
                return priorValue;
            }
            return DEFAULT_REVERT_VALUE;
        }

        // -----------------------------------------------------------------
        // Validation helpers
        // -----------------------------------------------------------------

        private static void validateKey(String key) {
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "key must be non-null and non-blank, got: " + key);
            }
        }

        private static void validateEnabledValue(int enabledValue) {
            if (enabledValue < 0) {
                throw new IllegalArgumentException(
                        "enabledValue must be >= 0, got: " + enabledValue);
            }
        }
    }

    // -----------------------------------------------------------------------
    // SecureSettingsSpec factory / ordered lists
    // -----------------------------------------------------------------------

    /**
     * Builds the ordered list of entries that must be applied to enable AutoX's
     * resizable/freeform launch support, given the current (pre-enable) values read
     * from the system.
     *
     * <p>Pass the result of
     * {@link com.xiddoc.androidautox.autox.provider.SystemSettingsProvider#getGlobalInt}
     * for each key.  If the provider returned
     * {@link com.xiddoc.androidautox.autox.provider.SettingsResult#notFound()}, pass
     * {@code null} for that key's prior value; if it returned
     * {@link com.xiddoc.androidautox.autox.provider.SettingsResult#ok(int)}, pass the
     * boxed value.
     *
     * <p>Apply order: {@code force_resizable_activities} first, then
     * {@code enable_freeform_support}.
     *
     * @param priorForceResizable current value of {@code force_resizable_activities},
     *                            or {@code null} if the key was absent
     * @param priorEnableFreeform current value of {@code enable_freeform_support},
     *                            or {@code null} if the key was absent
     * @return an unmodifiable ordered list of entries to apply
     */
    public static List<Entry> applyList(Integer priorForceResizable,
                                        Integer priorEnableFreeform) {
        List<Entry> list = new ArrayList<>(2);
        list.add(makeEntry(KEY_FORCE_RESIZABLE, priorForceResizable));
        list.add(makeEntry(KEY_ENABLE_FREEFORM, priorEnableFreeform));
        return Collections.unmodifiableList(list);
    }

    /**
     * Builds the ordered list of entries to revert when AutoX is disabled, given
     * the prior-value snapshots captured just before the last {@link #applyList} call.
     *
     * <p>Revert order is the reverse of apply order:
     * {@code enable_freeform_support} first, then {@code force_resizable_activities}.
     *
     * @param priorForceResizable prior value of {@code force_resizable_activities}
     *                            (as passed to {@link #applyList}), or {@code null}
     * @param priorEnableFreeform prior value of {@code enable_freeform_support}
     *                            (as passed to {@link #applyList}), or {@code null}
     * @return an unmodifiable ordered list of entries to revert
     */
    public static List<Entry> revertList(Integer priorForceResizable,
                                         Integer priorEnableFreeform) {
        List<Entry> list = new ArrayList<>(2);
        // Reverse order: freeform first, then force-resizable.
        list.add(makeEntry(KEY_ENABLE_FREEFORM, priorEnableFreeform));
        list.add(makeEntry(KEY_FORCE_RESIZABLE, priorForceResizable));
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns the ordered list of {@code Settings.Global} keys that AutoX manages
     * for resizable/freeform launch.  Callers can iterate this list to read the
     * current value of each key before calling {@link #applyList} or
     * {@link #revertList}.
     *
     * @return an unmodifiable ordered list: [{@link #KEY_FORCE_RESIZABLE},
     *         {@link #KEY_ENABLE_FREEFORM}]
     */
    public static List<String> managedKeys() {
        List<String> keys = new ArrayList<>(2);
        keys.add(KEY_FORCE_RESIZABLE);
        keys.add(KEY_ENABLE_FREEFORM);
        return Collections.unmodifiableList(keys);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static Entry makeEntry(String key, Integer priorValue) {
        if (priorValue == null) {
            return Entry.forAbsentKey(key, ENABLED_VALUE);
        }
        return Entry.forExistingKey(key, ENABLED_VALUE, priorValue);
    }

    private SecureSettingsSpec() {
        // Static utility class; prevent instantiation.
    }
}

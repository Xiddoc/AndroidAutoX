package com.xiddoc.androidautox.autox.ime;

import com.xiddoc.androidautox.autox.provider.SettingsResult;
import com.xiddoc.androidautox.autox.provider.SystemSettingsProvider;

import java.util.List;

/**
 * Thin applier that reads, writes, and reverts the per-display IME + system-decor
 * settings described by an {@link ImeDisplaySettingsSpec}.
 *
 * <h2>Apply / Revert contract</h2>
 * <ol>
 *   <li>{@link #readPriorAndApply(ImeDisplaySettingsSpec)} — reads current Secure-settings
 *       values for both keys, captures them as a new spec (via
 *       {@link ImeDisplaySettingsSpec#withPriorValues}), writes the required
 *       {@link ImeDisplaySettingsSpec#VALUE_ENABLED} value for each, and returns the
 *       updated spec (with priors filled in) so the caller can later pass it to
 *       {@link #revert(ImeDisplaySettingsSpec)}.</li>
 *   <li>{@link #revert(ImeDisplaySettingsSpec)} — restores the prior value for each
 *       entry. If an entry {@link ImeDisplaySettingsSpec.Entry#wasUnset()}, it writes
 *       {@link ImeDisplaySettingsSpec#VALUE_DISABLED} (restoring absence) rather than
 *       an undefined value.</li>
 * </ol>
 *
 * <h2>Error handling</h2>
 * <p>Both methods are best-effort: if a {@link SystemSettingsProvider} write returns
 * non-OK, the result is recorded in {@link ApplyResult} but execution continues (the
 * remaining entries are still attempted). Callers should inspect {@link ApplyResult}
 * to determine whether all writes succeeded.
 *
 * <h2>Testability</h2>
 * <p>This class takes a {@link SystemSettingsProvider} as a constructor argument so
 * tests can supply a fake/spy without any Android framework. No Android imports here.
 */
public final class ImeDisplaySettingsApplier {

    private final SystemSettingsProvider settingsProvider;

    /**
     * @param settingsProvider the provider to use for reading and writing secure settings;
     *                         must not be null
     * @throws IllegalArgumentException if {@code settingsProvider} is null
     */
    public ImeDisplaySettingsApplier(SystemSettingsProvider settingsProvider) {
        if (settingsProvider == null) {
            throw new IllegalArgumentException("settingsProvider must not be null");
        }
        this.settingsProvider = settingsProvider;
    }

    // -------------------------------------------------------------------------
    // Result types
    // -------------------------------------------------------------------------

    /**
     * Outcome of an {@link #readPriorAndApply} or {@link #revert} call.
     * Tracks how many entries succeeded and whether all succeeded.
     */
    public static final class ApplyResult {
        /** Number of entries successfully written. */
        public final int successCount;
        /** Total number of entries that were attempted. */
        public final int totalCount;
        /** {@code true} iff {@link #successCount} == {@link #totalCount}. */
        public final boolean allSucceeded;
        /**
         * The spec returned after applying (with prior values filled in).
         * For {@link #revert} calls this is the same spec that was passed in.
         * Never null.
         */
        public final ImeDisplaySettingsSpec resultSpec;

        ApplyResult(int successCount, int totalCount, ImeDisplaySettingsSpec resultSpec) {
            this.successCount = successCount;
            this.totalCount = totalCount;
            this.allSucceeded = (successCount == totalCount);
            this.resultSpec = resultSpec;
        }

        @Override
        public String toString() {
            return "ApplyResult{success=" + successCount + "/" + totalCount
                    + ", allSucceeded=" + allSucceeded + '}';
        }
    }

    // -------------------------------------------------------------------------
    // Apply
    // -------------------------------------------------------------------------

    /**
     * Reads the current Secure-settings values for both per-display keys, records them
     * as prior values, then writes {@link ImeDisplaySettingsSpec#VALUE_ENABLED} for
     * each entry in apply-list order (system-decors first, then IME).
     *
     * @param spec the spec describing which display and keys to configure; must not be null
     * @return an {@link ApplyResult} containing the count of successful writes and the
     *         updated spec with priors filled in (needed later for
     *         {@link #revert(ImeDisplaySettingsSpec)})
     * @throws IllegalArgumentException if {@code spec} is null
     */
    public ApplyResult readPriorAndApply(ImeDisplaySettingsSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }

        // Step 1: read prior values for the two keys in apply order.
        // Apply list is [decorEntry, imeEntry] (system-decors first).
        List<ImeDisplaySettingsSpec.Entry> applyList = spec.getApplyList();

        int priorDecor = readPrior(applyList.get(0).key);
        int priorIme = readPrior(applyList.get(1).key);

        // Step 2: build a new spec with the recorded priors.
        ImeDisplaySettingsSpec specWithPriors = spec.withPriorValues(priorDecor, priorIme);

        // Step 3: write VALUE_ENABLED for each entry in apply order.
        int successCount = 0;
        for (ImeDisplaySettingsSpec.Entry entry : specWithPriors.getApplyList()) {
            SettingsResult result = settingsProvider.putSecureInt(
                    entry.key, entry.applyValue);
            if (result.isOk()) {
                successCount++;
            }
        }

        return new ApplyResult(successCount, specWithPriors.getApplyList().size(),
                specWithPriors);
    }

    // -------------------------------------------------------------------------
    // Revert
    // -------------------------------------------------------------------------

    /**
     * Restores the prior per-display settings recorded in {@code spec} (as returned by
     * a previous {@link #readPriorAndApply} call).
     *
     * <p>Writes are made in revert-list order (IME entry first, then system-decors). For
     * each entry:
     * <ul>
     *   <li>If {@link ImeDisplaySettingsSpec.Entry#wasUnset()} the entry writes
     *       {@link ImeDisplaySettingsSpec#VALUE_DISABLED} (restores the key to the
     *       disabled/absent state).</li>
     *   <li>Otherwise the entry's {@link ImeDisplaySettingsSpec.Entry#priorValue} is
     *       written back.</li>
     * </ul>
     *
     * @param spec the spec with priors filled in (from {@link #readPriorAndApply}); must not be null
     * @return an {@link ApplyResult} describing the write outcomes
     * @throws IllegalArgumentException if {@code spec} is null
     */
    public ApplyResult revert(ImeDisplaySettingsSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }

        int successCount = 0;
        for (ImeDisplaySettingsSpec.Entry entry : spec.getRevertList()) {
            int revertValue = entry.wasUnset()
                    ? ImeDisplaySettingsSpec.VALUE_DISABLED
                    : entry.priorValue;
            SettingsResult result = settingsProvider.putSecureInt(entry.key, revertValue);
            if (result.isOk()) {
                successCount++;
            }
        }

        return new ApplyResult(successCount, spec.getRevertList().size(), spec);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the current Secure integer value for {@code key}. Returns the raw value if
     * OK, {@link ImeDisplaySettingsSpec#VALUE_DISABLED} (0) if denied, or
     * {@link ImeDisplaySettingsSpec#VALUE_UNSET} if the key was absent.
     */
    private int readPrior(String key) {
        SettingsResult r = settingsProvider.getSecureInt(key);
        if (r.isOk()) {
            return r.value;
        }
        if (r.status == SettingsResult.Status.NOT_FOUND) {
            return ImeDisplaySettingsSpec.VALUE_UNSET;
        }
        // DENIED — treat as disabled (unknown prior; will restore to disabled on revert).
        return ImeDisplaySettingsSpec.VALUE_DISABLED;
    }
}

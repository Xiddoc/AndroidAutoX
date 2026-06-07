package com.xiddoc.androidautox.autox;

import com.xiddoc.androidautox.autox.provider.SettingsResult;
import com.xiddoc.androidautox.autox.provider.SystemSettingsProvider;

import java.util.List;

/**
 * Thin applier that executes the ordered entry lists produced by
 * {@link SecureSettingsSpec} against a {@link SystemSettingsProvider}.
 *
 * <h2>Design rationale</h2>
 * <p>All decision logic (which keys, which values, which revert strategy) lives
 * in the pure {@link SecureSettingsSpec}.  This class <em>only</em> iterates the
 * lists and calls the provider — it contains no conditional logic beyond the
 * early-exit on the first failure.  As a result:
 * <ul>
 *   <li>It has <em>no Android imports</em> — the {@link SystemSettingsProvider}
 *       interface is itself framework-free.
 *   <li>It is fully testable with a fake {@link SystemSettingsProvider} on the
 *       plain JVM (see {@code FreeformSettingsApplierTest}).
 *   <li>It is <strong>not</strong> listed in {@code jacocoExclusions}; it must
 *       reach 100% line + branch coverage.
 * </ul>
 *
 * <h2>Usage (enable path)</h2>
 * <pre>{@code
 * // 1. Snapshot current values via the provider.
 * SettingsResult r1 = provider.getGlobalInt(SecureSettingsSpec.KEY_FORCE_RESIZABLE);
 * SettingsResult r2 = provider.getGlobalInt(SecureSettingsSpec.KEY_ENABLE_FREEFORM);
 * Integer prior1 = r1.isOk() ? r1.value : null;
 * Integer prior2 = r2.isOk() ? r2.value : null;
 *
 * // 2. Build the spec and apply.
 * List<SecureSettingsSpec.Entry> entries = SecureSettingsSpec.applyList(prior1, prior2);
 * ApplyResult result = FreeformSettingsApplier.apply(entries, provider);
 *
 * // 3. Persist prior values for later revert (e.g. in AutoXSettingsStore).
 * }</pre>
 *
 * <h2>Usage (revert path)</h2>
 * <pre>{@code
 * // Recover prior values from storage, rebuild revert list, revert.
 * List<SecureSettingsSpec.Entry> entries = SecureSettingsSpec.revertList(prior1, prior2);
 * FreeformSettingsApplier.apply(entries, provider);
 * }</pre>
 *
 * <h2>Revert-on-disable path</h2>
 * <p>The actual call-site that triggers revert when the user disables AutoX lives
 * in the AutoX enable/disable flow.  Because that flow involves the
 * {@code AutoXEnablementPolicy} and framework glue (excluded Activity / Service),
 * the call-site is left as a documented TODO for the framework-glue layer:
 * <pre>
 *   // TODO(WS3): in AutoXScreen.onAutoXDisabled() / AutoXEnablementPolicy disable path,
 *   //   recover priorForceResizable + priorEnableFreeform from AutoXSettingsStore,
 *   //   build SecureSettingsSpec.revertList(...), and call
 *   //   FreeformSettingsApplier.apply(revertList, provider).
 * </pre>
 */
public final class FreeformSettingsApplier {

    /**
     * Simple result returned by {@link #apply}: either all writes succeeded or
     * one failed (with the failing key recorded).
     */
    public static final class ApplyResult {

        /** {@code true} if every entry was written successfully. */
        public final boolean success;

        /**
         * The {@code Settings.Global} key whose write failed, or {@code null} if
         * {@link #success} is {@code true}.
         */
        public final String failedKey;

        /**
         * The {@link SettingsResult} returned by the provider for the failing key,
         * or {@code null} if {@link #success} is {@code true}.
         */
        public final SettingsResult failedResult;

        private ApplyResult(boolean success, String failedKey, SettingsResult failedResult) {
            this.success = success;
            this.failedKey = failedKey;
            this.failedResult = failedResult;
        }

        /** Constructs a successful result. */
        static ApplyResult ok() {
            return new ApplyResult(true, null, null);
        }

        /** Constructs a failure result carrying the failing key and provider result. */
        static ApplyResult failed(String key, SettingsResult result) {
            return new ApplyResult(false, key, result);
        }

        @Override
        public String toString() {
            if (success) {
                return "ApplyResult{success=true}";
            }
            return "ApplyResult{success=false, failedKey=" + failedKey
                    + ", failedResult=" + failedResult + '}';
        }
    }

    /**
     * Iterates the given entry list, writing each key's {@link SecureSettingsSpec.Entry#enabledValue()}
     * (for an apply pass) or {@link SecureSettingsSpec.Entry#revertValue()} (for a revert
     * pass — the caller must supply the correct list from
     * {@link SecureSettingsSpec#revertList}).
     *
     * <p>The caller decides which pass this is by choosing whether to pass an
     * {@link SecureSettingsSpec#applyList} or a {@link SecureSettingsSpec#revertList};
     * the same {@code apply} method handles both, writing whatever value the entry
     * specifies.  For an apply pass the entry carries {@link SecureSettingsSpec.Entry#enabledValue()};
     * a revert list entry carries the revert value via
     * {@link SecureSettingsSpec.Entry#revertValue()}.
     *
     * <p><strong>Note:</strong> this method writes
     * {@link SecureSettingsSpec.Entry#enabledValue()} from the entry.  For a revert pass
     * you should pass the list returned by {@link SecureSettingsSpec#revertList} and use
     * the overload {@link #revert(List, SystemSettingsProvider)} which writes
     * {@link SecureSettingsSpec.Entry#revertValue()} instead.
     *
     * @param entries  the ordered list of entries to apply (from
     *                 {@link SecureSettingsSpec#applyList})
     * @param provider the settings provider to write through
     * @return {@link ApplyResult#ok()} if all writes succeeded, or a failure result
     *         on the first error; processing stops at the first failure
     * @throws IllegalArgumentException if {@code entries} or {@code provider} is null
     */
    public static ApplyResult apply(List<SecureSettingsSpec.Entry> entries,
                                    SystemSettingsProvider provider) {
        validateArgs(entries, provider);
        for (SecureSettingsSpec.Entry entry : entries) {
            SettingsResult result = provider.putGlobalInt(entry.key(), entry.enabledValue());
            if (!result.isOk()) {
                return ApplyResult.failed(entry.key(), result);
            }
        }
        return ApplyResult.ok();
    }

    /**
     * Iterates the given revert-list, writing each entry's
     * {@link SecureSettingsSpec.Entry#revertValue()} back to the system settings.
     *
     * <p>Pass the list from {@link SecureSettingsSpec#revertList(Integer, Integer)},
     * reconstructed from the prior-value snapshot that was captured before the last
     * {@link #apply} call.
     *
     * @param revertEntries the ordered list of entries to revert (from
     *                      {@link SecureSettingsSpec#revertList})
     * @param provider      the settings provider to write through
     * @return {@link ApplyResult#ok()} if all writes succeeded, or a failure result
     *         on the first error
     * @throws IllegalArgumentException if {@code revertEntries} or {@code provider} is null
     */
    public static ApplyResult revert(List<SecureSettingsSpec.Entry> revertEntries,
                                     SystemSettingsProvider provider) {
        validateArgs(revertEntries, provider);
        for (SecureSettingsSpec.Entry entry : revertEntries) {
            SettingsResult result = provider.putGlobalInt(entry.key(), entry.revertValue());
            if (!result.isOk()) {
                return ApplyResult.failed(entry.key(), result);
            }
        }
        return ApplyResult.ok();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static void validateArgs(List<SecureSettingsSpec.Entry> entries,
                                     SystemSettingsProvider provider) {
        if (entries == null) {
            throw new IllegalArgumentException("entries must not be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
    }

    private FreeformSettingsApplier() {
        // Static utility class; prevent instantiation.
    }
}

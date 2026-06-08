package com.xiddoc.androidautox.autox.provider;

/**
 * Pure decision logic that maps a {@link ProviderCapabilities} snapshot to the provider
 * set AutoX should use, together with a human-readable reason.
 *
 * <p>No Android imports — exhaustively unit testable on the plain JVM. The decision is a
 * binary <b>LSPosed-first single path</b>:
 *
 * <ul>
 *   <li><b>LSPOSED</b> — an LSPosed module hook is active in {@code system_server} AND, once
 *       observable (post-surface), the two projection-critical hooks are honored
 *       (the trusted-display flag survives display creation AND cross-display input injection
 *       reaches the guest). This is the ONLY supported provider path: the trusted-display flag
 *       and cross-display input injection have no stable root-only path, so they go through
 *       LSPosed exclusively.</li>
 *   <li><b>BLOCKED</b> — LSPosed is not active, OR it is active but (post-surface) the
 *       trusted-display flag or input injection is not actually honored. AutoX must NOT silently
 *       degrade to a root-reflection path (it does not exist); it blocks cleanly with a reason
 *       that names what is missing/ineffective.</li>
 * </ul>
 *
 * <h2>Two-phase provisional / reevaluate model</h2>
 * <p>The two honored-flags ({@link ProviderCapabilities#trustedDisplayHonored} /
 * {@link ProviderCapabilities#inputInjectionHonored}) are structurally unobservable before the
 * Car App SDK delivers a surface. During the provisional phase they are conservatively
 * {@code false}; a provisional snapshot with LSPosed active therefore still resolves to
 * {@code LSPOSED} (LSPosed is installed/active, the honored-flags are simply not yet observed),
 * and the call-site enables the trusted-display hook on that provisional decision before the
 * display is created. After the surface exists the caller folds in the real honored-flags via
 * {@link AutoXProviders#reevaluate(boolean, boolean)}: if either hook turns out ineffective the
 * decision flips to {@code BLOCKED} naming that hook.
 *
 * <p>Note: {@code rootAvailable} / {@code platformSignature} no longer affect this decision —
 * trusted-display + input injection are LSPosed-only. Root is still required as a baseline by
 * {@code AutoXEnablementPolicy} (and settings writes stay on root), but it is not a provider
 * alternative here.
 */
public final class ProviderSelectionPolicy {

    /** Which concrete provider set the selection resolves to. */
    public enum Provider {
        /** LSPosed-module-backed providers — the sole supported path. */
        LSPOSED,
        /** No working LSPosed path — AutoX is blocked (no silent degrade). */
        BLOCKED
    }

    /**
     * Immutable result of a selection: the chosen {@link Provider} and the reason it was
     * chosen (surfaced in logs / diagnostics UI).
     */
    public static final class Decision {
        /** The selected provider set. Never null. */
        public final Provider provider;
        /** Human-readable explanation of the decision. Never null. */
        public final String reason;

        Decision(Provider provider, String reason) {
            this.provider = provider;
            this.reason = reason;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Decision)) return false;
            Decision d = (Decision) o;
            return provider == d.provider && reason.equals(d.reason);
        }

        @Override
        public int hashCode() {
            return 31 * provider.hashCode() + reason.hashCode();
        }

        @Override
        public String toString() {
            return "Decision{provider=" + provider + ", reason='" + reason + "'}";
        }
    }

    private ProviderSelectionPolicy() {
    }

    /**
     * Selects the provider set for the given capability snapshot, following the LSPosed-first
     * single path:
     *
     * <ol>
     *   <li>LSPosed NOT active → {@link Provider#BLOCKED}, reason names "requires LSPosed".</li>
     *   <li>LSPosed active but the trusted-display flag is not honored (post-surface) →
     *       {@link Provider#BLOCKED}, reason names the ineffective trusted-display hook.</li>
     *   <li>LSPosed active but input injection is not honored (post-surface) →
     *       {@link Provider#BLOCKED}, reason names the ineffective injection hook.</li>
     *   <li>LSPosed active and both honored (when observable) → {@link Provider#LSPOSED}.</li>
     * </ol>
     *
     * <p>During the provisional (pre-surface) phase the two honored-flags are {@code false} but
     * are NOT yet meaningful, so the call-site treats a provisional LSPosed-active snapshot as
     * {@code LSPOSED}; the {@code BLOCKED}-on-ineffective-hook branches only matter once
     * {@link AutoXProviders#reevaluate(boolean, boolean)} has folded in the observed values.
     *
     * @param caps detected capabilities; must not be null
     * @return the {@link Decision} (provider + reason)
     * @throws IllegalArgumentException if {@code caps} is null
     */
    public static Decision select(ProviderCapabilities caps) {
        if (caps == null) {
            throw new IllegalArgumentException("caps must not be null");
        }

        if (!caps.lsposedModuleActive) {
            return new Decision(Provider.BLOCKED,
                    "AutoX requires LSPosed: the LSPosed module is not active in system_server. "
                            + "Install/enable the AndroidAutoX LSPosed module and reboot.");
        }

        if (!caps.trustedDisplayHonored) {
            return new Decision(Provider.BLOCKED,
                    "LSPosed active but the trusted-display hook is not honored "
                            + "(VIRTUAL_DISPLAY_FLAG_TRUSTED did not survive); projection would be "
                            + "untrusted/unusable.");
        }

        if (!caps.inputInjectionHonored) {
            return new Decision(Provider.BLOCKED,
                    "LSPosed active and trusted display honored, but the input-injection hook is "
                            + "not honored; gestures would not reach the guest.");
        }

        return new Decision(Provider.LSPOSED,
                "LSPosed module active in system_server; trusted display and input injection are "
                        + "honored — using hook-backed providers (settings stay on root).");
    }
}

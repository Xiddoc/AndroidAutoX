package com.xiddoc.androidautox.autox.provider;

/**
 * Pure decision logic that maps a {@link ProviderCapabilities} snapshot to the provider
 * set AutoX should use, together with a human-readable reason.
 *
 * <p>No Android imports — exhaustively unit testable on the plain JVM. The decision is
 * deliberately simple and total:
 *
 * <ol>
 *   <li><b>LSPOSED</b> — an LSPosed module hook is active in {@code system_server}. This
 *       is preferred because it relaxes the trusted-display, input-injection and
 *       settings checks at the source, working even on devices where root-reflection is
 *       silently dropped.</li>
 *   <li><b>ROOT_REFLECTION</b> — no LSPosed, but a privileged path exists (root, or the
 *       platform signature) <em>and</em> the core projection capabilities
 *       (trusted display AND input injection) are actually honored. Best-effort
 *       reflection drives the privileged APIs directly.</li>
 *   <li><b>DEGRADED</b> — neither of the above holds: either there is no privileged path
 *       at all, or there is one but the kernel/framework silently drops the trusted-flag
 *       or cross-display input injection, so projection cannot work reliably.</li>
 * </ol>
 */
public final class ProviderSelectionPolicy {

    /** Which concrete provider set the selection resolves to. */
    public enum Provider {
        /** LSPosed-module-backed providers (preferred). */
        LSPOSED,
        /** Root / signature reflection-backed providers. */
        ROOT_REFLECTION,
        /** No working privileged path — projection is degraded / unavailable. */
        DEGRADED
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
     * Selects the provider set for the given capability snapshot.
     *
     * @param caps detected capabilities; must not be null
     * @return the {@link Decision} (provider + reason)
     * @throws IllegalArgumentException if {@code caps} is null
     */
    public static Decision select(ProviderCapabilities caps) {
        if (caps == null) {
            throw new IllegalArgumentException("caps must not be null");
        }

        if (caps.lsposedModuleActive) {
            return new Decision(Provider.LSPOSED,
                    "LSPosed module active in system_server; using hook-backed providers "
                            + "(relaxes trusted-display, input-injection and settings checks).");
        }

        boolean hasPrivilegedPath = caps.rootAvailable || caps.platformSignature;
        if (!hasPrivilegedPath) {
            return new Decision(Provider.DEGRADED,
                    "No privileged path: LSPosed inactive, no root, not platform-signed.");
        }

        if (!caps.trustedDisplayHonored) {
            return new Decision(Provider.DEGRADED,
                    "Privileged path present but VIRTUAL_DISPLAY_FLAG_TRUSTED is not honored; "
                            + "projection would be untrusted/unusable.");
        }

        if (!caps.inputInjectionHonored) {
            return new Decision(Provider.DEGRADED,
                    "Privileged path present and trusted display honored, but cross-display "
                            + "input injection is silently dropped; gestures would not reach the guest.");
        }

        String how = caps.rootAvailable ? "root reflection" : "platform-signature reflection";
        return new Decision(Provider.ROOT_REFLECTION,
                "No LSPosed; using " + how + " — trusted display and input injection are honored.");
    }
}

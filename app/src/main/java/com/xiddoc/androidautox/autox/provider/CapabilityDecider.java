package com.xiddoc.androidautox.autox.provider;

/**
 * Pure logic that turns raw probe inputs (gathered by the excluded
 * {@code ReflectiveCapabilityProbe} glue) into a {@link ProviderCapabilities} snapshot.
 *
 * <p>Separating this from the reflection keeps the decision logic 100% unit testable: the
 * glue only collects booleans from the live system; this class decides what they mean.
 *
 * <p>No Android imports.
 */
public final class CapabilityDecider {

    private CapabilityDecider() {
    }

    /**
     * Builds a {@link ProviderCapabilities} from individually probed signals.
     *
     * <p>Each parameter is an already-resolved observation from the glue:
     *
     * @param lsposedSelfHookFired   {@code true} if the module's self-probe hook fired,
     *                               proving the module is loaded in {@code system_server}
     * @param platformSignatureMatch {@code true} if the app's signing cert equals the
     *                               platform cert
     * @param rootShellAvailable     {@code true} if a root shell could be obtained
     * @param createdDisplayTrusted  {@code true} if a test virtual display came back with
     *                               the trusted flag actually set
     * @param injectionAccepted      {@code true} if a probe {@code injectInputEvent} to a
     *                               secondary display returned accepted
     * @param settingsWriteSucceeded {@code true} if a probe protected-settings write
     *                               round-tripped
     * @return the assembled, immutable capability snapshot
     */
    public static ProviderCapabilities decide(boolean lsposedSelfHookFired,
                                              boolean platformSignatureMatch,
                                              boolean rootShellAvailable,
                                              boolean createdDisplayTrusted,
                                              boolean injectionAccepted,
                                              boolean settingsWriteSucceeded) {
        return ProviderCapabilities.builder()
                .lsposedModuleActive(lsposedSelfHookFired)
                .platformSignature(platformSignatureMatch)
                .rootAvailable(rootShellAvailable)
                .trustedDisplayHonored(createdDisplayTrusted)
                .inputInjectionHonored(injectionAccepted)
                .secureSettingsWritable(settingsWriteSucceeded)
                .build();
    }

    /**
     * Convenience predicate: is any privileged write/inject path believed usable?
     * Used by the glue to decide whether it is even worth running the heavier probes.
     *
     * @param caps a capability snapshot
     * @return {@code true} if LSPosed is active, or a root/signature path exists
     */
    public static boolean hasAnyPrivilegedPath(ProviderCapabilities caps) {
        if (caps == null) {
            throw new IllegalArgumentException("caps must not be null");
        }
        return caps.lsposedModuleActive || caps.rootAvailable || caps.platformSignature;
    }
}

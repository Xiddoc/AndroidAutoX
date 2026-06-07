package com.xiddoc.androidautox.autox.provider.lsposed;

import com.xiddoc.androidautox.autox.provider.TrustedFlagPolicy;

/**
 * EXCLUDED GLUE: locates the {@code flags} argument in the live
 * {@code createVirtualDisplay} call frame and forces the trusted flag using the pure
 * {@link TrustedFlagPolicy}. Cannot be unit-tested off-device (the argument layout depends
 * on the concrete {@code system_server} method signature); listed in {@code jacocoExclusions}.
 *
 * <h2>Scoping to AutoX's display (P1)</h2>
 * <p>The trusted flag is only forced when the {@link HookGatePolicy} confirms the hook
 * should act — i.e. the matching IPC command is enabled AND the display <b>name</b> in the
 * frame equals AutoX's own display name. This stops the hook from making <em>every</em>
 * virtual display trusted system-wide. The name match is the only AutoX-identifying signal
 * available this early in {@code createVirtualDisplay} (the display id does not exist yet).
 */
final class TrustedFlagBridge {

    private TrustedFlagBridge() {
    }

    /**
     * Scans the hooked call's arguments for the display-name string and the integer flags
     * value; if {@link HookGatePolicy#shouldActForDisplayName} approves, ORs the trusted flag
     * into the flags argument. Best-effort: if no plausible flags/name argument is found, or
     * the gate rejects, leaves the frame unchanged. The bit math is delegated to the pure
     * {@link TrustedFlagPolicy#withTrusted} and the act/no-act decision to the pure
     * {@link HookGatePolicy}.
     *
     * @param args                 the hooked method's argument array (may be null)
     * @param ipcEnabled           whether the ENABLE_TRUSTED_DISPLAY IPC command is enabled
     * @param expectedDisplayName  AutoX's own virtual-display name
     *                             ({@code VirtualDisplayConfig.DISPLAY_NAME})
     */
    static void forceTrustedFlag(Object[] args, boolean ipcEnabled,
                                 String expectedDisplayName) {
        if (args == null) {
            return;
        }
        // The createVirtualDisplay frame carries a String display name; use it to confirm
        // this call belongs to AutoX before touching any flags. If the gate rejects, do
        // nothing (fail closed — no system-wide trusted-display escalation).
        String hookedName = firstString(args);
        if (!HookGatePolicy.shouldActForDisplayName(ipcEnabled, hookedName, expectedDisplayName)) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Integer) {
                int flags = (Integer) args[i];
                if (!TrustedFlagPolicy.isTrusted(flags)) {
                    args[i] = TrustedFlagPolicy.withTrusted(flags);
                }
                return;
            }
        }
    }

    /** Returns the first {@link String} argument, or {@code null} if none. */
    private static String firstString(Object[] args) {
        for (Object a : args) {
            if (a instanceof String) {
                return (String) a;
            }
        }
        return null;
    }
}

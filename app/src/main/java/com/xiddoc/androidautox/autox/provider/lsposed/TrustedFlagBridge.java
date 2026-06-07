package com.xiddoc.androidautox.autox.provider.lsposed;

import com.xiddoc.androidautox.autox.provider.TrustedFlagPolicy;

/**
 * EXCLUDED GLUE: locates the {@code flags} argument in the live
 * {@code createVirtualDisplay} call frame and forces the trusted flag using the pure
 * {@link TrustedFlagPolicy}. Cannot be unit-tested off-device (the argument layout depends
 * on the concrete {@code system_server} method signature); listed in {@code jacocoExclusions}.
 */
final class TrustedFlagBridge {

    private TrustedFlagBridge() {
    }

    /**
     * Scans the hooked call's arguments for the integer flags value and ORs the trusted
     * flag into it. Best-effort: if no plausible flags argument is found, leaves the frame
     * unchanged. The bit math is delegated to the pure {@link TrustedFlagPolicy#withTrusted}.
     *
     * @param args the hooked method's argument array (may be null)
     */
    static void forceTrustedFlag(Object[] args) {
        if (args == null) {
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
}

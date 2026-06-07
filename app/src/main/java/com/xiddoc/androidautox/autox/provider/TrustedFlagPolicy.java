package com.xiddoc.androidautox.autox.provider;

import com.xiddoc.androidautox.autox.VirtualDisplayConfig;

/**
 * Pure flag math for the trusted-display hook: given the {@code flags} integer passed to
 * {@code createVirtualDisplay}, returns the value with {@code VIRTUAL_DISPLAY_FLAG_TRUSTED}
 * forced on.
 *
 * <p>No Android imports — fully unit testable. The excluded {@code TrustedFlagBridge} glue
 * locates the flags argument in the live call frame and delegates the actual bit math here,
 * so the only thing that can't be tested off-device is the argument-array plumbing, not the
 * decision.
 */
public final class TrustedFlagPolicy {

    private TrustedFlagPolicy() {
    }

    /**
     * @param flags the original virtual-display flags
     * @return {@code flags | FLAG_TRUSTED}
     */
    public static int withTrusted(int flags) {
        return flags | VirtualDisplayConfig.FLAG_TRUSTED;
    }

    /**
     * @param flags the original virtual-display flags
     * @return {@code true} iff {@link VirtualDisplayConfig#FLAG_TRUSTED} is already set
     */
    public static boolean isTrusted(int flags) {
        return (flags & VirtualDisplayConfig.FLAG_TRUSTED) != 0;
    }
}

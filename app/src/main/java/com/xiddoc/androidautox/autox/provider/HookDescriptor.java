package com.xiddoc.androidautox.autox.provider;

/**
 * Immutable descriptor of a single class+method the LSPosed module hooks in
 * {@code system_server}. Pure data (no Android imports) so the whole hook-target table
 * is unit testable; the actual {@code XposedHelpers.findAndHookMethod} call lives in the
 * excluded Xposed glue and reads its targets from {@link HookTargetTable}.
 */
public final class HookDescriptor {

    /** Logical role of the hook, independent of SDK-specific class/method names. */
    public enum Target {
        /** {@code DisplayManagerService} trusted-flag / ADD_TRUSTED_DISPLAY check. */
        DISPLAY_TRUSTED_FLAG,
        /** {@code InputManagerService#injectInputEvent} target-display check. */
        INPUT_INJECT_DISPLAY,
        /** {@code DisplayWindowSettings#shouldShowIme} per-display IME gate. */
        DISPLAY_SHOULD_SHOW_IME,
        /** {@code DisplayWindowSettings#shouldShowSystemDecors} per-display decor gate. */
        DISPLAY_SHOULD_SHOW_SYSTEM_DECORS,
        /** {@code ActivityTaskManagerService} launch-on-display permission check. */
        LAUNCH_ON_DISPLAY
    }

    /**
     * Sentinel for an argument-position field that has <b>not</b> been verified against the
     * concrete {@code system_server} method signature on a real device. A descriptor carrying
     * {@code UNKNOWN_ARG_INDEX} tells the (excluded) bridge glue it must NOT touch that argument
     * — the safe degradation is a no-op, never a wrong-position mutation. See the
     * {@code TODO(device-verify)} markers in {@code InputInjectionBridge}.
     */
    public static final int UNKNOWN_ARG_INDEX = -1;

    /** The logical role this descriptor fills. Never null. */
    public final Target target;
    /** Fully-qualified class name to hook, e.g. {@code com.android.server.display.DisplayManagerService}. */
    public final String className;
    /** Method name to hook. */
    public final String methodName;
    /**
     * Zero-based index of the injection-{@code mode} {@code int} argument in the hooked method's
     * argument array, or {@link #UNKNOWN_ARG_INDEX} if not yet device-verified for this SDK.
     *
     * <p>Pins the {@code mode} position per-SDK so the bridge can normalise <em>only</em> that
     * argument (rather than blindly writing the last {@code int}, which on a trailing-displayId
     * signature would clobber the routing target — see MUST-FIX 1). Defaults to
     * {@link #UNKNOWN_ARG_INDEX} for every current entry because the exact
     * {@code injectInputEvent} overload has not been confirmed on-device; while it is unknown the
     * bridge skips mode-relaxation entirely (a wrong guess would be a wrong-display redirect, a
     * NO-OP is safe).
     */
    public final int modeArgIndex;
    /**
     * Zero-based index of the target-{@code displayId} {@code int} argument in the hooked
     * method's argument array, or {@link #UNKNOWN_ARG_INDEX} if not yet device-verified.
     *
     * <p>Lets the bridge read/stamp the display id by a pinned position instead of guessing the
     * "first int" (which in an {@code ActivityTaskManagerService} launch frame is typically a
     * uid/pid/flags, not a displayId — see MUST-FIX 3). Defaults to {@link #UNKNOWN_ARG_INDEX}.
     */
    public final int displayIdArgIndex;

    /**
     * @param target     logical role; must not be null
     * @param className  FQCN to hook; must not be null/blank
     * @param methodName method name to hook; must not be null/blank
     * @throws IllegalArgumentException on any null/blank argument
     */
    public HookDescriptor(Target target, String className, String methodName) {
        this(target, className, methodName, UNKNOWN_ARG_INDEX, UNKNOWN_ARG_INDEX);
    }

    /**
     * Full constructor that also pins the per-SDK argument positions.
     *
     * @param target            logical role; must not be null
     * @param className         FQCN to hook; must not be null/blank
     * @param methodName        method name to hook; must not be null/blank
     * @param modeArgIndex      zero-based index of the injection-mode int arg, or
     *                          {@link #UNKNOWN_ARG_INDEX} ({@code -1}) if unverified
     * @param displayIdArgIndex zero-based index of the target-displayId int arg, or
     *                          {@link #UNKNOWN_ARG_INDEX} ({@code -1}) if unverified
     * @throws IllegalArgumentException on any null/blank string arg or an arg index {@code < -1}
     */
    public HookDescriptor(Target target, String className, String methodName,
                          int modeArgIndex, int displayIdArgIndex) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("className must not be null/blank");
        }
        if (methodName == null || methodName.trim().isEmpty()) {
            throw new IllegalArgumentException("methodName must not be null/blank");
        }
        if (modeArgIndex < UNKNOWN_ARG_INDEX) {
            throw new IllegalArgumentException("modeArgIndex must be >= -1: " + modeArgIndex);
        }
        if (displayIdArgIndex < UNKNOWN_ARG_INDEX) {
            throw new IllegalArgumentException(
                    "displayIdArgIndex must be >= -1: " + displayIdArgIndex);
        }
        this.target = target;
        this.className = className;
        this.methodName = methodName;
        this.modeArgIndex = modeArgIndex;
        this.displayIdArgIndex = displayIdArgIndex;
    }

    /** @return {@code true} iff a verified {@link #modeArgIndex} is pinned for this descriptor. */
    public boolean hasModeArgIndex() {
        return modeArgIndex != UNKNOWN_ARG_INDEX;
    }

    /** @return {@code true} iff a verified {@link #displayIdArgIndex} is pinned for this descriptor. */
    public boolean hasDisplayIdArgIndex() {
        return displayIdArgIndex != UNKNOWN_ARG_INDEX;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HookDescriptor)) return false;
        HookDescriptor d = (HookDescriptor) o;
        return target == d.target
                && className.equals(d.className)
                && methodName.equals(d.methodName)
                && modeArgIndex == d.modeArgIndex
                && displayIdArgIndex == d.displayIdArgIndex;
    }

    @Override
    public int hashCode() {
        int h = target.hashCode();
        h = 31 * h + className.hashCode();
        h = 31 * h + methodName.hashCode();
        h = 31 * h + modeArgIndex;
        h = 31 * h + displayIdArgIndex;
        return h;
    }

    @Override
    public String toString() {
        return "HookDescriptor{" + target + " -> " + className + "#" + methodName
                + " modeArgIndex=" + modeArgIndex
                + " displayIdArgIndex=" + displayIdArgIndex + '}';
    }
}

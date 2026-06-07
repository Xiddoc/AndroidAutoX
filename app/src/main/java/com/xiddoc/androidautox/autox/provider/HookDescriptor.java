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

    /** The logical role this descriptor fills. Never null. */
    public final Target target;
    /** Fully-qualified class name to hook, e.g. {@code com.android.server.display.DisplayManagerService}. */
    public final String className;
    /** Method name to hook. */
    public final String methodName;

    /**
     * @param target     logical role; must not be null
     * @param className  FQCN to hook; must not be null/blank
     * @param methodName method name to hook; must not be null/blank
     * @throws IllegalArgumentException on any null/blank argument
     */
    public HookDescriptor(Target target, String className, String methodName) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("className must not be null/blank");
        }
        if (methodName == null || methodName.trim().isEmpty()) {
            throw new IllegalArgumentException("methodName must not be null/blank");
        }
        this.target = target;
        this.className = className;
        this.methodName = methodName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HookDescriptor)) return false;
        HookDescriptor d = (HookDescriptor) o;
        return target == d.target
                && className.equals(d.className)
                && methodName.equals(d.methodName);
    }

    @Override
    public int hashCode() {
        int h = target.hashCode();
        h = 31 * h + className.hashCode();
        h = 31 * h + methodName.hashCode();
        return h;
    }

    @Override
    public String toString() {
        return "HookDescriptor{" + target + " -> " + className + "#" + methodName + '}';
    }
}

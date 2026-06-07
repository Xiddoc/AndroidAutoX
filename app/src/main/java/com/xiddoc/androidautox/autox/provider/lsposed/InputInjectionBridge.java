package com.xiddoc.androidautox.autox.provider.lsposed;

import de.robv.android.xposed.XC_MethodHook;

/**
 * EXCLUDED GLUE: bridges the input-injection hook to the live {@code InputManagerService}
 * call frame. Isolated so {@link AutoXXposedModule} stays focused on wiring. Cannot be
 * unit-tested off-device (needs a real {@code system_server} call frame); listed in
 * {@code jacocoExclusions}.
 *
 * <h2>HONEST STATUS: this is scaffolding, not a working bypass</h2>
 * <p>{@link #allow(XC_MethodHook.MethodHookParam)} is currently a <b>no-op placeholder</b>.
 * It does not rewrite the in-flight {@code injectInputEvent} permission check, so on a real
 * device the call still proceeds with whatever permission the caller already holds (i.e. the
 * LSPosed input-injection path is NOT yet functional). The version-specific argument rewrite
 * against the concrete {@code InputManagerService} signature must be implemented and validated
 * on-device. The caller ({@link AutoXXposedModule}) already gates this via the pure
 * {@link HookGatePolicy} so that, once implemented, the bypass will only ever apply to AutoX's
 * own display id.
 *
 * <p>// TODO(device-verify): implement the per-display permission-check rewrite against the
 * real IMS {@code injectInputEvent} signature on API 31–34, then drop the placeholder note.
 */
final class InputInjectionBridge {

    private InputInjectionBridge() {
    }

    /**
     * Placeholder for relaxing the per-display permission check for the in-flight
     * {@code injectInputEvent} call. <b>Currently a no-op</b> (see class Javadoc): it leaves
     * the frame untouched so the call proceeds with the caller's existing permission. The
     * caller has already confirmed (via {@link HookGatePolicy}) that this frame targets
     * AutoX's display before invoking this method.
     *
     * @param param the in-flight hooked-method frame
     */
    static void allow(XC_MethodHook.MethodHookParam param) {
        // No-op placeholder. The version-specific argument rewrite is applied on-device and
        // is NOT yet implemented — see class Javadoc / TODO(device-verify).
        if (param == null) {
            return;
        }
        Object[] args = param.args;
        if (args == null) {
            return;
        }
        // Intentionally minimal: the version-specific argument rewrite is applied on-device.
    }
}

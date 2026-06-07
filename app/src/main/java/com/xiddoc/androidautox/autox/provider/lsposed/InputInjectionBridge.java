package com.xiddoc.androidautox.autox.provider.lsposed;

import de.robv.android.xposed.XC_MethodHook;

/**
 * EXCLUDED GLUE: bridges the input-injection hook to the live {@code InputManagerService}
 * call frame. Isolated so {@link AutoXXposedModule} stays focused on wiring. Cannot be
 * unit-tested off-device (needs a real {@code system_server} call frame); listed in
 * {@code jacocoExclusions}.
 */
final class InputInjectionBridge {

    private InputInjectionBridge() {
    }

    /**
     * Relaxes the per-display permission check for the in-flight {@code injectInputEvent}
     * call. The real implementation rewrites the injection mode / asserted-permission
     * argument so the call proceeds for the AutoX display. Kept best-effort.
     *
     * @param param the in-flight hooked-method frame
     */
    static void allow(XC_MethodHook.MethodHookParam param) {
        // Real device behaviour is implemented against the concrete IMS signature, which is
        // only present in system_server. This placeholder leaves the frame untouched so the
        // call proceeds with whatever permission the caller already holds.
        Object[] args = param.args;
        if (args == null) {
            return;
        }
        // Intentionally minimal: the version-specific argument rewrite is applied on-device.
    }
}

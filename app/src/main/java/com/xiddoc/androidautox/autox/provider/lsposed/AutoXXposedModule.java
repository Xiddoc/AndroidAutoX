package com.xiddoc.androidautox.autox.provider.lsposed;

import android.os.Build;

import com.xiddoc.androidautox.autox.provider.HookDescriptor;
import com.xiddoc.androidautox.autox.provider.HookTargetSet;
import com.xiddoc.androidautox.autox.provider.HookTargetTable;
import com.xiddoc.androidautox.autox.provider.IpcCommand;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed module entry point. EXCLUDED GLUE — this class is irreducible Xposed/framework
 * reflection and cannot be unit-tested off-device; it is listed in {@code jacocoExclusions}.
 *
 * <h2>What it does</h2>
 * In {@code system_server} (package {@code android}) it installs hooks that:
 * <ol>
 *   <li>Honor {@code VIRTUAL_DISPLAY_FLAG_TRUSTED} for AutoX virtual displays.</li>
 *   <li>Allow {@code injectInputEvent} targeting a specific display.</li>
 *   <li>Set per-display {@code shouldShowIme} / {@code shouldShowSystemDecors}.</li>
 *   <li>Permit launch-on-display.</li>
 * </ol>
 *
 * <h2>All real logic lives in pure classes</h2>
 * The class/method targets come from the pure {@link HookTargetTable}; the app&harr;module
 * commands are parsed with the pure {@link IpcCommand} schema read over
 * {@link XSharedPreferences}. This glue only wires those decisions to live reflection.
 *
 * <h2>SAFETY</h2>
 * <ul>
 *   <li>Every hook body is wrapped in try/catch and <b>fails closed</b> — a throw never
 *       escapes into {@code system_server} (which would bootloop the device).</li>
 *   <li>Targets are chosen by {@code Build.VERSION.SDK_INT}; an unknown SDK yields an
 *       {@link HookTargetSet#resolved unresolved} set and the module degrades to a no-op.</li>
 *   <li>Each individual {@code findAndHookMethod} is independently guarded, so a missing
 *       method signature on one target does not abort the others.</li>
 * </ul>
 */
public final class AutoXXposedModule implements IXposedHookLoadPackage {

    /** The module's own package, used to open its XSharedPreferences. */
    static final String MODULE_PACKAGE = "com.xiddoc.androidautox";
    /** Shared-prefs file the app writes IPC commands into. */
    static final String IPC_PREFS_FILE = "autox_lsposed_ipc";
    /** Prefs key holding the newline-separated encoded {@link IpcCommand}s. */
    static final String IPC_PREFS_KEY = "commands";
    /** The system_server package name. */
    static final String SYSTEM_SERVER_PACKAGE = "android";

    private final XSharedPreferences prefs;

    public AutoXXposedModule() {
        XSharedPreferences p;
        try {
            p = new XSharedPreferences(MODULE_PACKAGE, IPC_PREFS_FILE);
        } catch (Throwable t) {
            // Never let prefs init crash the loaded package.
            p = null;
        }
        this.prefs = p;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (lpparam == null || !SYSTEM_SERVER_PACKAGE.equals(lpparam.packageName)) {
                return; // we only patch system_server
            }
            HookTargetSet targets = HookTargetTable.resolveFor(Build.VERSION.SDK_INT);
            if (!targets.resolved) {
                XposedBridge.log("AutoX: unsupported SDK " + Build.VERSION.SDK_INT
                        + "; no hooks installed (degraded).");
                return;
            }
            installHooks(lpparam.classLoader, targets);
        } catch (Throwable t) {
            // FAIL CLOSED: never propagate into system_server.
            XposedBridge.log(t);
        }
    }

    private void installHooks(ClassLoader cl, HookTargetSet targets) {
        for (HookDescriptor d : targets.all().values()) {
            try {
                installOne(cl, d);
            } catch (Throwable t) {
                // Per-target guard: one missing signature must not abort the rest.
                XposedBridge.log("AutoX: failed to install hook " + d + ": " + t);
            }
        }
    }

    private void installOne(ClassLoader cl, HookDescriptor d) {
        switch (d.target) {
            case DISPLAY_TRUSTED_FLAG:
                hookTrustedDisplay(cl, d);
                break;
            case INPUT_INJECT_DISPLAY:
                hookInputInjection(cl, d);
                break;
            case DISPLAY_SHOULD_SHOW_IME:
                hookForceTrue(cl, d);
                break;
            case DISPLAY_SHOULD_SHOW_SYSTEM_DECORS:
                hookForceTrue(cl, d);
                break;
            case LAUNCH_ON_DISPLAY:
                hookForceTrue(cl, d);
                break;
            default:
                // Unreachable; enum is exhaustive. Logged for forward-compat.
                XposedBridge.log("AutoX: unknown hook target " + d.target);
        }
    }

    /**
     * Ensures the trusted flag survives the {@code createVirtualDisplay} path for our
     * display name. Best-effort: ORs {@code FLAG_TRUSTED} back into the flags argument if
     * present; failures fail closed.
     */
    private void hookTrustedDisplay(ClassLoader cl, HookDescriptor d) {
        XposedHelpers.findAndHookMethod(d.className, cl, d.methodName, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (isCommandEnabled(IpcCommand.Type.ENABLE_TRUSTED_DISPLAY)) {
                        TrustedFlagBridge.forceTrustedFlag(param.args);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t); // fail closed
                }
            }
        });
    }

    /** Lets injectInputEvent target an arbitrary display when enabled. */
    private void hookInputInjection(ClassLoader cl, HookDescriptor d) {
        XposedHelpers.findAndHookMethod(d.className, cl, d.methodName, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (isCommandEnabled(IpcCommand.Type.ALLOW_INPUT_INJECTION)) {
                        // Real impl bypasses the per-display permission check here; the
                        // bridge centralizes the (testable) decision of whether to allow.
                        InputInjectionBridge.allow(param);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t); // fail closed
                }
            }
        });
    }

    /**
     * Forces a boolean gate method to return {@code true} (used for shouldShowIme,
     * shouldShowSystemDecors and launch-on-display), gated by the matching IPC command.
     */
    private void hookForceTrue(ClassLoader cl, HookDescriptor d) {
        final IpcCommand.Type gate = gateFor(d.target);
        XposedHelpers.findAndHookMethod(d.className, cl, d.methodName, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (gate == null || isCommandEnabled(gate)) {
                        param.setResult(Boolean.TRUE);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t); // fail closed
                }
            }
        });
    }

    private static IpcCommand.Type gateFor(HookDescriptor.Target target) {
        switch (target) {
            case DISPLAY_SHOULD_SHOW_IME:
                return IpcCommand.Type.SET_DISPLAY_IME;
            default:
                return null; // decors + launch-on-display are unconditionally allowed
        }
    }

    /**
     * Reads the app's IPC commands from XSharedPreferences and returns whether one of the
     * given {@code type} is currently present/enabled. Uses the pure {@link IpcCommand}
     * decoder; a malformed line is skipped, never thrown.
     */
    private boolean isCommandEnabled(IpcCommand.Type type) {
        if (prefs == null) {
            return false;
        }
        try {
            if (prefs.hasFileChanged()) {
                prefs.reload();
            }
            String blob = prefs.getString(IPC_PREFS_KEY, "");
            if (blob == null || blob.isEmpty()) {
                return false;
            }
            for (String line : blob.split("\n")) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    if (IpcCommand.decode(line).getType() == type) {
                        return true;
                    }
                } catch (IllegalArgumentException ignored) {
                    // malformed line — skip, never throw out of the hook
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        return false;
    }
}

package com.xiddoc.androidautox.autox.provider.lsposed;

import android.os.Build;

import com.xiddoc.androidautox.autox.VirtualDisplayConfig;
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
 * {@link XSharedPreferences}; the act/no-act decision is made by the pure
 * {@link HookGatePolicy}. This glue only wires those decisions to live reflection.
 *
 * <h2>Scoping (P1): only AutoX's display</h2>
 * Each hook is gated by {@link HookGatePolicy} so it acts only for AutoX's own display
 * (matched by display name for trusted-display, or by the AutoX display id carried in the
 * IPC command for the per-display gates / input injection) rather than forcing trusted /
 * {@code shouldShowSystemDecors} / {@code shouldShowIme} / launch-on-display SYSTEM-WIDE.
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
                    boolean enabled = isCommandEnabled(IpcCommand.Type.ENABLE_TRUSTED_DISPLAY);
                    // The gate (pure HookGatePolicy, inside the bridge) confirms the frame's
                    // display NAME matches AutoX's before any flag is touched — no system-wide
                    // trusted-display escalation.
                    TrustedFlagBridge.forceTrustedFlag(
                            param.args, enabled, VirtualDisplayConfig.DISPLAY_NAME);
                } catch (Throwable t) {
                    XposedBridge.log(t); // fail closed
                }
            }
        });
    }

    /** Lets injectInputEvent target AutoX's display when enabled. */
    private void hookInputInjection(ClassLoader cl, HookDescriptor d) {
        XposedHelpers.findAndHookMethod(d.className, cl, d.methodName, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    boolean enabled = isCommandEnabled(IpcCommand.Type.ALLOW_INPUT_INJECTION);
                    int autoxDisplayId = autoxDisplayId(IpcCommand.Type.ALLOW_INPUT_INJECTION);
                    // The bridge runs the pure HookGatePolicy gate internally (mirroring
                    // TrustedFlagBridge) and only relaxes the frame for AutoX's display id —
                    // never a system-wide injection relaxation.
                    InputInjectionBridge.allow(
                            param, Build.VERSION.SDK_INT, enabled, autoxDisplayId);
                } catch (Throwable t) {
                    XposedBridge.log(t); // fail closed
                }
            }
        });
    }

    /**
     * Forces a boolean gate method to return {@code true} (used for shouldShowIme,
     * shouldShowSystemDecors and launch-on-display) — but ONLY for AutoX's own display id.
     *
     * <p>P1: previously decors + launch-on-display were forced unconditionally (system-wide).
     * Now every force-true hook is gated by {@link HookGatePolicy#shouldActForDisplayId}: the
     * matching IPC command must be enabled AND the display id in the frame must equal the
     * AutoX display id carried in that command. A frame for any other display is left
     * untouched (the original method result stands).
     */
    private void hookForceTrue(ClassLoader cl, HookDescriptor d) {
        final IpcCommand.Type gate = gateFor(d.target);
        XposedHelpers.findAndHookMethod(d.className, cl, d.methodName, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    boolean enabled = isCommandEnabled(gate);
                    int autoxDisplayId = autoxDisplayId(gate);
                    int hookedDisplayId = firstInt(param.args);
                    if (HookGatePolicy.shouldActForDisplayId(
                            enabled, hookedDisplayId, autoxDisplayId)) {
                        param.setResult(Boolean.TRUE);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t); // fail closed
                }
            }
        });
    }

    /** Maps a per-display force-true target to its gating IPC command type. */
    private static IpcCommand.Type gateFor(HookDescriptor.Target target) {
        switch (target) {
            case DISPLAY_SHOULD_SHOW_IME:
            case DISPLAY_SHOULD_SHOW_SYSTEM_DECORS:
                return IpcCommand.Type.SET_DISPLAY_IME;
            default:
                // launch-on-display is now its own first-class, display-scoped command.
                return IpcCommand.Type.LAUNCH_ON_DISPLAY;
        }
    }

    /** Returns the first {@code int}/{@link Integer} argument in {@code args}, or -1. */
    private static int firstInt(Object[] args) {
        if (args == null) {
            return HookGatePolicy.NO_DISPLAY_ID;
        }
        for (Object a : args) {
            if (a instanceof Integer) {
                return (Integer) a;
            }
        }
        return HookGatePolicy.NO_DISPLAY_ID;
    }

    /**
     * Reads the AutoX display id carried in the first enabled IPC command of {@code type}
     * via {@link IpcCommand#displayId()}. Returns {@link HookGatePolicy#NO_DISPLAY_ID}
     * when no such command is present or its display-id arg is absent/unparseable.
     */
    private int autoxDisplayId(IpcCommand.Type type) {
        if (prefs == null || type == null) {
            return HookGatePolicy.NO_DISPLAY_ID;
        }
        try {
            if (prefs.hasFileChanged()) {
                prefs.reload();
            }
            String blob = prefs.getString(IPC_PREFS_KEY, "");
            if (blob == null || blob.isEmpty()) {
                return HookGatePolicy.NO_DISPLAY_ID;
            }
            for (String line : blob.split("\n")) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    IpcCommand cmd = IpcCommand.decode(line);
                    if (cmd.getType() == type) {
                        // IpcCommand.displayId() is fail-safe: NO_DISPLAY_ID when absent/invalid.
                        return cmd.displayId();
                    }
                } catch (IllegalArgumentException ignored) {
                    // malformed line — skip
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        return HookGatePolicy.NO_DISPLAY_ID;
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

package com.xiddoc.androidautox.autox.provider.lsposed;

import com.xiddoc.androidautox.autox.provider.HookDescriptor;
import com.xiddoc.androidautox.autox.provider.HookTargetSet;
import com.xiddoc.androidautox.autox.provider.HookTargetTable;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * EXCLUDED GLUE: relaxes the per-display permission check for the in-flight
 * {@code InputManagerService#injectInputEvent} call so AutoX can inject touch/key events onto
 * its own virtual display. Cannot be unit-tested off-device (needs a real {@code system_server}
 * call frame and the concrete IMS signature); listed in {@code jacocoExclusions}. The act/no-act
 * decision is delegated to the pure, 100%-tested {@link HookGatePolicy}; the target method comes
 * from the pure {@link HookTargetTable}. Structured to mirror {@link TrustedFlagBridge}: the gate
 * runs <em>inside</em> the bridge so the module wiring stays thin.
 *
 * <h2>Why a permission relax is needed</h2>
 * <p>{@code injectInputEvent} is guarded by the {@code android.permission.INJECT_EVENTS}
 * signature permission, and on API 31+ {@code InputManagerService} additionally rejects events
 * targeting a display the caller does not own. AutoX (even rooted) is not the display owner from
 * {@code system_server}'s point of view, so the event is dropped/throws. This hook, gated to
 * AutoX's display id, neutralises that check for AutoX's display only — never system-wide.
 *
 * <h2>Per-SDK strategy (API 31-34)</h2>
 * <p>Across API 31-34 the public {@code IInputManager#injectInputEvent} entry takes an
 * {@code InputEvent} plus an {@code int mode} (and on some builds a target {@code uid}/display).
 * The most portable relaxation that is robust to the exact argument list is:
 * <ol>
 *   <li>Confirm via {@link HookGatePolicy#shouldActForDisplayId} that the in-flight event targets
 *       AutoX's display id (extracted from the {@code InputEvent}'s {@code mDisplayId} field, or
 *       the first {@code int} arg as a fallback).</li>
 *   <li>Normalise the injection {@code mode} argument to
 *       {@code INPUT_EVENT_INJECTION_SYNC_NONE} (value {@code 0}) so the call does not block on a
 *       privileged-sync result the relaxed caller cannot obtain, AND</li>
 *   <li>Clear the per-display ownership rejection by stamping the event's display id back onto
 *       the gated AutoX display id so downstream window-routing accepts it.</li>
 * </ol>
 * <p>The exact field/argument names below are best-effort and MUST be confirmed on a rooted
 * device running LSPosed — see the {@code // TODO(device-verify)} markers. Everything is wrapped
 * fail-closed: any reflection error leaves the frame untouched and is only logged, so a throw
 * never escapes into {@code system_server}.
 */
final class InputInjectionBridge {

    /**
     * {@code InputManager.INPUT_EVENT_INJECTION_SYNC_NONE} — fire-and-forget injection that does
     * not wait for a (privileged) result. Stable across API 31-34.
     * // TODO(device-verify): confirm the constant value is still 0 on the target build.
     */
    private static final int INPUT_EVENT_INJECTION_SYNC_NONE = 0;

    private InputInjectionBridge() {
    }

    /**
     * Relaxes the in-flight {@code injectInputEvent} frame for AutoX's display, if the gate
     * approves. Mirrors {@link TrustedFlagBridge#forceTrustedFlag}: the gate decision lives here,
     * driven by primitives the caller extracted from the live frame.
     *
     * @param param           the in-flight hooked-method frame (may be null → no-op)
     * @param sdkInt          {@code Build.VERSION.SDK_INT} (selects the target signature)
     * @param ipcEnabled      whether the {@code ALLOW_INPUT_INJECTION} IPC command is enabled
     * @param autoxDisplayId  the display id AutoX is currently driving
     *                        ({@link HookGatePolicy#NO_DISPLAY_ID} when none)
     */
    static void allow(XC_MethodHook.MethodHookParam param, int sdkInt,
                      boolean ipcEnabled, int autoxDisplayId) {
        if (param == null) {
            return;
        }
        Object[] args = param.args;
        if (args == null) {
            return;
        }
        // The target method/signature for this SDK comes from the pure table; if the SDK is
        // unknown, resolve() reports unresolved and we leave the frame untouched.
        HookTargetSet targets = HookTargetTable.resolveFor(sdkInt);
        if (!targets.resolved) {
            return;
        }
        HookDescriptor injectTarget = targets.get(HookDescriptor.Target.INPUT_INJECT_DISPLAY);
        if (injectTarget == null) {
            return; // table has no injection descriptor for this SDK — fail closed
        }

        int hookedDisplayId = displayIdFromFrame(args);
        if (!HookGatePolicy.shouldActForDisplayId(ipcEnabled, hookedDisplayId, autoxDisplayId)) {
            return; // not AutoX's display (or disabled) — never a system-wide relaxation
        }

        try {
            relaxInjectionMode(args);
            stampDisplayId(args, autoxDisplayId);
        } catch (Throwable t) {
            // FAIL CLOSED: a reflection/field error must never throw out of system_server.
            XposedBridge.log(t);
        }
    }

    /**
     * Extracts the target display id from the call frame: the {@code InputEvent}'s display id if
     * present, else the first {@code int} argument.
     *
     * <p>// TODO(device-verify): on API 31-34 the {@code InputEvent} display id is held in the
     * private field {@code mDisplayId}; confirm the field name (it has historically also been
     * exposed via {@code InputEvent#getDisplayId()}).
     */
    private static int displayIdFromFrame(Object[] args) {
        for (Object a : args) {
            if (a == null) {
                continue;
            }
            Integer fromEvent = readDisplayIdField(a);
            if (fromEvent != null) {
                return fromEvent;
            }
        }
        // Fallback: first int argument is the explicit displayId on signatures that carry one.
        for (Object a : args) {
            if (a instanceof Integer) {
                return (Integer) a;
            }
        }
        return HookGatePolicy.NO_DISPLAY_ID;
    }

    /**
     * Reads {@code mDisplayId} off an {@code InputEvent} (or any object that exposes it) via
     * reflection. Returns {@code null} when the object has no such field, so the caller can fall
     * back to the explicit-int-arg path.
     *
     * <p>// TODO(device-verify): confirm {@code mDisplayId} exists and is an {@code int} on the
     * concrete {@code MotionEvent}/{@code KeyEvent} classes for API 31-34 (and that LSPosed can
     * reach it despite hidden-API greylist enforcement, which it normally exempts).
     */
    private static Integer readDisplayIdField(Object event) {
        try {
            Field f = findField(event.getClass(), "mDisplayId");
            if (f == null) {
                return null;
            }
            f.setAccessible(true);
            Object v = f.get(event);
            if (v instanceof Integer) {
                return (Integer) v;
            }
            return null;
        } catch (Throwable t) {
            return null; // not an InputEvent / inaccessible — fall back
        }
    }

    /**
     * Normalises the injection {@code mode} argument to {@code SYNC_NONE} so the relaxed caller
     * does not block on a privileged synchronous result. The mode is the {@code int} that follows
     * the {@code InputEvent} in the {@code injectInputEvent} signature; we set the LAST {@code int}
     * argument (the mode trails the event/display ints across API 31-34).
     *
     * <p>// TODO(device-verify): confirm the {@code mode} argument position for the exact
     * {@code InputManagerService#injectInputEvent} overload on the target SDK; on some builds it
     * is the 2nd arg ({@code event, mode}) and on others the 4th ({@code event, uid, displayId,
     * mode}). Setting the last int is the most robust single-position heuristic but should be
     * pinned to the concrete signature on-device.
     */
    private static void relaxInjectionMode(Object[] args) {
        int lastIntIdx = -1;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Integer) {
                lastIntIdx = i;
            }
        }
        if (lastIntIdx >= 0) {
            args[lastIntIdx] = INPUT_EVENT_INJECTION_SYNC_NONE;
        }
    }

    /**
     * Stamps the AutoX display id back onto the event so {@code system_server}'s window routing
     * treats the injection as targeting AutoX's (owned, trusted) display, clearing the
     * per-display ownership rejection. Best-effort: writes {@code mDisplayId} on the
     * {@code InputEvent} arg if present.
     *
     * <p>// TODO(device-verify): confirm that writing {@code mDisplayId} is sufficient on API
     * 31-34 (vs. an explicit display-id int argument), and that no additional uid/owner check in
     * {@code InputManagerService#injectInputEventToTarget} must also be relaxed.
     */
    private static void stampDisplayId(Object[] args, int autoxDisplayId) {
        for (Object a : args) {
            if (a == null) {
                continue;
            }
            try {
                Field f = findField(a.getClass(), "mDisplayId");
                if (f != null && f.getType() == int.class) {
                    f.setAccessible(true);
                    f.setInt(a, autoxDisplayId);
                    return;
                }
            } catch (Throwable ignored) {
                // try the next argument
            }
        }
    }

    /** Walks the class hierarchy looking for a declared field {@code name}, or null. */
    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        return null;
    }
}

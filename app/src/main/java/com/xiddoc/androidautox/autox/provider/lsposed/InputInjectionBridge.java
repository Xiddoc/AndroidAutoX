package com.xiddoc.androidautox.autox.provider.lsposed;

import com.xiddoc.androidautox.autox.provider.HookDescriptor;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * EXCLUDED GLUE: relaxes the per-display permission check for the in-flight
 * {@code InputManagerService#injectInputEvent} call so AutoX can inject touch/key events onto
 * its own virtual display. Cannot be unit-tested off-device (needs a real {@code system_server}
 * call frame and the concrete IMS signature); listed in {@code jacocoExclusions}. The act/no-act
 * decision is delegated to the pure, 100%-tested {@link HookGatePolicy}; the per-SDK argument
 * positions come from the pure {@link HookDescriptor}. Structured to mirror
 * {@link TrustedFlagBridge}: the gate runs <em>inside</em> the bridge so the module wiring stays
 * thin, and the bridge does NOT re-resolve the hook target (the module only installs this hook
 * when the target is already resolved, so a re-resolution here would be dead code).
 *
 * <h2>!!! HONEST STATUS — UNVERIFIED ON-DEVICE !!!</h2>
 * <p><b>None of the reflection/argument assumptions in this class have been confirmed on a real
 * rooted device running LSPosed.</b> In particular, ALL of the following are best-effort
 * <em>guesses</em>, NOT confirmed-functional behaviour:
 * <ul>
 *   <li>that the {@code InputEvent} display id lives in a field literally named
 *       {@code mDisplayId} (and is reachable past hidden-API greylist enforcement);</li>
 *   <li>the argument <em>position</em> of the injection {@code mode} int in the concrete
 *       {@code injectInputEvent} overload (so by default we do NOT touch it — see
 *       {@link #relaxInjectionMode});</li>
 *   <li>that stamping {@code mDisplayId} back onto the event is sufficient to clear the
 *       {@code InputManagerService} per-display <em>ownership</em> check (there may be a separate
 *       uid/owner gate in {@code injectInputEventToTarget} that this does not touch).</li>
 * </ul>
 * <p>Until each is verified against the real signature (see the {@code TODO(device-verify)}
 * markers), this bridge should be treated as a SCAFFOLD that fails closed: every code path that
 * is not positively confirmed degrades to a no-op rather than performing a speculative — and
 * possibly harmful — mutation. Do not present this as a working injection bypass.
 *
 * <h2>Why a permission relax is needed</h2>
 * <p>{@code injectInputEvent} is guarded by the {@code android.permission.INJECT_EVENTS}
 * signature permission, and on API 31+ {@code InputManagerService} additionally rejects events
 * targeting a display the caller does not own. AutoX (even rooted) is not the display owner from
 * {@code system_server}'s point of view, so the event is dropped/throws. This hook, gated to
 * AutoX's display id, is intended to neutralise that check for AutoX's display only — never
 * system-wide.
 *
 * <h2>Design note — SYNC_NONE changes injection SEMANTICS, not just permission</h2>
 * <p>Forcing the mode to {@code INPUT_EVENT_INJECTION_SYNC_NONE} (value {@code 0}) makes the call
 * fire-and-forget: the caller no longer blocks on, nor can observe, a synchronous injection
 * result (handled / would-block / failed). That is a deliberate <em>semantic</em> change, not
 * merely a permission relaxation — a caller that relied on the sync result would behave
 * differently. It is therefore gated, off by default (the mode arg position is unverified), and
 * applied only once the position is pinned in {@link HookDescriptor#modeArgIndex}.
 */
final class InputInjectionBridge {

    /**
     * {@code InputManager.INPUT_EVENT_INJECTION_SYNC_NONE} — fire-and-forget injection that does
     * not wait for a (privileged) result.
     * // TODO(device-verify): confirm the constant value is still 0 on the target build.
     */
    private static final int INPUT_EVENT_INJECTION_SYNC_NONE = 0;

    /** Name of the (unverified) {@code InputEvent} display-id field. */
    private static final String DISPLAY_ID_FIELD = "mDisplayId";

    /**
     * Cache of resolved {@code mDisplayId} {@link Field}s keyed by the declaring {@link Class}.
     * {@code injectInputEvent} is a hot path in {@code system_server}, so walking the class chain
     * on every event would be wasteful; we resolve once per class and reuse. A class that has no
     * such field is cached as {@link #NO_FIELD} so the negative result is not re-walked either.
     * {@link ConcurrentHashMap} is thread-safe and the resolution is idempotent (a benign race
     * just recomputes the same {@link Field}). Fail-closed: any reflection error caches/returns
     * {@code null}.
     */
    private static final ConcurrentHashMap<Class<?>, Field> FIELD_CACHE =
            new ConcurrentHashMap<Class<?>, Field>();

    /** Sentinel cached for "this class has no mDisplayId field" (ConcurrentHashMap forbids null). */
    private static final Field NO_FIELD;

    static {
        Field marker;
        try {
            // A guaranteed-present field on a stable JDK class, used only as a not-null sentinel.
            marker = InputInjectionBridge.class.getDeclaredField("INPUT_EVENT_INJECTION_SYNC_NONE");
        } catch (NoSuchFieldException e) {
            marker = null; // unreachable; the field above exists
        }
        NO_FIELD = marker;
    }

    private InputInjectionBridge() {
    }

    /**
     * Relaxes the in-flight {@code injectInputEvent} frame for AutoX's display, if the gate
     * approves. Mirrors {@link TrustedFlagBridge#forceTrustedFlag}: the gate decision lives here,
     * driven by primitives the caller extracted from the live frame, and the hook target is NOT
     * re-resolved (the module only installs the hook for an already-resolved descriptor).
     *
     * @param param           the in-flight hooked-method frame (may be null → no-op)
     * @param target          the resolved {@link HookDescriptor} for this SDK (carries the pinned
     *                        per-SDK argument positions; may be null → no-op)
     * @param ipcEnabled      whether the {@code ALLOW_INPUT_INJECTION} IPC command is enabled
     * @param autoxDisplayId  the display id AutoX is currently driving
     *                        ({@link HookGatePolicy#NO_DISPLAY_ID} when none)
     */
    static void allow(XC_MethodHook.MethodHookParam param, HookDescriptor target,
                      boolean ipcEnabled, int autoxDisplayId) {
        if (param == null || target == null) {
            return;
        }
        Object[] args = param.args;
        if (args == null) {
            return;
        }

        int hookedDisplayId = displayIdFromFrame(args, target);
        if (!HookGatePolicy.shouldActForDisplayId(ipcEnabled, hookedDisplayId, autoxDisplayId)) {
            return; // not AutoX's display (or disabled) — never a system-wide relaxation
        }

        try {
            relaxInjectionMode(args, target);
            stampDisplayId(args, autoxDisplayId);
        } catch (Throwable t) {
            // FAIL CLOSED: a reflection/field error must never throw out of system_server.
            XposedBridge.log(t);
        }
    }

    /**
     * Extracts the target display id from the call frame. Prefers the per-SDK pinned
     * {@link HookDescriptor#displayIdArgIndex} (a verified explicit-int position); otherwise the
     * {@code InputEvent}'s display-id field; otherwise, as a last resort, the first {@code int}
     * argument.
     *
     * <p>NOTE on the interaction with {@link #relaxInjectionMode}: the last-int fallback here and
     * the mode mutation deliberately do NOT share an argument. Mode relaxation is skipped unless a
     * <em>distinct</em> {@link HookDescriptor#modeArgIndex} is pinned, so the fallback display id
     * (which may itself be a trailing int) can never be zeroed by mode normalisation.
     *
     * <p>// TODO(device-verify): on API 31-34 the {@code InputEvent} display id is held in the
     * private field {@code mDisplayId}; confirm the field name (it has historically also been
     * exposed via {@code InputEvent#getDisplayId()}). Prefer pinning
     * {@link HookDescriptor#displayIdArgIndex} once the real signature is known.
     */
    private static int displayIdFromFrame(Object[] args, HookDescriptor target) {
        if (target.hasDisplayIdArgIndex()
                && target.displayIdArgIndex < args.length
                && args[target.displayIdArgIndex] instanceof Integer) {
            return (Integer) args[target.displayIdArgIndex];
        }
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
            Field f = cachedField(event.getClass());
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
     * Normalises the injection {@code mode} argument to {@code SYNC_NONE} — but ONLY when the
     * concrete {@code mode} position has been device-verified and pinned in
     * {@link HookDescriptor#modeArgIndex}.
     *
     * <p>MUST-FIX 1: the previous heuristic ("set the LAST int arg") is unsafe. For an
     * {@code injectInputEvent} overload that carries the target {@code displayId} as a trailing
     * {@code int} (no separate mode arg), zeroing the last int would overwrite the display id
     * with {@code 0} and redirect AutoX's injected events to display 0. Because the exact overload
     * is not yet device-verified, we therefore <b>skip mode-relaxation entirely</b> while
     * {@code modeArgIndex} is {@link HookDescriptor#UNKNOWN_ARG_INDEX} — a NO-OP is always safe,
     * a wrong-position write is not. Once the position is pinned per-SDK, only that exact argument
     * is touched.
     *
     * <p>// TODO(device-verify): determine the {@code mode} argument index for the concrete
     * {@code InputManagerService#injectInputEvent} overload on each supported SDK and pin it in
     * {@code HookTargetTable} via {@link HookDescriptor#modeArgIndex}. Until then this is a no-op.
     */
    private static void relaxInjectionMode(Object[] args, HookDescriptor target) {
        if (!target.hasModeArgIndex()) {
            // Unverified mode position — do nothing (never guess; a wrong guess can clobber the
            // displayId arg and redirect events to display 0). See MUST-FIX 1 / HONEST STATUS.
            return;
        }
        int idx = target.modeArgIndex;
        if (idx < args.length && args[idx] instanceof Integer) {
            args[idx] = INPUT_EVENT_INJECTION_SYNC_NONE;
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
     * {@code InputManagerService#injectInputEventToTarget} must also be relaxed. This is one of
     * the UNVERIFIED guesses called out in the HONEST STATUS banner.
     */
    private static void stampDisplayId(Object[] args, int autoxDisplayId) {
        for (Object a : args) {
            if (a == null) {
                continue;
            }
            try {
                Field f = cachedField(a.getClass());
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

    /**
     * Returns the cached {@code mDisplayId} {@link Field} for {@code cls}, resolving (and caching)
     * it on first use. Returns {@code null} when the class chain has no such field. Caches the
     * negative result via {@link #NO_FIELD} so the chain is walked at most once per class.
     */
    private static Field cachedField(Class<?> cls) {
        Field cached = FIELD_CACHE.get(cls);
        if (cached != null) {
            return cached == NO_FIELD ? null : cached;
        }
        Field resolved = findField(cls, DISPLAY_ID_FIELD);
        FIELD_CACHE.put(cls, resolved == null ? NO_FIELD : resolved);
        return resolved;
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

package com.xiddoc.androidautox.autox.provider.lsposed;

import android.hardware.input.InputManager;
import android.os.Build;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.xiddoc.androidautox.autox.AutoXLog;
import com.xiddoc.androidautox.autox.GestureSpec;
import com.xiddoc.androidautox.autox.provider.InputProvider;

import java.lang.reflect.Method;

/**
 * EXCLUDED GLUE: the LSPosed-backed {@link InputProvider} for AutoX cross-display gesture
 * injection — the ONLY supported AutoX injection path.
 *
 * <h2>LSPosed-first single path</h2>
 * <p>AutoX's cross-display input injection has no stable root-only path: even a rooted app is
 * not the display owner from {@code system_server}'s point of view, so
 * {@code InputManagerService} rejects events targeting AutoX's virtual display. The
 * {@link InputInjectionBridge} LSPosed hook (running inside {@code system_server}, gated to
 * AutoX's display) relaxes that per-display ownership check. This class is the app-side half:
 * it builds the {@link MotionEvent}s and issues the {@code InputManager#injectInputEvent} call;
 * the LSPosed hook makes the call reach AutoX's display. It is only ever wired in when the
 * provider selection resolves to {@link com.xiddoc.androidautox.autox.provider.ProviderSelectionPolicy.Provider#LSPOSED};
 * when LSPosed is inactive AutoX is BLOCKED and no injector is created (no silent root fallback).
 *
 * <h2>Why reflection?</h2>
 * <p>{@code InputManager#injectInputEvent(InputEvent, int)} is a {@code @hide} method absent
 * from the public SDK surface; it is the only Android-native path to inject onto an arbitrary
 * display. No {@code Runtime.exec} / shell strings are used.
 *
 * <h2>Untestable seam</h2>
 * <p>All gesture-parameter math lives in the framework-free
 * {@link com.xiddoc.androidautox.autox.GestureSpec} /
 * {@link com.xiddoc.androidautox.autox.CoordinateTranslator}. This class needs a real
 * {@link InputManager} and the platform input subsystem, so it is excluded from the JaCoCo
 * coverage gate. Tests provide a stub {@link InputProvider}.
 */
public final class LsposedInputInjector implements InputProvider {

    /** Last observed result of a real injection; drives {@link #isInjectionHonored()}. */
    private volatile boolean lastInjectionAccepted;

    /**
     * {@code InputManager.INJECT_INPUT_EVENT_MODE_ASYNC} — async (fire-and-forget) injection,
     * sufficient for AutoX gestures. Value {@code 0}.
     */
    private static final int INJECT_MODE_ASYNC = 0;

    /** Cached reference to the hidden {@code injectInputEvent} method. */
    private final Method injectInputEvent;

    /**
     * The object the {@link #injectInputEvent} method is invoked on. On API &lt; 34 this is the
     * {@link InputManager} passed to the constructor; on API 34+ it is the hidden
     * {@code android.hardware.input.InputManagerGlobal} singleton.
     */
    private final Object injectTarget;

    /**
     * Constructs the injector, resolving the hidden {@code injectInputEvent} method via
     * reflection. On API 33 and below the method lives on {@link InputManager}; on API 34 the
     * platform moved it to {@code InputManagerGlobal}. If resolution fails on every path,
     * {@link #inject} fails closed (returns {@code false}).
     *
     * <p>// TODO(device-verify): confirm the exact {@code InputManagerGlobal} class name,
     * {@code getInstance()} signature, and that {@code injectInputEvent(InputEvent, int)} is
     * still the honored entry point on a real API-34 device.
     *
     * @param inputManager the system {@link InputManager}; obtain via
     *                     {@code context.getSystemService(InputManager.class)}
     */
    public LsposedInputInjector(InputManager inputManager) {
        Object target = null;
        Method resolved = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+: injectInputEvent lives on InputManagerGlobal, not InputManager.
                Class<?> globalCls = Class.forName(
                        "android.hardware.input.InputManagerGlobal");
                Method getInstance = globalCls.getDeclaredMethod("getInstance");
                getInstance.setAccessible(true);
                target = getInstance.invoke(null);
                resolved = globalCls.getDeclaredMethod(
                        "injectInputEvent", android.view.InputEvent.class, int.class);
                resolved.setAccessible(true);
            } else {
                target = inputManager;
                resolved = InputManager.class.getDeclaredMethod(
                        "injectInputEvent", android.view.InputEvent.class, int.class);
                resolved.setAccessible(true);
            }
        } catch (Throwable t) {
            AutoXLog.w("Inject", "LsposedInputInjector: injectInputEvent could not be resolved on "
                    + "this platform (SDK " + Build.VERSION.SDK_INT + "); gesture injection "
                    + "will be a no-op.", t);
            target = null;
            resolved = null;
        }
        // Fail-closed: only keep the method if we also have a non-null target to invoke on.
        if (resolved != null && target != null) {
            this.injectTarget = target;
            this.injectInputEvent = resolved;
            AutoXLog.i("Inject", "injectInputEvent resolved on SDK " + Build.VERSION.SDK_INT
                    + " via " + target.getClass().getName());
        } else {
            this.injectTarget = null;
            this.injectInputEvent = null;
            AutoXLog.w("Inject", "injectInputEvent UNRESOLVED on SDK " + Build.VERSION.SDK_INT
                    + " — every gesture will be a no-op (isInjectionHonored=false)");
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds one or more {@link MotionEvent}s from {@code spec} and dispatches them via the
     * hidden {@code InputManager#injectInputEvent}. A TAP produces DOWN+UP; a SWIPE produces
     * DOWN, interpolated MOVEs, and UP. Each event is tagged with the virtual display's id so
     * the LSPosed-relaxed input subsystem routes them to the correct window on that display.
     */
    @Override
    public boolean inject(GestureSpec spec) {
        if (injectInputEvent == null) {
            AutoXLog.w("Inject", "inject: injectInputEvent unavailable; dropping gesture " + spec);
            return false;
        }
        try {
            boolean accepted = dispatchGesture(spec);
            lastInjectionAccepted = accepted;
            // The accepted flag is the single most diagnostic bit for the injection path: false
            // here on a real device means injectInputEvent ran but system_server dropped the event
            // (the LSPosed per-display ownership relax did not take effect for this displayId).
            AutoXLog.d("Inject", "dispatched " + spec.getKind() + " on display "
                    + spec.getDisplayId() + " -> accepted=" + accepted);
            return accepted;
        } catch (SecurityException e) {
            AutoXLog.w("Inject", "inject: INJECT_EVENTS permission denied; dropping gesture " + spec, e);
            lastInjectionAccepted = false;
            return false;
        } catch (Exception e) {
            AutoXLog.w("Inject", "inject: unexpected error dispatching gesture " + spec, e);
            lastInjectionAccepted = false;
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reports {@code true} only after a real {@link #inject} call was accepted. Until the
     * first successful injection (or if reflection was never resolved) this returns
     * {@code false}, so the selection layer treats the LSPosed injection hook as unproven and
     * BLOCKS rather than assuming success.
     */
    @Override
    public boolean isInjectionHonored() {
        return injectInputEvent != null && lastInjectionAccepted;
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private boolean dispatchGesture(GestureSpec spec) throws Exception {
        long now = SystemClock.uptimeMillis();
        boolean allAccepted = true;

        if (spec.getKind() == GestureSpec.Kind.TAP) {
            allAccepted &= injectMotionEvent(
                    buildMotionEvent(MotionEvent.ACTION_DOWN, now, now,
                            spec.getX1(), spec.getY1(), spec.getDisplayId()));
            allAccepted &= injectMotionEvent(
                    buildMotionEvent(MotionEvent.ACTION_UP, now, now + 10L,
                            spec.getX1(), spec.getY1(), spec.getDisplayId()));
        } else {
            // SWIPE: interpolate across the gesture duration with ~16ms steps (≈60fps).
            long durationMs = spec.getDurationMs();
            long stepMs = 16L;
            long steps = Math.max(1L, durationMs / stepMs);
            long downTime = now;

            allAccepted &= injectMotionEvent(
                    buildMotionEvent(MotionEvent.ACTION_DOWN, downTime, downTime,
                            spec.getX1(), spec.getY1(), spec.getDisplayId()));

            for (long i = 1; i <= steps; i++) {
                float fraction = (float) i / (float) steps;
                float x = spec.getX1() + fraction * (spec.getX2() - spec.getX1());
                float y = spec.getY1() + fraction * (spec.getY2() - spec.getY1());
                long eventTime = downTime + (i * stepMs);
                allAccepted &= injectMotionEvent(
                        buildMotionEvent(MotionEvent.ACTION_MOVE, downTime, eventTime,
                                x, y, spec.getDisplayId()));
            }

            allAccepted &= injectMotionEvent(
                    buildMotionEvent(MotionEvent.ACTION_UP, downTime, downTime + durationMs,
                            spec.getX2(), spec.getY2(), spec.getDisplayId()));
        }
        return allAccepted;
    }

    /**
     * Builds a single-pointer {@link MotionEvent} for {@code action}, tagged with the target
     * virtual {@code displayId} via the 16-arg {@link MotionEvent#obtain} (API 34+).
     */
    private static MotionEvent buildMotionEvent(int action, long downTime, long eventTime,
                                                float x, float y, int displayId) {
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_FINGER;

        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = x;
        pc.y = y;
        pc.pressure = 1.0f;
        pc.size = 1.0f;

        return MotionEvent.obtain(
                downTime, eventTime, action,
                1,
                new MotionEvent.PointerProperties[]{pp},
                new MotionEvent.PointerCoords[]{pc},
                0, 0, 1.0f, 1.0f,
                0, 0,
                InputDevice.SOURCE_TOUCHSCREEN,
                displayId,
                0, 0);
    }

    private boolean injectMotionEvent(MotionEvent event) throws Exception {
        try {
            Object result = injectInputEvent.invoke(injectTarget, event, INJECT_MODE_ASYNC);
            return Boolean.TRUE.equals(result);
        } finally {
            event.recycle();
        }
    }
}

package com.xiddoc.androidautox.autox;

import android.hardware.input.InputManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.xiddoc.androidautox.autox.provider.InputProvider;

import java.lang.reflect.Method;

/**
 * {@link GestureInjector} implementation that uses the platform
 * {@code InputManager#injectInputEvent(InputEvent, int)} via reflection to dispatch
 * gestures onto a virtual display.
 *
 * <h2>Why reflection?</h2>
 * <p>{@code InputManager#injectInputEvent} is a {@code @hide} method absent from the
 * public SDK surface. It is the only Android-native path to inject touch events onto
 * an arbitrary display without routing through the {@code UiAutomation} API (which
 * requires an instrumentation context). No {@code Runtime.exec} / shell strings are
 * used here.
 *
 * <h2>Privilege requirement</h2>
 * <p>The method is guarded by {@code android.permission.INJECT_EVENTS} (signature
 * level). On a rooted device this can be acquired by running the app with elevated
 * privileges via libsu, but the injection call itself is a pure Java native-API call —
 * no explicit {@code su} shell is spawned. On a non-rooted device the call will be
 * silently dropped by the framework and {@link #inject} will return {@code false}.
 *
 * <h2>Untestable seam</h2>
 * <p>This class is intentionally kept as thin as possible — all gesture-parameter
 * logic lives in the framework-free {@link GestureSpec} and {@link CoordinateTranslator}.
 * The class is excluded from the JaCoCo coverage gate because it cannot be exercised
 * off-device (it needs a real {@link InputManager} and the platform's input subsystem).
 *
 * <p><b>Use {@link GestureInjector} as the injection type in all non-framework code.</b>
 * Swap this implementation for a stub in tests.
 *
 * <h2>WS4 provider seam</h2>
 * <p>This class now also implements the WS4 {@link InputProvider} seam. {@link #inject}
 * satisfies both interfaces unchanged; {@link #isInjectionHonored()} reports the last
 * observed success of a real injection so the provider-selection layer can detect when
 * {@code injectInputEvent} is silently dropped (root reflection on a hardened device) and
 * fall back / degrade instead of assuming success.
 */
public final class ReflectiveGestureInjector implements GestureInjector, InputProvider {

    private static final String TAG = "AndroidAutoX";

    /** Last observed result of a real injection; drives {@link #isInjectionHonored()}. */
    private volatile boolean lastInjectionAccepted;

    /**
     * {@code InputManagerCompat.INJECT_INPUT_EVENT_MODE_ASYNC} — the mode constant
     * accepted by {@code InputManager#injectInputEvent}. Value {@code 0} corresponds
     * to async (fire-and-forget) injection, which is sufficient for AutoX gestures.
     */
    private static final int INJECT_MODE_ASYNC = 0;

    /** Cached reference to the hidden {@code injectInputEvent} method. */
    private final Method injectInputEvent;

    /**
     * The object the {@link #injectInputEvent} method is invoked on. On API &lt; 34 this is
     * the {@link InputManager} passed to the constructor; on API 34+ it is the
     * {@code android.hardware.input.InputManagerGlobal} singleton (see class Javadoc).
     */
    private final Object injectTarget;

    /**
     * Constructs an injector, resolving the hidden {@code injectInputEvent} method
     * via reflection.
     *
     * <h3>API-34 relocation</h3>
     * <p>On API 33 and below the method lives on {@link InputManager} itself
     * ({@code InputManager#injectInputEvent(InputEvent, int)}). On API 34 the platform
     * moved the implementation to the hidden singleton
     * {@code android.hardware.input.InputManagerGlobal}; the {@link InputManager} instance
     * method was removed, so reflecting it against {@link InputManager} throws
     * {@link NoSuchMethodException}. We therefore choose the target by
     * {@link Build.VERSION#SDK_INT}: on API 34+ we reflect
     * {@code InputManagerGlobal#getInstance()} and resolve {@code injectInputEvent} on that
     * instance; below 34 we resolve it on {@link InputManager}.
     *
     * <p>If resolution fails on every path, subsequent calls to {@link #inject}
     * immediately return {@code false} with a warning logged (fail-closed — no events are
     * dispatched and the provider reports the privilege as unproven).
     *
     * <p>// TODO(device-verify): confirm the exact {@code InputManagerGlobal} class name,
     * {@code getInstance()} signature, and that {@code injectInputEvent(InputEvent, int)}
     * is still the honored entry point on a real API-34 device; the relocation is
     * documented but the precise reflective shape can only be validated on-device.
     *
     * @param inputManager the system {@link InputManager}; obtain via
     *                     {@code context.getSystemService(InputManager.class)}
     */
    public ReflectiveGestureInjector(InputManager inputManager) {
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
            Log.w(TAG, "ReflectiveGestureInjector: injectInputEvent could not be resolved on "
                    + "this platform (SDK " + Build.VERSION.SDK_INT + "); gesture injection "
                    + "will be a no-op.", t);
            target = null;
            resolved = null;
        }
        // Fail-closed: only keep the method if we also have a non-null target to invoke on.
        if (resolved != null && target != null) {
            this.injectTarget = target;
            this.injectInputEvent = resolved;
        } else {
            this.injectTarget = null;
            this.injectInputEvent = null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds one or more {@link MotionEvent} objects from the {@link GestureSpec}
     * and dispatches them via the hidden {@code InputManager#injectInputEvent} method.
     *
     * <ul>
     *   <li>A {@link GestureSpec.Kind#TAP} produces a DOWN event followed immediately
     *       by an UP event at the same coordinates.</li>
     *   <li>A {@link GestureSpec.Kind#SWIPE} produces a DOWN event, a series of MOVE
     *       events interpolated across the gesture duration, and a final UP event.</li>
     * </ul>
     *
     * <p>All events are tagged with the virtual display's id via
     * {@link MotionEvent#setDisplayId(int)} so the input subsystem routes them to
     * the correct window on that display.
     *
     * <p>Returns {@code false} and logs a warning if:
     * <ul>
     *   <li>The reflection target was not resolved at construction time.</li>
     *   <li>The platform call throws {@link SecurityException} (privilege absent).</li>
     *   <li>Any other reflection error occurs.</li>
     * </ul>
     */
    @Override
    public boolean inject(GestureSpec spec) {
        if (injectInputEvent == null) {
            Log.w(TAG, "inject: injectInputEvent unavailable; dropping gesture " + spec);
            return false;
        }
        try {
            boolean accepted = dispatchGesture(spec);
            lastInjectionAccepted = accepted;
            return accepted;
        } catch (SecurityException e) {
            Log.w(TAG, "inject: INJECT_EVENTS permission denied; dropping gesture " + spec, e);
            lastInjectionAccepted = false;
            return false;
        } catch (Exception e) {
            Log.w(TAG, "inject: unexpected error dispatching gesture " + spec, e);
            lastInjectionAccepted = false;
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reports {@code true} only after a real {@link #inject} call was accepted by the
     * input subsystem. Until the first successful injection (or if the reflection target
     * was never resolved) this returns {@code false}, so the selection layer treats the
     * provider as unproven rather than assuming the privilege is present.
     */
    @Override
    public boolean isInjectionHonored() {
        return injectInputEvent != null && lastInjectionAccepted;
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Builds and injects the MotionEvents for {@code spec}.
     *
     * @return {@code true} if every injected event was accepted by the framework
     */
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
     * Builds a single-pointer {@link MotionEvent} for the given action, tagged with
     * the target virtual {@code displayId}.
     *
     * <p>Uses the 16-argument form of {@link MotionEvent#obtain} (available since
     * API 34) which accepts a {@code displayId} parameter, routing the event to the
     * correct virtual display without requiring the {@code @hide} {@code setDisplayId}
     * method. If this form is unavailable on older APIs the build will fail at compile
     * time rather than silently misroute events at runtime.
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

        // 16-arg obtain: downTime, eventTime, action, pointerCount,
        //                pointerProperties[], pointerCoords[],
        //                metaState, buttonState, xPrecision, yPrecision,
        //                deviceId, edgeFlags, source, displayId, flags, classification
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

    /**
     * Calls the hidden {@code InputManager#injectInputEvent} via reflection.
     *
     * @return the boolean return value of the underlying platform call
     */
    private boolean injectMotionEvent(MotionEvent event) throws Exception {
        try {
            Object result = injectInputEvent.invoke(injectTarget, event, INJECT_MODE_ASYNC);
            return Boolean.TRUE.equals(result);
        } finally {
            event.recycle();
        }
    }
}

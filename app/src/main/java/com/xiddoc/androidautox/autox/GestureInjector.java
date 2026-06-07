package com.xiddoc.androidautox.autox;

/**
 * Abstraction for injecting native input gestures onto a virtual display.
 *
 * <p>Implementations translate a {@link GestureSpec} into one or more
 * {@link android.view.MotionEvent} objects and dispatch them to the correct
 * display via the platform's input subsystem. Because Android has no public API
 * for cross-display input injection, every real implementation requires either:
 * <ul>
 *   <li>Elevated system privileges (signature/priv-app), or</li>
 *   <li>Root access (for the reflection-based fallback).</li>
 * </ul>
 *
 * <p>This interface exists as a seam so that:
 * <ol>
 *   <li>The framework-coupled injection code ({@link ReflectiveGestureInjector}) is
 *       isolated behind a well-defined boundary and excluded from the coverage gate.</li>
 *   <li>Tests can provide a no-op or recording stub without touching any Android API.</li>
 * </ol>
 *
 * <p><b>Privilege note:</b> injection via {@code InputManager#injectInputEvent} is
 * guarded by {@code android.permission.INJECT_EVENTS}, a signature-level permission.
 * Without it the call is silently dropped or throws {@link SecurityException}. Apps
 * running on rooted devices can escalate via {@code app_process} / libsu, but that
 * path is handled entirely inside the implementation; callers of this interface need
 * not be aware of the mechanism.
 */
public interface GestureInjector {

    /**
     * Injects the gesture described by {@code spec} onto the target display.
     *
     * <p>The call is best-effort: if the underlying platform call is unavailable
     * (e.g. the reflection target changed) or the required privilege is absent,
     * implementations should log a warning and return {@code false} rather than
     * throwing an unchecked exception.
     *
     * @param spec the gesture to inject; must not be {@code null}
     * @return {@code true} if the gesture was accepted by the input subsystem,
     *         {@code false} if it was dropped or if the injection API was unavailable
     */
    boolean inject(GestureSpec spec);
}

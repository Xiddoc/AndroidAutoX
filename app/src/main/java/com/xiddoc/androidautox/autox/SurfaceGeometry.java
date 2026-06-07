package com.xiddoc.androidautox.autox;

/**
 * Pure decision logic for the AutoX surface-lifecycle resize-vs-recreate policy.
 *
 * <h2>Problem</h2>
 * <p>When the Android Auto host calls
 * {@link androidx.car.app.SurfaceCallback#onSurfaceAvailable} more than once,
 * or when the car surface geometry changes mid-session, the
 * {@link VirtualDisplayController} either needs to:
 * <ul>
 *   <li>{@link Action#NOOP}      — dimensions/dpi are identical; no action needed.</li>
 *   <li>{@link Action#RESIZE}    — same {@link android.view.Surface} object but new
 *       width/height/dpi; call {@link android.hardware.display.VirtualDisplay#resize}
 *       instead of destroying and recreating the display.</li>
 *   <li>{@link Action#RECREATE}  — the underlying {@code Surface} identity changed
 *       (the host tore down and re-created the surface); the old
 *       {@link android.hardware.display.VirtualDisplay} is now backed by an invalid
 *       surface and must be released and recreated.</li>
 * </ul>
 *
 * <h2>Design</h2>
 * <p>This class is pure logic with no Android imports.  It accepts raw primitives
 * (width, height, dpi) and a boolean {@code surfaceIdentityChanged} (extracted from
 * the framework {@code SurfaceContainer} by the calling glue), deciding the action
 * entirely from those values.  That keeps the policy 100% unit-testable on the JVM
 * while the glue ({@link AutoXScreen}) remains excluded from the coverage gate.
 *
 * <h2>Decision rules</h2>
 * <pre>
 *   surfaceIdentityChanged == true                    → RECREATE (surface object changed)
 *   old == new (w, h, dpi all equal)                  → NOOP
 *   w/h/dpi differ but surface identity unchanged     → RESIZE
 * </pre>
 *
 * <p>This class has <b>no Android imports</b> and is fully unit-testable with plain
 * JUnit.  It is <em>not</em> in {@code jacocoExclusions} and must remain at 100%
 * line + branch coverage.
 */
public final class SurfaceGeometry {

    /**
     * The action that {@link AutoXScreen} should take in response to a surface geometry
     * event.
     */
    public enum Action {
        /**
         * Dimensions and dpi are identical and the surface identity has not changed;
         * no action is required.
         */
        NOOP,
        /**
         * The surface identity is the same but the dimensions or dpi have changed.
         * Call {@link android.hardware.display.VirtualDisplay#resize} on the existing
         * display instead of releasing and recreating it.
         */
        RESIZE,
        /**
         * The underlying {@link android.view.Surface} object has changed (a new surface
         * was provided by the host).  The old {@link android.hardware.display.VirtualDisplay}
         * is backed by an invalid surface and must be {@code release()}d and recreated.
         */
        RECREATE,
    }

    private SurfaceGeometry() {
    }

    /**
     * Decides the action required when a surface geometry event is received.
     *
     * @param oldWidth              width of the existing virtual display in pixels
     * @param oldHeight             height of the existing virtual display in pixels
     * @param oldDpi                density of the existing virtual display in dpi
     * @param newWidth              width reported by the new surface container in pixels
     * @param newHeight             height reported by the new surface container in pixels
     * @param newDpi                density reported by the new surface container in dpi
     * @param surfaceIdentityChanged {@code true} if the host provided a different
     *                               {@link android.view.Surface} object (i.e. the
     *                               surface was torn down and re-created), {@code false}
     *                               if the existing surface is being resized in place
     * @return the {@link Action} the caller should take
     */
    public static Action decide(int oldWidth, int oldHeight, int oldDpi,
                                int newWidth, int newHeight, int newDpi,
                                boolean surfaceIdentityChanged) {
        if (surfaceIdentityChanged) {
            return Action.RECREATE;
        }
        if (oldWidth == newWidth && oldHeight == newHeight && oldDpi == newDpi) {
            return Action.NOOP;
        }
        return Action.RESIZE;
    }
}

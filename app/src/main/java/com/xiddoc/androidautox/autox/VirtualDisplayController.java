package com.xiddoc.androidautox.autox;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.view.Surface;

/**
 * Thin wrapper around {@link DisplayManager#createVirtualDisplay} that creates and
 * manages the AutoX isolated virtual display.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Accepts an {@link AutoXDisplaySpec} (width, height, dpi) and a {@link Surface}
 *       provided by the Android Auto {@code SurfaceContainer}.</li>
 *   <li>Derives the display flags from {@link VirtualDisplayConfig#defaultFlags()}.</li>
 *   <li>Uses {@link VirtualDisplayConfig#DISPLAY_NAME} as the display name.</li>
 *   <li>Exposes the created display id via {@link #getDisplayId()}.</li>
 *   <li>Releases the {@link VirtualDisplay} on {@link #release()}.</li>
 * </ul>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>No mirroring / screen-capture: {@link VirtualDisplayConfig#FLAG_OWN_CONTENT_ONLY}
 *       is always set via {@code defaultFlags()}, so the display shows only what the
 *       launched guest app renders onto it.</li>
 *   <li>This class is framework-coupled and cannot be unit-tested off-device; it is
 *       therefore excluded from the JaCoCo coverage gate.</li>
 *   <li>Callers should always {@link #release()} the controller (e.g. from
 *       {@code AutoXScreen.onSurfaceDestroyed}) to free the display resource.</li>
 * </ul>
 */
public final class VirtualDisplayController {

    private final VirtualDisplay virtualDisplay;

    /**
     * The spec describing the current geometry of the virtual display.
     * Updated in place by {@link #resize(AutoXDisplaySpec)}.
     */
    private AutoXDisplaySpec spec;

    /** True once {@link #release()} has run; guards against double-release and use-after-release. */
    private boolean released;

    /**
     * Creates the AutoX isolated virtual display.
     *
     * @param displayManager the system {@link DisplayManager}; obtain via
     *                       {@code context.getSystemService(DisplayManager.class)}
     * @param spec           display geometry (width, height, dpi) from the car's
     *                       {@code SurfaceContainer}
     * @param surface        the {@link Surface} provided by the
     *                       {@code SurfaceCallback.onSurfaceAvailable} callback
     * @throws IllegalArgumentException if {@code spec} or {@code surface} is null
     * @throws IllegalStateException    if the display manager refuses to create the
     *                                  virtual display (e.g. missing permission)
     */
    public VirtualDisplayController(DisplayManager displayManager,
                                    AutoXDisplaySpec spec,
                                    Surface surface) {
        if (displayManager == null) {
            throw new IllegalArgumentException("displayManager must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }

        AutoXLog.d("VDisplay", "VirtualDisplayController: creating display " + spec
                + " flags=0x" + Integer.toHexString(VirtualDisplayConfig.defaultFlags()));

        this.spec = spec;
        this.virtualDisplay = displayManager.createVirtualDisplay(
                VirtualDisplayConfig.DISPLAY_NAME,
                spec.getWidth(),
                spec.getHeight(),
                spec.getDensityDpi(),
                surface,
                VirtualDisplayConfig.defaultFlags());

        if (virtualDisplay == null) {
            throw new IllegalStateException(
                    "DisplayManager.createVirtualDisplay returned null — "
                            + "check that VIRTUAL_DISPLAY_FLAG_TRUSTED is permitted on this device.");
        }
        AutoXLog.d("VDisplay", "VirtualDisplayController: display created id=" + getDisplayId());
    }

    /**
     * Returns the display id of the underlying {@link VirtualDisplay}, suitable for
     * passing to {@link android.app.ActivityOptions#setLaunchDisplayId(int)} and
     * {@link GestureSpec#getDisplayId()}.
     *
     * @return the virtual display id; always &ge; 1 (display 0 is the primary display)
     * @throws IllegalStateException if called after {@link #release()}
     */
    public int getDisplayId() {
        if (released) {
            throw new IllegalStateException("VirtualDisplayController has been released");
        }
        return virtualDisplay.getDisplay().getDisplayId();
    }

    /**
     * Returns the {@link AutoXDisplaySpec} this controller was created with (the geometry of
     * the virtual display). Used by {@link AutoXScreen} to build the virtual-space side of the
     * {@link CoordinateTranslator} when routing touch events.
     *
     * @return the virtual-display spec
     * @throws IllegalStateException if called after {@link #release()}
     */
    public AutoXDisplaySpec getSpec() {
        if (released) {
            throw new IllegalStateException("VirtualDisplayController has been released");
        }
        return spec;
    }

    /**
     * Resizes the underlying {@link VirtualDisplay} in place without recreating it.
     *
     * <p>Use this when the car surface reports new dimensions or dpi but the
     * {@link android.view.Surface} object itself has not changed — i.e. when
     * {@link SurfaceGeometry#decide} returns {@link SurfaceGeometry.Action#RESIZE}.
     * Resizing avoids the overhead of destroying and recreating the display (which
     * would re-trigger an app launch) while keeping the projection in sync with the
     * new car-surface geometry.
     *
     * @param newSpec the new display geometry; must not be {@code null}
     * @throws IllegalArgumentException if {@code newSpec} is {@code null}
     * @throws IllegalStateException    if called after {@link #release()}
     */
    public void resize(@androidx.annotation.NonNull AutoXDisplaySpec newSpec) {
        if (newSpec == null) {
            throw new IllegalArgumentException("newSpec must not be null");
        }
        if (released) {
            throw new IllegalStateException("VirtualDisplayController has been released");
        }
        AutoXLog.d("VDisplay", "VirtualDisplayController: resizing display id=" + getDisplayId()
                + " to " + newSpec);
        virtualDisplay.resize(newSpec.getWidth(), newSpec.getHeight(), newSpec.getDensityDpi());
        spec = newSpec;
    }

    /**
     * Releases the underlying {@link VirtualDisplay}, freeing the display resource.
     *
     * <p>After this call {@link #getDisplayId()} and {@link #getSpec()} fail fast with
     * {@link IllegalStateException}. Idempotent: a second call is a harmless no-op.
     */
    public void release() {
        if (released) {
            return;
        }
        AutoXLog.d("VDisplay", "VirtualDisplayController: releasing display id=" + getDisplayId());
        released = true;
        virtualDisplay.release();
    }
}

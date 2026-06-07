package com.xiddoc.androidautox.autox;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.Log;
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

    private static final String TAG = "AndroidAutoX";

    private final VirtualDisplay virtualDisplay;

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
     * @throws RuntimeException         if the display manager refuses to create the
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

        Log.d(TAG, "VirtualDisplayController: creating display " + spec
                + " flags=0x" + Integer.toHexString(VirtualDisplayConfig.defaultFlags()));

        this.virtualDisplay = displayManager.createVirtualDisplay(
                VirtualDisplayConfig.DISPLAY_NAME,
                spec.getWidth(),
                spec.getHeight(),
                spec.getDensityDpi(),
                surface,
                VirtualDisplayConfig.defaultFlags());

        if (virtualDisplay == null) {
            throw new RuntimeException(
                    "DisplayManager.createVirtualDisplay returned null — "
                            + "check that VIRTUAL_DISPLAY_FLAG_TRUSTED is permitted on this device.");
        }
        Log.d(TAG, "VirtualDisplayController: display created id=" + getDisplayId());
    }

    /**
     * Returns the display id of the underlying {@link VirtualDisplay}, suitable for
     * passing to {@link android.app.ActivityOptions#setLaunchDisplayId(int)} and
     * {@link GestureSpec#getDisplayId()}.
     *
     * @return the virtual display id; always &ge; 1 (display 0 is the primary display)
     */
    public int getDisplayId() {
        return virtualDisplay.getDisplay().getDisplayId();
    }

    /**
     * Releases the underlying {@link VirtualDisplay}, freeing the display resource.
     *
     * <p>After this call the display id returned by {@link #getDisplayId()} is invalid.
     * Idempotent: calling {@code release()} more than once is harmless.
     */
    public void release() {
        Log.d(TAG, "VirtualDisplayController: releasing display id=" + getDisplayId());
        virtualDisplay.release();
    }
}

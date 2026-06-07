package com.xiddoc.androidautox.autox.provider;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import com.xiddoc.androidautox.autox.AutoXDisplaySpec;
import com.xiddoc.androidautox.autox.VirtualDisplayConfig;

/**
 * EXCLUDED GLUE: best-effort {@link DisplayProvider} backed by
 * {@link DisplayManager#createVirtualDisplay}. Requests the trusted flag via the pure
 * {@link VirtualDisplayConfig#defaultFlags()} and DETECTS whether it was actually honored
 * by re-reading {@code Display.getFlags()} on the created display, reporting the result via
 * {@link #isTrustedDisplayHonored()} instead of assuming success.
 *
 * <p>Framework-coupled (DisplayManager / VirtualDisplay / Surface) so it is listed in
 * {@code jacocoExclusions}. The flag math and trusted-detection threshold live in the pure
 * {@link VirtualDisplayConfig} / {@link TrustedFlagPolicy}.
 */
public final class RootDisplayProvider implements DisplayProvider {

    private static final String TAG = "AndroidAutoX";

    private final DisplayManager displayManager;
    private final Surface surface;

    private VirtualDisplay virtualDisplay;
    private boolean trustedHonored;

    /**
     * @param displayManager system {@link DisplayManager}
     * @param surface        the {@link Surface} to render the virtual display onto
     */
    public RootDisplayProvider(DisplayManager displayManager, Surface surface) {
        if (displayManager == null) {
            throw new IllegalArgumentException("displayManager must not be null");
        }
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }
        this.displayManager = displayManager;
        this.surface = surface;
    }

    @Override
    public int create(AutoXDisplaySpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        try {
            virtualDisplay = displayManager.createVirtualDisplay(
                    VirtualDisplayConfig.DISPLAY_NAME,
                    spec.getWidth(), spec.getHeight(), spec.getDensityDpi(),
                    surface, VirtualDisplayConfig.defaultFlags());
        } catch (Throwable t) {
            Log.w(TAG, "createVirtualDisplay failed", t);
            virtualDisplay = null;
        }
        if (virtualDisplay == null) {
            trustedHonored = false;
            return NO_DISPLAY;
        }
        detectTrusted();
        return getDisplayId();
    }

    /** Re-reads the created display's flags to see whether the trusted flag survived. */
    private void detectTrusted() {
        try {
            Display d = virtualDisplay.getDisplay();
            trustedHonored = d != null && TrustedFlagPolicy.isTrusted(d.getFlags());
        } catch (Throwable t) {
            Log.w(TAG, "trusted-flag detection failed; assuming not honored", t);
            trustedHonored = false;
        }
    }

    @Override
    public boolean resize(int width, int height, int densityDpi) {
        if (virtualDisplay == null) {
            return false;
        }
        try {
            virtualDisplay.resize(width, height, densityDpi);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "resize failed", t);
            return false;
        }
    }

    @Override
    public void release() {
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Throwable ignored) {
                // best-effort
            }
            virtualDisplay = null;
        }
        trustedHonored = false;
    }

    @Override
    public int getDisplayId() {
        if (virtualDisplay == null) {
            return NO_DISPLAY;
        }
        Display d = virtualDisplay.getDisplay();
        return d == null ? NO_DISPLAY : d.getDisplayId();
    }

    @Override
    public boolean isTrustedDisplayHonored() {
        return trustedHonored;
    }
}

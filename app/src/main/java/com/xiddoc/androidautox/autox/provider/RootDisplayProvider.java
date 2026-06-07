package com.xiddoc.androidautox.autox.provider;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import com.xiddoc.androidautox.autox.AutoXDisplaySpec;
import com.xiddoc.androidautox.autox.VirtualDisplayConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * EXCLUDED GLUE: best-effort {@link DisplayProvider} backed by
 * {@link DisplayManager#createVirtualDisplay}. Requests the trusted flag via the pure
 * {@link VirtualDisplayConfig#defaultFlags()} and DETECTS whether it was actually honored
 * by inspecting the created display's {@code DisplayInfo.flags} against
 * {@code Display.FLAG_TRUSTED}, reporting the result via {@link #isTrustedDisplayHonored()}
 * instead of assuming success.
 *
 * <h2>Why not {@code TrustedFlagPolicy.isTrusted(Display.getFlags())}?</h2>
 * <p>{@code Display.getFlags()} returns the {@code Display.FLAG_*} bitspace, which is a
 * <b>different</b> bitspace from the {@code VIRTUAL_DISPLAY_FLAG_*} values used when
 * <em>creating</em> the display (which is what {@link VirtualDisplayConfig#FLAG_TRUSTED} /
 * {@link TrustedFlagPolicy} model). Masking the public {@code getFlags()} value with the
 * create-time {@code FLAG_TRUSTED} bit therefore tests the wrong bit. The trusted state is
 * only observable on the {@code @hide} {@code DisplayInfo.flags} field via
 * {@code Display.FLAG_TRUSTED}, which we read by best-effort reflection here.
 *
 * <p>Framework-coupled (DisplayManager / VirtualDisplay / Surface / hidden DisplayInfo) so
 * it is listed in {@code jacocoExclusions}. The create-time flag math lives in the pure
 * {@link VirtualDisplayConfig}.
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

    /**
     * Inspects the created display's {@code DisplayInfo.flags} against
     * {@code Display.FLAG_TRUSTED} to see whether the trusted flag survived.
     *
     * <p>Best-effort reflection: reads the hidden {@code Display.getDisplayInfo()} (or the
     * hidden {@code DisplayInfo.flags} field) and the hidden {@code Display.FLAG_TRUSTED}
     * constant. If either is unavailable on this platform, conservatively reports
     * {@code false} (not honored) rather than guessing.
     *
     * <p>// TODO(device-verify): confirm on a real device that {@code Display.FLAG_TRUSTED}
     * is set on the {@code DisplayInfo} of an LSPosed/root-trusted virtual display, and that
     * {@code getDisplayInfo()} remains accessible from a (root) app process on API 31–34.
     */
    private void detectTrusted() {
        try {
            Display d = virtualDisplay.getDisplay();
            trustedHonored = d != null && readTrustedFromDisplayInfo(d);
        } catch (Throwable t) {
            Log.w(TAG, "trusted-flag detection failed; assuming not honored", t);
            trustedHonored = false;
        }
    }

    /**
     * Reflectively reads {@code (DisplayInfo.flags & Display.FLAG_TRUSTED) != 0} for
     * {@code display}. Returns a conservative {@code false} if any reflective piece is
     * unavailable.
     */
    private static boolean readTrustedFromDisplayInfo(Display display) {
        try {
            // Display.FLAG_TRUSTED is @hide; resolve it reflectively. Absent → not honored.
            Field flagTrustedField = Display.class.getField("FLAG_TRUSTED");
            int flagTrusted = flagTrustedField.getInt(null);

            // Display.getDisplayInfo() is @hide and returns an android.view.DisplayInfo
            // whose `flags` int field carries the FLAG_TRUSTED bit.
            Method getDisplayInfo = Display.class.getMethod("getDisplayInfo");
            getDisplayInfo.setAccessible(true);
            Object displayInfo = getDisplayInfo.invoke(display);
            if (displayInfo == null) {
                return false;
            }
            Field flagsField = displayInfo.getClass().getField("flags");
            int infoFlags = flagsField.getInt(displayInfo);
            return (infoFlags & flagTrusted) != 0;
        } catch (Throwable t) {
            return false;
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

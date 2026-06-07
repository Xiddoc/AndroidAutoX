package com.xiddoc.androidautox.autox;

/**
 * Computes the {@link android.hardware.display.DisplayManager} virtual-display
 * flag bitmask from semantic policy booleans for the AutoX isolated display.
 *
 * <p>Centralising flag composition here keeps the Android glue in the Activity/Service
 * thin and makes the policy testable with plain JUnit — no device or emulator required.
 *
 * <h2>Flag constants</h2>
 * <p>The three flags used by AutoX are:
 * <ul>
 *   <li>{@link #FLAG_PUBLIC} ({@code 0x00000001}) — mirror of
 *       {@code DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC}. Makes the display
 *       visible to other apps via {@code DisplayManager.getDisplays()}.</li>
 *   <li>{@link #FLAG_OWN_CONTENT_ONLY} ({@code 0x00000008}) — mirror of
 *       {@code DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY}. Prevents
 *       the system from mirroring content from other displays onto this one.</li>
 *   <li>{@link #FLAG_TRUSTED} ({@code 0x00000400}, i.e. {@code 1 << 10}) — a
 *       {@code @hide} constant not present in the public SDK. Grants the display
 *       elevated trust, which is required for certain system UI to render on it.
 *       Requires the {@code android.permission.ADD_TRUSTED_DISPLAY} signature
 *       permission. Defined here by its numeric value because it is not accessible
 *       from the public API surface.</li>
 * </ul>
 *
 * <p>This class has no Android imports and is fully unit-testable with plain JUnit.
 */
public final class VirtualDisplayConfig {

    /**
     * Mirror of {@code DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC} ({@code 1}).
     * Makes the virtual display visible to external apps.
     */
    public static final int FLAG_PUBLIC = 1;           // DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC

    /**
     * Mirror of {@code DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY}
     * ({@code 8}). Prevents content from other displays being mirrored onto
     * this virtual display.
     */
    public static final int FLAG_OWN_CONTENT_ONLY = 8; // DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY

    /**
     * Numeric value of the {@code @hide} constant
     * {@code DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED} ({@code 1 << 10 = 1024}).
     *
     * <p>This constant is not part of the public Android SDK API; it is defined here
     * by its integer value. Setting this flag requires the
     * {@code android.permission.ADD_TRUSTED_DISPLAY} signature-level permission.
     * Callers without that permission should omit this flag (pass {@code trusted=false}
     * to {@link #flags(boolean, boolean, boolean)}).
     */
    public static final int FLAG_TRUSTED = 1 << 10;   // DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED (@hide, value=1024)

    /**
     * The display name used when creating the AutoX isolated virtual display via
     * {@link android.hardware.display.DisplayManager#createVirtualDisplay}.
     */
    public static final String DISPLAY_NAME = "AutoX_Isolated_Canvas";

    private VirtualDisplayConfig() {
    }

    /**
     * Computes the virtual-display flag bitmask for the given policy booleans.
     *
     * @param isPublic       whether to include {@link #FLAG_PUBLIC}
     * @param ownContentOnly whether to include {@link #FLAG_OWN_CONTENT_ONLY}
     * @param trusted        whether to include {@link #FLAG_TRUSTED} (requires
     *                       {@code ADD_TRUSTED_DISPLAY} signature permission)
     * @return the ORed bitmask of the requested flags
     */
    public static int flags(boolean isPublic, boolean ownContentOnly, boolean trusted) {
        int result = 0;
        if (isPublic) {
            result |= FLAG_PUBLIC;
        }
        if (ownContentOnly) {
            result |= FLAG_OWN_CONTENT_ONLY;
        }
        if (trusted) {
            result |= FLAG_TRUSTED;
        }
        return result;
    }

    /**
     * Returns the default AutoX virtual-display flag bitmask:
     * {@link #FLAG_PUBLIC} | {@link #FLAG_OWN_CONTENT_ONLY} | {@link #FLAG_TRUSTED}.
     *
     * <p>This is the recommended combination for the AutoX isolated display: the
     * display is publicly enumerable, shows only its own content, and is trusted
     * so system-level UI can render onto it.
     *
     * @return default flag bitmask ({@code FLAG_PUBLIC | FLAG_OWN_CONTENT_ONLY | FLAG_TRUSTED})
     */
    public static int defaultFlags() {
        return flags(true, true, true);
    }
}

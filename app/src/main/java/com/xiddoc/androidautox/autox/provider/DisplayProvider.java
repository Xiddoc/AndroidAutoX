package com.xiddoc.androidautox.autox.provider;

import com.xiddoc.androidautox.autox.AutoXDisplaySpec;

/**
 * Privileged-provider seam for creating, resizing and releasing the AutoX virtual
 * display, and for reporting whether the {@code VIRTUAL_DISPLAY_FLAG_TRUSTED} request
 * was actually honored.
 *
 * <p>A trusted virtual display requires the {@code ADD_TRUSTED_DISPLAY} signature
 * permission. On a stock or root-only device {@code DisplayManager} may silently strip
 * the trusted flag (creating an untrusted display) rather than failing — guest apps then
 * cannot receive certain system UI / input. The LSPosed hook relaxes the trusted-flag
 * check in {@code system_server}; the {@link #isTrustedDisplayHonored()} probe lets the
 * selection layer detect which case it is in.
 *
 * <p>All methods are best-effort and report state via return values rather than throwing
 * where practical; construction failures (a null display) surface as
 * {@link #getDisplayId()} returning {@link #NO_DISPLAY}.
 */
public interface DisplayProvider {

    /** Sentinel display id returned before a display is created or after release. */
    int NO_DISPLAY = -1;

    /**
     * Creates the AutoX virtual display for the given geometry.
     *
     * @param spec the virtual-display geometry (width, height, dpi); must not be null
     * @return the created display id (&ge; 1), or {@link #NO_DISPLAY} on failure
     */
    int create(AutoXDisplaySpec spec);

    /**
     * Resizes the current virtual display in place.
     *
     * @param width      new width in pixels (&gt; 0)
     * @param height     new height in pixels (&gt; 0)
     * @param densityDpi new density (&gt; 0)
     * @return {@code true} if the resize was applied
     */
    boolean resize(int width, int height, int densityDpi);

    /** Releases the current virtual display; idempotent. */
    void release();

    /**
     * @return the current display id (&ge; 1) or {@link #NO_DISPLAY} if none is active
     */
    int getDisplayId();

    /**
     * Reports whether the most recently created display actually carries the trusted
     * flag (i.e. {@code VIRTUAL_DISPLAY_FLAG_TRUSTED} was honored, not silently stripped).
     *
     * @return {@code true} if the active display is trusted
     */
    boolean isTrustedDisplayHonored();
}

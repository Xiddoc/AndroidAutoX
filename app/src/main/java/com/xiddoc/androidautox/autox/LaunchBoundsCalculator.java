package com.xiddoc.androidautox.autox;

/**
 * Pure utility class that computes the launch bounds rectangle used when AutoX
 * targets a specific orientation/aspect policy for an app launched onto the
 * virtual display.
 *
 * <h2>Motivation</h2>
 * <p>When freeform windowing is enabled (via
 * {@link SecureSettingsSpec#KEY_ENABLE_FREEFORM}), Android honours
 * {@code ActivityOptions.setLaunchBounds(Rect)}.  Supplying a correctly-sized
 * bounds rectangle lets AutoX force a "forced vertical" (portrait) layout even
 * when the virtual display itself is landscape — important because most head-unit
 * displays are wide/landscape while many companion apps are designed for a
 * portrait window.
 *
 * <h2>Architecture §2.4 — forced-vertical case</h2>
 * <p>AutoX's "forced vertical" orientation policy picks the largest portrait
 * sub-rectangle that fits within the display while honouring a target aspect
 * ratio (height/width &ge; 1).  The rectangle is centred horizontally on the
 * display and top-aligned.
 *
 * <ul>
 *   <li>If the display is already portrait (height &ge; width) the full display
 *       area is used.
 *   <li>Otherwise the width is shrunk to {@code floor(height / aspectRatio)} so
 *       that {@code boundsHeight / boundsWidth &ge; aspectRatio}.  The shorter
 *       width is centred: {@code left = (displayWidth - boundsWidth) / 2}.
 * </ul>
 *
 * <h2>No Android imports</h2>
 * <p>This class is deliberately framework-free.  The caller (excluded glue)
 * converts the returned {@link Bounds} into {@code android.graphics.Rect} and
 * passes it to {@code ActivityOptions.setLaunchBounds}.  That keeps this
 * policy 100% unit-testable on the JVM.
 *
 * <p>This class is <em>not</em> in {@code jacocoExclusions} and must remain at
 * 100% line + branch coverage.
 */
public final class LaunchBoundsCalculator {

    /**
     * Immutable value object holding a launch bounds rectangle as
     * (left, top, right, bottom) in virtual-display pixel coordinates.
     *
     * <p>Coordinates are consistent with {@code android.graphics.Rect}:
     * {@code left &le; right}, {@code top &le; bottom}.
     */
    public static final class Bounds {

        /** Left edge of the bounds rectangle in pixels (inclusive). */
        public final int left;

        /** Top edge of the bounds rectangle in pixels (inclusive). */
        public final int top;

        /** Right edge of the bounds rectangle in pixels (exclusive). */
        public final int right;

        /** Bottom edge of the bounds rectangle in pixels (exclusive). */
        public final int bottom;

        Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        /**
         * Width of the bounds rectangle in pixels: {@code right - left}.
         */
        public int width() {
            return right - left;
        }

        /**
         * Height of the bounds rectangle in pixels: {@code bottom - top}.
         */
        public int height() {
            return bottom - top;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Bounds)) return false;
            Bounds other = (Bounds) o;
            return left == other.left && top == other.top
                    && right == other.right && bottom == other.bottom;
        }

        @Override
        public int hashCode() {
            int result = left;
            result = 31 * result + top;
            result = 31 * result + right;
            result = 31 * result + bottom;
            return result;
        }

        @Override
        public String toString() {
            return "Bounds{left=" + left + ", top=" + top
                    + ", right=" + right + ", bottom=" + bottom + '}';
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Computes the launch bounds for the "forced vertical" (portrait) policy.
     *
     * <p>Returns the largest portrait sub-rectangle that fits within the display,
     * centred horizontally, top-aligned, with an aspect ratio (height/width) of at
     * least {@code targetAspectRatio}.
     *
     * <ul>
     *   <li>If {@code displayHeight &ge; displayWidth} (already portrait), the full
     *       display rectangle is returned: {@code Bounds(0, 0, displayWidth, displayHeight)}.
     *   <li>Otherwise the width is capped to
     *       {@code floor(displayHeight / targetAspectRatio)} and centred:
     *       {@code left = (displayWidth - boundsWidth) / 2}.
     * </ul>
     *
     * @param displayWidth      the virtual display width in pixels; must be &gt; 0
     * @param displayHeight     the virtual display height in pixels; must be &gt; 0
     * @param densityDpi        the virtual display density in dpi; must be &gt; 0
     * @param targetAspectRatio the minimum height/width ratio; must be &gt; 0.0
     * @return the computed launch bounds
     * @throws IllegalArgumentException if any parameter is out of range
     */
    public static Bounds forcedVertical(int displayWidth, int displayHeight, int densityDpi,
                                        double targetAspectRatio) {
        validateDimension("displayWidth", displayWidth);
        validateDimension("displayHeight", displayHeight);
        validateDimension("densityDpi", densityDpi);
        validateAspectRatio(targetAspectRatio);

        // Already portrait (or square): use the full display area.
        if (displayHeight >= displayWidth) {
            return new Bounds(0, 0, displayWidth, displayHeight);
        }

        // Landscape display: shrink width to satisfy the portrait aspect-ratio constraint.
        int boundsWidth = (int) (displayHeight / targetAspectRatio);
        // Clamp to display width (in case targetAspectRatio < 1.0 would produce a wider rect).
        if (boundsWidth > displayWidth) {
            boundsWidth = displayWidth;
        }
        int left = (displayWidth - boundsWidth) / 2;
        return new Bounds(left, 0, left + boundsWidth, displayHeight);
    }

    /**
     * Computes the launch bounds for the full-display policy — the target app
     * fills the entire virtual display.  This is the identity case: no scaling or
     * cropping.
     *
     * @param displayWidth  the virtual display width in pixels; must be &gt; 0
     * @param displayHeight the virtual display height in pixels; must be &gt; 0
     * @param densityDpi    the virtual display density in dpi; must be &gt; 0
     * @return {@code Bounds(0, 0, displayWidth, displayHeight)}
     * @throws IllegalArgumentException if any parameter is out of range
     */
    public static Bounds fullDisplay(int displayWidth, int displayHeight, int densityDpi) {
        validateDimension("displayWidth", displayWidth);
        validateDimension("displayHeight", displayHeight);
        validateDimension("densityDpi", densityDpi);
        return new Bounds(0, 0, displayWidth, displayHeight);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static void validateDimension(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    name + " must be positive (> 0), got: " + value);
        }
    }

    private static void validateAspectRatio(double ratio) {
        if (ratio <= 0.0 || Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            throw new IllegalArgumentException(
                    "targetAspectRatio must be a finite positive number, got: " + ratio);
        }
    }

    private LaunchBoundsCalculator() {
        // Static utility class; prevent instantiation.
    }
}

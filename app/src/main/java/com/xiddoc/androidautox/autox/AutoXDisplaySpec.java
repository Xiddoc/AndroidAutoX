package com.xiddoc.androidautox.autox;

import java.util.Objects;

/**
 * Immutable value object describing the geometry of a display surface — either the
 * physical car head-unit display that the Android Auto stack hands us via a
 * {@code SurfaceContainer}, or the {@link android.hardware.display.VirtualDisplay}
 * we create to host the guest app.
 *
 * <p>Instances are validated eagerly: width, height and densityDpi must all be
 * strictly positive, reflecting the constraint that a display of zero or negative
 * size cannot render any content.
 *
 * <p>This class is pure data — it carries no Android framework dependencies — so it
 * can be created and verified in plain JUnit tests on the JVM.
 */
public final class AutoXDisplaySpec {

    private final int width;
    private final int height;
    private final int densityDpi;

    /**
     * Constructs a validated display specification.
     *
     * @param width      horizontal resolution in pixels; must be &gt; 0
     * @param height     vertical resolution in pixels; must be &gt; 0
     * @param densityDpi screen density in dots per inch; must be &gt; 0
     * @throws IllegalArgumentException if any parameter is &le; 0
     */
    public AutoXDisplaySpec(int width, int height, int densityDpi) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive, got: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive, got: " + height);
        }
        if (densityDpi <= 0) {
            throw new IllegalArgumentException("densityDpi must be positive, got: " + densityDpi);
        }
        this.width = width;
        this.height = height;
        this.densityDpi = densityDpi;
    }

    /**
     * Returns the horizontal resolution of this display surface in pixels.
     *
     * @return width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the vertical resolution of this display surface in pixels.
     *
     * @return height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the screen density of this display surface in dots per inch.
     *
     * @return density in dpi
     */
    public int getDensityDpi() {
        return densityDpi;
    }

    /**
     * Two {@code AutoXDisplaySpec} instances are equal when their width, height, and
     * densityDpi fields are all identical.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AutoXDisplaySpec)) return false;
        AutoXDisplaySpec other = (AutoXDisplaySpec) o;
        return width == other.width
                && height == other.height
                && densityDpi == other.densityDpi;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(width, height, densityDpi);
    }

    /**
     * Returns a human-readable representation useful for logging and debugging,
     * e.g. {@code AutoXDisplaySpec{width=1920, height=1080, densityDpi=240}}.
     */
    @Override
    public String toString() {
        return "AutoXDisplaySpec{"
                + "width=" + width
                + ", height=" + height
                + ", densityDpi=" + densityDpi
                + '}';
    }
}

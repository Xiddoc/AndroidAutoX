package com.xiddoc.androidautox.autox;

import java.util.Objects;

/**
 * Pure-math mapping from car-digitizer touch coordinates to virtual-display
 * pixel coordinates for the AutoX isolated virtual display.
 *
 * <p>The head-unit's touch digitizer reports events in its own coordinate space
 * (the <em>car</em> space). The guest app runs on a {@link android.hardware.display.VirtualDisplay}
 * that may have a different resolution. This class scales a raw digitizer hit
 * into the virtual-display's coordinate space using the resolution ratio:
 *
 * <pre>
 *   X_virt = X_car * (Width_virt  / Width_car)
 *   Y_virt = Y_car * (Height_virt / Height_car)
 * </pre>
 *
 * <p>Results are <em>clamped</em> to the target bounds {@code [0, width]} and
 * {@code [0, height]} so an out-of-range digitizer hit (e.g. a bezel tap
 * reported as a coordinate just outside the logical screen area) never produces
 * a point that falls off the virtual canvas.
 *
 * <p>All internal arithmetic uses {@code double} for precision; outputs are
 * {@code float} (the type Android's {@code MotionEvent} uses for coordinates).
 * No Android imports are needed — this class is unit-testable with plain JUnit.
 */
public final class CoordinateTranslator {

    /**
     * An immutable pair of translated (x, y) floats returned by
     * {@link #translate(float, float)}.
     */
    public static final class TranslatedPoint {

        private final float x;
        private final float y;

        /**
         * Constructs a translated point.
         *
         * @param x translated x coordinate in virtual-display space
         * @param y translated y coordinate in virtual-display space
         */
        public TranslatedPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }

        /**
         * Returns the translated x coordinate in virtual-display pixel space.
         *
         * @return x coordinate
         */
        public float getX() {
            return x;
        }

        /**
         * Returns the translated y coordinate in virtual-display pixel space.
         *
         * @return y coordinate
         */
        public float getY() {
            return y;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TranslatedPoint)) return false;
            TranslatedPoint other = (TranslatedPoint) o;
            return Float.compare(x, other.x) == 0 && Float.compare(y, other.y) == 0;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        /**
         * Returns a human-readable representation, e.g.
         * {@code TranslatedPoint{x=320.0, y=240.0}}.
         */
        @Override
        public String toString() {
            return "TranslatedPoint{x=" + x + ", y=" + y + '}';
        }
    }

    /** Width of the car display (digitizer space), in pixels. */
    private final int carWidth;
    /** Height of the car display (digitizer space), in pixels. */
    private final int carHeight;
    /** Width of the virtual display (target space), in pixels. */
    private final int virtWidth;
    /** Height of the virtual display (target space), in pixels. */
    private final int virtHeight;

    /** Pre-computed X scale ratio (virtWidth / carWidth). */
    private final double scaleX;
    /** Pre-computed Y scale ratio (virtHeight / carHeight). */
    private final double scaleY;

    /**
     * Constructs a translator from a source (car) spec to a target (virtual) spec.
     *
     * @param carSpec  geometry of the car head-unit display (digitizer space)
     * @param virtSpec geometry of the virtual display (target coordinate space)
     * @throws NullPointerException if either spec is {@code null}
     */
    public CoordinateTranslator(AutoXDisplaySpec carSpec, AutoXDisplaySpec virtSpec) {
        this(Objects.requireNonNull(carSpec, "carSpec").getWidth(),
             carSpec.getHeight(),
             Objects.requireNonNull(virtSpec, "virtSpec").getWidth(),
             virtSpec.getHeight());
    }

    /**
     * Constructs a translator from raw width/height pairs.
     *
     * @param carWidth   car-display width in pixels; must be &gt; 0
     * @param carHeight  car-display height in pixels; must be &gt; 0
     * @param virtWidth  virtual-display width in pixels; must be &gt; 0
     * @param virtHeight virtual-display height in pixels; must be &gt; 0
     * @throws IllegalArgumentException if any dimension is &le; 0
     */
    public CoordinateTranslator(int carWidth, int carHeight, int virtWidth, int virtHeight) {
        if (carWidth <= 0) {
            throw new IllegalArgumentException("carWidth must be positive, got: " + carWidth);
        }
        if (carHeight <= 0) {
            throw new IllegalArgumentException("carHeight must be positive, got: " + carHeight);
        }
        if (virtWidth <= 0) {
            throw new IllegalArgumentException("virtWidth must be positive, got: " + virtWidth);
        }
        if (virtHeight <= 0) {
            throw new IllegalArgumentException("virtHeight must be positive, got: " + virtHeight);
        }
        this.carWidth = carWidth;
        this.carHeight = carHeight;
        this.virtWidth = virtWidth;
        this.virtHeight = virtHeight;
        this.scaleX = (double) virtWidth / carWidth;
        this.scaleY = (double) virtHeight / carHeight;
    }

    /**
     * Translates a car-digitizer x coordinate into virtual-display x space.
     *
     * <p>The result is clamped to {@code [0, virtWidth]}.
     *
     * @param xCar x coordinate in car-digitizer space
     * @return x coordinate in virtual-display pixel space, clamped to [0, virtWidth]
     */
    public float translateX(float xCar) {
        double raw = xCar * scaleX;
        return (float) Math.max(0.0, Math.min(virtWidth, raw));
    }

    /**
     * Translates a car-digitizer y coordinate into virtual-display y space.
     *
     * <p>The result is clamped to {@code [0, virtHeight]}.
     *
     * @param yCar y coordinate in car-digitizer space
     * @return y coordinate in virtual-display pixel space, clamped to [0, virtHeight]
     */
    public float translateY(float yCar) {
        double raw = yCar * scaleY;
        return (float) Math.max(0.0, Math.min(virtHeight, raw));
    }

    /**
     * Translates a car-digitizer (x, y) pair into a virtual-display
     * {@link TranslatedPoint}, clamping both axes.
     *
     * @param xCar x coordinate in car-digitizer space
     * @param yCar y coordinate in car-digitizer space
     * @return the translated and clamped point in virtual-display space
     */
    public TranslatedPoint translate(float xCar, float yCar) {
        return new TranslatedPoint(translateX(xCar), translateY(yCar));
    }
}

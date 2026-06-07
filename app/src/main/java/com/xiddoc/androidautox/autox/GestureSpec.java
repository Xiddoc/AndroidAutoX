package com.xiddoc.androidautox.autox;

import java.util.Objects;

/**
 * Immutable value object modelling a native input gesture to dispatch to a
 * target virtual display.
 *
 * <p>AndroidAutoX prefers native Android input injection (via
 * {@link android.view.InputEvent} / {@link android.hardware.input.InputManager})
 * over shell-string approaches because native injection is lower-latency, more
 * accurate, and does not depend on the {@code input} command-line tool's availability.
 * A {@code GestureSpec} carries all the parameters needed to construct and inject a
 * {@link android.view.MotionEvent}: the target display, the gesture kind, and the
 * coordinate / timing data.
 *
 * <p>Two gesture kinds are supported:
 * <ul>
 *   <li>{@link Kind#TAP} — a single-point press-and-release at {@code (x, y)}.</li>
 *   <li>{@link Kind#SWIPE} — a linear drag from {@code (x1, y1)} to
 *       {@code (x2, y2)} over {@code durationMs} milliseconds.</li>
 * </ul>
 *
 * <p>Use the static factory methods {@link #tap} and {@link #swipe} to create
 * instances. The constructor validates all invariants eagerly.
 *
 * <p>This class has no Android framework imports — it is fully unit-testable with
 * plain JUnit on the JVM.
 */
public final class GestureSpec {

    /**
     * The kind of gesture this spec represents.
     */
    public enum Kind {
        /** A single-point tap (press + release). */
        TAP,
        /** A linear drag from one point to another over a given duration. */
        SWIPE
    }

    private final Kind kind;
    private final int displayId;
    private final float x1;
    private final float y1;
    private final float x2;
    private final float y2;
    private final long durationMs;

    private GestureSpec(Kind kind, int displayId,
                        float x1, float y1,
                        float x2, float y2,
                        long durationMs) {
        this.kind = kind;
        this.displayId = displayId;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.durationMs = durationMs;
    }

    // ------------------------------------------------------------------
    // Static factories
    // ------------------------------------------------------------------

    /**
     * Creates a {@link Kind#TAP} gesture spec targeting the given display.
     *
     * @param displayId the ID of the virtual display to receive the tap;
     *                  must be &ge; 0
     * @param x         tap x coordinate in virtual-display pixel space
     * @param y         tap y coordinate in virtual-display pixel space
     * @return a new immutable {@code GestureSpec} of kind {@link Kind#TAP}
     * @throws IllegalArgumentException if {@code displayId} is &lt; 0
     */
    public static GestureSpec tap(int displayId, float x, float y) {
        if (displayId < 0) {
            throw new IllegalArgumentException(
                    "displayId must be >= 0, got: " + displayId);
        }
        return new GestureSpec(Kind.TAP, displayId, x, y, x, y, 0L);
    }

    /**
     * Creates a {@link Kind#SWIPE} gesture spec targeting the given display.
     *
     * @param displayId  the ID of the virtual display to receive the swipe;
     *                   must be &ge; 0
     * @param x1         start x coordinate in virtual-display pixel space
     * @param y1         start y coordinate in virtual-display pixel space
     * @param x2         end x coordinate in virtual-display pixel space
     * @param y2         end y coordinate in virtual-display pixel space
     * @param durationMs duration of the swipe gesture in milliseconds; must be &gt; 0
     * @return a new immutable {@code GestureSpec} of kind {@link Kind#SWIPE}
     * @throws IllegalArgumentException if {@code displayId} is &lt; 0 or
     *                                  {@code durationMs} is &le; 0
     */
    public static GestureSpec swipe(int displayId,
                                    float x1, float y1,
                                    float x2, float y2,
                                    long durationMs) {
        if (displayId < 0) {
            throw new IllegalArgumentException(
                    "displayId must be >= 0, got: " + displayId);
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException(
                    "durationMs must be > 0, got: " + durationMs);
        }
        return new GestureSpec(Kind.SWIPE, displayId, x1, y1, x2, y2, durationMs);
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    /**
     * Returns the kind of this gesture ({@link Kind#TAP} or {@link Kind#SWIPE}).
     *
     * @return gesture kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Returns the ID of the virtual display that should receive this gesture.
     *
     * @return display ID (&ge; 0)
     */
    public int getDisplayId() {
        return displayId;
    }

    /**
     * Returns the x coordinate of the touch point (for {@link Kind#TAP}) or the
     * start x coordinate (for {@link Kind#SWIPE}).
     *
     * @return x1 coordinate
     */
    public float getX1() {
        return x1;
    }

    /**
     * Returns the y coordinate of the touch point (for {@link Kind#TAP}) or the
     * start y coordinate (for {@link Kind#SWIPE}).
     *
     * @return y1 coordinate
     */
    public float getY1() {
        return y1;
    }

    /**
     * Returns the end x coordinate of a {@link Kind#SWIPE} gesture. For
     * {@link Kind#TAP} this returns the same value as {@link #getX1()}.
     *
     * @return x2 coordinate
     */
    public float getX2() {
        return x2;
    }

    /**
     * Returns the end y coordinate of a {@link Kind#SWIPE} gesture. For
     * {@link Kind#TAP} this returns the same value as {@link #getY1()}.
     *
     * @return y2 coordinate
     */
    public float getY2() {
        return y2;
    }

    /**
     * Returns the duration of a {@link Kind#SWIPE} gesture in milliseconds. For
     * {@link Kind#TAP} this always returns {@code 0}.
     *
     * @return duration in milliseconds
     */
    public long getDurationMs() {
        return durationMs;
    }

    // ------------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------------

    /**
     * Two {@code GestureSpec} instances are equal when every field — kind,
     * displayId, x1, y1, x2, y2, and durationMs — is identical.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GestureSpec)) return false;
        GestureSpec other = (GestureSpec) o;
        return kind == other.kind
                && displayId == other.displayId
                && Float.compare(x1, other.x1) == 0
                && Float.compare(y1, other.y1) == 0
                && Float.compare(x2, other.x2) == 0
                && Float.compare(y2, other.y2) == 0
                && durationMs == other.durationMs;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(kind, displayId, x1, y1, x2, y2, durationMs);
    }

    /**
     * Returns a human-readable representation, e.g.:
     * <pre>
     *   GestureSpec{kind=TAP, displayId=1, x1=100.0, y1=200.0}
     *   GestureSpec{kind=SWIPE, displayId=1, x1=0.0, y1=0.0, x2=500.0, y2=0.0, durationMs=300}
     * </pre>
     */
    @Override
    public String toString() {
        if (kind == Kind.TAP) {
            return "GestureSpec{kind=TAP"
                    + ", displayId=" + displayId
                    + ", x1=" + x1
                    + ", y1=" + y1
                    + '}';
        }
        return "GestureSpec{kind=SWIPE"
                + ", displayId=" + displayId
                + ", x1=" + x1
                + ", y1=" + y1
                + ", x2=" + x2
                + ", y2=" + y2
                + ", durationMs=" + durationMs
                + '}';
    }
}

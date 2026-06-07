package com.xiddoc.androidautox.autox;

import java.util.Objects;

/**
 * Pure, framework-free routing logic that turns raw car-digitizer gesture inputs into
 * {@link GestureSpec} objects targeting the AutoX virtual display.
 *
 * <p>The Android Auto host reports touch events ({@code onClick}, {@code onScroll},
 * {@code onFling}) in the <em>car-surface</em> coordinate space. The guest app runs on a
 * {@link android.hardware.display.VirtualDisplay} that may have a different resolution. This
 * class scales the car-space inputs into virtual-display space using a
 * {@link CoordinateTranslator} built from {@code (carSpec, virtSpec)} so that the injected
 * gestures land at the correct pixel on the virtual canvas.
 *
 * <p><b>Identity note:</b> today the virtual display is created at the car's resolution, so
 * {@code virtSpec == carSpec} and the scaling is an identity map at runtime. The wiring is
 * still kept correct so it works when they differ (e.g. forced-vertical layouts where the
 * virtual display has a portrait geometry while the car surface is landscape).
 *
 * <p>All arithmetic is done here (centre/endpoint/velocity-projection) so it is unit-testable
 * with plain JUnit on the JVM. {@link AutoXScreen} is a thin caller of these methods.
 */
public final class TouchRouter {

    private TouchRouter() {
    }

    /**
     * Routes a tap from car-surface space to the virtual display.
     *
     * @param carSpec   geometry of the car head-unit surface (digitizer space)
     * @param virtSpec  geometry of the virtual display (target space)
     * @param displayId the virtual display id to receive the tap; must be &ge; 0
     * @param carX      tap x coordinate in car-surface pixels
     * @param carY      tap y coordinate in car-surface pixels
     * @return a {@link GestureSpec.Kind#TAP} spec at the translated, clamped point
     * @throws NullPointerException     if either spec is {@code null}
     * @throws IllegalArgumentException if {@code displayId} is &lt; 0
     */
    public static GestureSpec routeTap(AutoXDisplaySpec carSpec, AutoXDisplaySpec virtSpec,
                                       int displayId, float carX, float carY) {
        Objects.requireNonNull(carSpec, "carSpec");
        Objects.requireNonNull(virtSpec, "virtSpec");
        CoordinateTranslator translator = new CoordinateTranslator(carSpec, virtSpec);
        CoordinateTranslator.TranslatedPoint pt = translator.translate(carX, carY);
        return GestureSpec.tap(displayId, pt.getX(), pt.getY());
    }

    /**
     * Routes a scroll from car-surface space to the virtual display as a swipe.
     *
     * <p>The swipe starts at the centre of the <em>virtual</em> display and ends at the
     * start minus the scroll distance (scaled from car space into virtual space). Both
     * endpoints are clamped to the virtual-display bounds.
     *
     * @param carSpec    geometry of the car head-unit surface
     * @param virtSpec   geometry of the virtual display
     * @param displayId  the virtual display id; must be &ge; 0
     * @param distanceX  horizontal scroll distance in car-surface pixels
     * @param distanceY  vertical scroll distance in car-surface pixels
     * @param durationMs swipe duration in milliseconds; must be &gt; 0
     * @return a {@link GestureSpec.Kind#SWIPE} spec
     * @throws NullPointerException     if either spec is {@code null}
     * @throws IllegalArgumentException if {@code displayId} is &lt; 0 or {@code durationMs} &le; 0
     */
    public static GestureSpec routeScroll(AutoXDisplaySpec carSpec, AutoXDisplaySpec virtSpec,
                                          int displayId, float distanceX, float distanceY,
                                          long durationMs) {
        Objects.requireNonNull(carSpec, "carSpec");
        Objects.requireNonNull(virtSpec, "virtSpec");
        CoordinateTranslator translator = new CoordinateTranslator(carSpec, virtSpec);
        // Scale the car-space scroll distance into virtual-display space.
        double scaleX = (double) virtSpec.getWidth() / carSpec.getWidth();
        double scaleY = (double) virtSpec.getHeight() / carSpec.getHeight();
        float startX = virtSpec.getWidth() / 2.0f;
        float startY = virtSpec.getHeight() / 2.0f;
        float endX = (float) (startX - distanceX * scaleX);
        float endY = (float) (startY - distanceY * scaleY);
        return swipeBetween(translator, displayId, startX, startY, endX, endY, durationMs);
    }

    /**
     * Routes a fling from car-surface space to the virtual display as a swipe.
     *
     * <p>The swipe starts at the centre of the <em>virtual</em> display; the fling velocity
     * (car pixels per second) is projected over {@code durationMs} and scaled into virtual
     * space to form the end point. Both endpoints are clamped to the virtual-display bounds.
     *
     * @param carSpec    geometry of the car head-unit surface
     * @param virtSpec   geometry of the virtual display
     * @param displayId  the virtual display id; must be &ge; 0
     * @param velocityX  horizontal fling velocity in car pixels per second
     * @param velocityY  vertical fling velocity in car pixels per second
     * @param durationMs swipe duration in milliseconds; must be &gt; 0
     * @return a {@link GestureSpec.Kind#SWIPE} spec
     * @throws NullPointerException     if either spec is {@code null}
     * @throws IllegalArgumentException if {@code displayId} is &lt; 0 or {@code durationMs} &le; 0
     */
    public static GestureSpec routeFling(AutoXDisplaySpec carSpec, AutoXDisplaySpec virtSpec,
                                         int displayId, float velocityX, float velocityY,
                                         long durationMs) {
        Objects.requireNonNull(carSpec, "carSpec");
        Objects.requireNonNull(virtSpec, "virtSpec");
        CoordinateTranslator translator = new CoordinateTranslator(carSpec, virtSpec);
        double scaleX = (double) virtSpec.getWidth() / carSpec.getWidth();
        double scaleY = (double) virtSpec.getHeight() / carSpec.getHeight();
        float seconds = durationMs / 1000.0f;
        float startX = virtSpec.getWidth() / 2.0f;
        float startY = virtSpec.getHeight() / 2.0f;
        // Project the (car-space) velocity over the swipe duration, then scale to virt space.
        float endX = (float) (startX + velocityX * seconds * scaleX);
        float endY = (float) (startY + velocityY * seconds * scaleY);
        return swipeBetween(translator, displayId, startX, startY, endX, endY, durationMs);
    }

    /**
     * Builds a swipe spec from two virtual-space endpoints, clamping each to the
     * virtual-display bounds via the translator (which is fed virtual-space inputs at a
     * 1:1 ratio because the points are already in virtual space — clamping only).
     */
    private static GestureSpec swipeBetween(CoordinateTranslator translator, int displayId,
                                            float startX, float startY,
                                            float endX, float endY, long durationMs) {
        CoordinateTranslator.TranslatedPoint start = translator.clamp(startX, startY);
        CoordinateTranslator.TranslatedPoint end = translator.clamp(endX, endY);
        return GestureSpec.swipe(displayId, start.getX(), start.getY(),
                end.getX(), end.getY(), durationMs);
    }
}

package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link TouchRouter} — no Android runtime required.
 *
 * <p>Covers tap/scroll/fling routing with both identity and a deliberately NON-SQUARE
 * car→virt map (car 100x200 → virt 200x100, so scaleX=2 and scaleY=0.5). The non-square
 * cases would fail if the X and Y axes were ever swapped. Also covers endpoint arithmetic
 * (scroll subtracts distance, fling projects velocity over the duration), clamping at the
 * virtual-display bounds, and the null-spec / invalid-arg validation branches.
 */
public class TouchRouterTest {

    private static final float DELTA = 0.0001f;

    private static final AutoXDisplaySpec CAR_NONSQUARE  = new AutoXDisplaySpec(100, 200, 160);
    private static final AutoXDisplaySpec VIRT_NONSQUARE = new AutoXDisplaySpec(200, 100, 160);

    private static final AutoXDisplaySpec SQUARE = new AutoXDisplaySpec(100, 100, 160);

    // ------------------------------------------------------------------
    // routeTap
    // ------------------------------------------------------------------

    @Test
    public void routeTap_identity_passesThrough() {
        GestureSpec g = TouchRouter.routeTap(SQUARE, SQUARE, 1, 40f, 60f);
        assertEquals(GestureSpec.Kind.TAP, g.getKind());
        assertEquals(1, g.getDisplayId());
        assertEquals(40f, g.getX1(), DELTA);
        assertEquals(60f, g.getY1(), DELTA);
    }

    @Test
    public void routeTap_nonSquare_scalesAxesIndependently() {
        // car 100x200 → virt 200x100; scaleX=2, scaleY=0.5.
        GestureSpec g = TouchRouter.routeTap(CAR_NONSQUARE, VIRT_NONSQUARE, 1, 50f, 50f);
        assertEquals(100f, g.getX1(), DELTA); // 50 * 2
        assertEquals(25f,  g.getY1(), DELTA); // 50 * 0.5
    }

    @Test
    public void routeTap_clampsToVirtBounds() {
        // Far-out car coord clamps to the virtual canvas bounds (200x100).
        GestureSpec g = TouchRouter.routeTap(CAR_NONSQUARE, VIRT_NONSQUARE, 1, 9999f, 9999f);
        assertEquals(200f, g.getX1(), DELTA);
        assertEquals(100f, g.getY1(), DELTA);
    }

    @Test(expected = NullPointerException.class)
    public void routeTap_nullCarSpec_throws() {
        TouchRouter.routeTap(null, SQUARE, 1, 0f, 0f);
    }

    @Test(expected = NullPointerException.class)
    public void routeTap_nullVirtSpec_throws() {
        TouchRouter.routeTap(SQUARE, null, 1, 0f, 0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void routeTap_negativeDisplayId_throws() {
        TouchRouter.routeTap(SQUARE, SQUARE, -1, 0f, 0f);
    }

    // ------------------------------------------------------------------
    // routeScroll
    // ------------------------------------------------------------------

    @Test
    public void routeScroll_identity_startsAtCenter_endsAtCenterMinusDistance() {
        // virt 100x100 → center (50,50). distance (10, 20) → end (40, 30).
        GestureSpec g = TouchRouter.routeScroll(SQUARE, SQUARE, 1, 10f, 20f, 200L);
        assertEquals(GestureSpec.Kind.SWIPE, g.getKind());
        assertEquals(50f, g.getX1(), DELTA);
        assertEquals(50f, g.getY1(), DELTA);
        assertEquals(40f, g.getX2(), DELTA);
        assertEquals(30f, g.getY2(), DELTA);
        assertEquals(200L, g.getDurationMs());
    }

    @Test
    public void routeScroll_nonSquare_scalesDistanceIndependently() {
        // virt 200x100 → center (100,50). scaleX=2, scaleY=0.5.
        // endX = 100 - 10*2 = 80; endY = 50 - 20*0.5 = 40.
        GestureSpec g = TouchRouter.routeScroll(CAR_NONSQUARE, VIRT_NONSQUARE, 2, 10f, 20f, 150L);
        assertEquals(100f, g.getX1(), DELTA);
        assertEquals(50f,  g.getY1(), DELTA);
        assertEquals(80f,  g.getX2(), DELTA);
        assertEquals(40f,  g.getY2(), DELTA);
        assertEquals(2, g.getDisplayId());
    }

    @Test
    public void routeScroll_endpointClampedToBounds() {
        // virt 100x100, center (50,50). Huge distances drive the endpoint out of bounds.
        // endX = 50 - 1000 = -950 → clamp 0; endY = 50 - (-1000) = 1050 → clamp 100.
        GestureSpec g = TouchRouter.routeScroll(SQUARE, SQUARE, 1, 1000f, -1000f, 200L);
        assertEquals(0f,   g.getX2(), DELTA);
        assertEquals(100f, g.getY2(), DELTA);
    }

    @Test(expected = NullPointerException.class)
    public void routeScroll_nullCarSpec_throws() {
        TouchRouter.routeScroll(null, SQUARE, 1, 0f, 0f, 200L);
    }

    @Test(expected = NullPointerException.class)
    public void routeScroll_nullVirtSpec_throws() {
        TouchRouter.routeScroll(SQUARE, null, 1, 0f, 0f, 200L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void routeScroll_negativeDisplayId_throws() {
        TouchRouter.routeScroll(SQUARE, SQUARE, -1, 0f, 0f, 200L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void routeScroll_zeroDuration_throws() {
        TouchRouter.routeScroll(SQUARE, SQUARE, 1, 0f, 0f, 0L);
    }

    // ------------------------------------------------------------------
    // routeFling
    // ------------------------------------------------------------------

    @Test
    public void routeFling_identity_projectsVelocityOverDuration() {
        // virt 100x100, center (50,50). duration 200ms → 0.2s.
        // endX = 50 + 50*0.2 = 60; endY = 50 + (-100)*0.2 = 30.
        GestureSpec g = TouchRouter.routeFling(SQUARE, SQUARE, 1, 50f, -100f, 200L);
        assertEquals(GestureSpec.Kind.SWIPE, g.getKind());
        assertEquals(50f, g.getX1(), DELTA);
        assertEquals(50f, g.getY1(), DELTA);
        assertEquals(60f, g.getX2(), DELTA);
        assertEquals(30f, g.getY2(), DELTA);
        assertEquals(200L, g.getDurationMs());
    }

    @Test
    public void routeFling_nonSquare_scalesVelocityIndependently() {
        // virt 200x100, center (100,50). scaleX=2, scaleY=0.5; 0.2s.
        // endX = 100 + 100*0.2*2 = 140; endY = 50 + (-200)*0.2*0.5 = 30.
        GestureSpec g = TouchRouter.routeFling(CAR_NONSQUARE, VIRT_NONSQUARE, 3, 100f, -200f, 200L);
        assertEquals(100f, g.getX1(), DELTA);
        assertEquals(50f,  g.getY1(), DELTA);
        assertEquals(140f, g.getX2(), DELTA);
        assertEquals(30f,  g.getY2(), DELTA);
        assertEquals(3, g.getDisplayId());
    }

    @Test
    public void routeFling_endpointClampedToBounds() {
        // virt 100x100, center (50,50). Huge velocity drives endpoint out of bounds.
        // endX = 50 + 10000*0.2 = 2050 → clamp 100; endY = 50 + (-10000)*0.2 = -1950 → clamp 0.
        GestureSpec g = TouchRouter.routeFling(SQUARE, SQUARE, 1, 10000f, -10000f, 200L);
        assertEquals(100f, g.getX2(), DELTA);
        assertEquals(0f,   g.getY2(), DELTA);
    }

    @Test(expected = NullPointerException.class)
    public void routeFling_nullCarSpec_throws() {
        TouchRouter.routeFling(null, SQUARE, 1, 0f, 0f, 200L);
    }

    @Test(expected = NullPointerException.class)
    public void routeFling_nullVirtSpec_throws() {
        TouchRouter.routeFling(SQUARE, null, 1, 0f, 0f, 200L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void routeFling_negativeDisplayId_throws() {
        TouchRouter.routeFling(SQUARE, SQUARE, -1, 0f, 0f, 200L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void routeFling_zeroDuration_throws() {
        TouchRouter.routeFling(SQUARE, SQUARE, 1, 0f, 0f, 0L);
    }
}

package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link GestureSpec} — no Android runtime required.
 *
 * <p>Covers: static factory methods ({@link GestureSpec#tap} and
 * {@link GestureSpec#swipe}), validation (negative displayId, non-positive
 * durationMs), getters, equals/hashCode contract, toString, and the
 * {@link GestureSpec.Kind} enum values.
 */
public class GestureSpecTest {

    private static final float DELTA = 0.0001f;

    // ------------------------------------------------------------------
    // Kind enum
    // ------------------------------------------------------------------

    @Test
    public void kindEnum_hasTapAndSwipe() {
        assertEquals(GestureSpec.Kind.TAP, GestureSpec.Kind.valueOf("TAP"));
        assertEquals(GestureSpec.Kind.SWIPE, GestureSpec.Kind.valueOf("SWIPE"));
    }

    // ------------------------------------------------------------------
    // tap() — construction and getters
    // ------------------------------------------------------------------

    @Test
    public void tap_storesDisplayIdAndCoordinates() {
        GestureSpec g = GestureSpec.tap(2, 100f, 200f);
        assertEquals(GestureSpec.Kind.TAP, g.getKind());
        assertEquals(2, g.getDisplayId());
        assertEquals(100f, g.getX1(), DELTA);
        assertEquals(200f, g.getY1(), DELTA);
    }

    @Test
    public void tap_x2AndY2EqualX1AndY1() {
        GestureSpec g = GestureSpec.tap(0, 50f, 75f);
        assertEquals(g.getX1(), g.getX2(), DELTA);
        assertEquals(g.getY1(), g.getY2(), DELTA);
    }

    @Test
    public void tap_durationMsIsZero() {
        GestureSpec g = GestureSpec.tap(1, 0f, 0f);
        assertEquals(0L, g.getDurationMs());
    }

    @Test
    public void tap_displayIdZero_isValid() {
        GestureSpec g = GestureSpec.tap(0, 10f, 20f);
        assertEquals(0, g.getDisplayId());
    }

    @Test
    public void tap_originCoordinates() {
        GestureSpec g = GestureSpec.tap(1, 0f, 0f);
        assertEquals(0f, g.getX1(), DELTA);
        assertEquals(0f, g.getY1(), DELTA);
    }

    // ------------------------------------------------------------------
    // tap() — validation
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void tap_negativeDisplayId_throws() {
        GestureSpec.tap(-1, 100f, 200f);
    }

    // ------------------------------------------------------------------
    // swipe() — construction and getters
    // ------------------------------------------------------------------

    @Test
    public void swipe_storesAllFields() {
        GestureSpec g = GestureSpec.swipe(3, 0f, 0f, 500f, 300f, 250L);
        assertEquals(GestureSpec.Kind.SWIPE, g.getKind());
        assertEquals(3, g.getDisplayId());
        assertEquals(0f,   g.getX1(), DELTA);
        assertEquals(0f,   g.getY1(), DELTA);
        assertEquals(500f, g.getX2(), DELTA);
        assertEquals(300f, g.getY2(), DELTA);
        assertEquals(250L, g.getDurationMs());
    }

    @Test
    public void swipe_displayIdZero_isValid() {
        GestureSpec g = GestureSpec.swipe(0, 10f, 20f, 30f, 40f, 100L);
        assertEquals(0, g.getDisplayId());
    }

    @Test
    public void swipe_durationOneMs_isValid() {
        GestureSpec g = GestureSpec.swipe(1, 0f, 0f, 100f, 0f, 1L);
        assertEquals(1L, g.getDurationMs());
    }

    @Test
    public void swipe_sameStartAndEndCoords_isValid() {
        // A zero-distance swipe is structurally valid (duration still > 0)
        GestureSpec g = GestureSpec.swipe(1, 50f, 50f, 50f, 50f, 10L);
        assertEquals(50f, g.getX2(), DELTA);
        assertEquals(50f, g.getY2(), DELTA);
    }

    // ------------------------------------------------------------------
    // swipe() — validation
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void swipe_negativeDisplayId_throws() {
        GestureSpec.swipe(-1, 0f, 0f, 100f, 100f, 200L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void swipe_zeroDurationMs_throws() {
        GestureSpec.swipe(1, 0f, 0f, 100f, 100f, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void swipe_negativeDurationMs_throws() {
        GestureSpec.swipe(1, 0f, 0f, 100f, 100f, -1L);
    }

    // ------------------------------------------------------------------
    // equals — reflexive
    // ------------------------------------------------------------------

    @Test
    public void tap_equals_reflexive() {
        GestureSpec g = GestureSpec.tap(1, 100f, 200f);
        assertTrue(g.equals(g));
    }

    @Test
    public void swipe_equals_reflexive() {
        GestureSpec g = GestureSpec.swipe(1, 0f, 0f, 100f, 200f, 300L);
        assertTrue(g.equals(g));
    }

    // ------------------------------------------------------------------
    // equals — symmetric
    // ------------------------------------------------------------------

    @Test
    public void tap_equals_symmetric_whenEqual() {
        GestureSpec a = GestureSpec.tap(1, 100f, 200f);
        GestureSpec b = GestureSpec.tap(1, 100f, 200f);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
    }

    @Test
    public void swipe_equals_symmetric_whenEqual() {
        GestureSpec a = GestureSpec.swipe(2, 10f, 20f, 30f, 40f, 500L);
        GestureSpec b = GestureSpec.swipe(2, 10f, 20f, 30f, 40f, 500L);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
    }

    // ------------------------------------------------------------------
    // equals — null and different type
    // ------------------------------------------------------------------

    @Test
    public void equals_null_returnsFalse() {
        GestureSpec g = GestureSpec.tap(1, 100f, 200f);
        assertFalse(g.equals(null));
    }

    @Test
    public void equals_differentType_returnsFalse() {
        GestureSpec g = GestureSpec.tap(1, 100f, 200f);
        assertFalse(g.equals("not a gesture"));
    }

    // ------------------------------------------------------------------
    // equals — each field differs
    // ------------------------------------------------------------------

    @Test
    public void equals_differentKind_returnsFalse() {
        GestureSpec tap   = GestureSpec.tap(1, 100f, 200f);
        GestureSpec swipe = GestureSpec.swipe(1, 100f, 200f, 100f, 200f, 50L);
        assertFalse(tap.equals(swipe));
    }

    @Test
    public void equals_differentDisplayId_returnsFalse() {
        GestureSpec a = GestureSpec.tap(1, 100f, 200f);
        GestureSpec b = GestureSpec.tap(2, 100f, 200f);
        assertFalse(a.equals(b));
    }

    @Test
    public void equals_differentX1_returnsFalse() {
        GestureSpec a = GestureSpec.tap(1, 100f, 200f);
        GestureSpec b = GestureSpec.tap(1, 101f, 200f);
        assertFalse(a.equals(b));
    }

    @Test
    public void equals_differentY1_returnsFalse() {
        GestureSpec a = GestureSpec.tap(1, 100f, 200f);
        GestureSpec b = GestureSpec.tap(1, 100f, 201f);
        assertFalse(a.equals(b));
    }

    @Test
    public void swipe_equals_differentX2_returnsFalse() {
        GestureSpec a = GestureSpec.swipe(1, 0f, 0f, 100f, 50f, 100L);
        GestureSpec b = GestureSpec.swipe(1, 0f, 0f, 101f, 50f, 100L);
        assertFalse(a.equals(b));
    }

    @Test
    public void swipe_equals_differentY2_returnsFalse() {
        GestureSpec a = GestureSpec.swipe(1, 0f, 0f, 100f, 50f, 100L);
        GestureSpec b = GestureSpec.swipe(1, 0f, 0f, 100f, 51f, 100L);
        assertFalse(a.equals(b));
    }

    @Test
    public void swipe_equals_differentDurationMs_returnsFalse() {
        GestureSpec a = GestureSpec.swipe(1, 0f, 0f, 100f, 50f, 100L);
        GestureSpec b = GestureSpec.swipe(1, 0f, 0f, 100f, 50f, 200L);
        assertFalse(a.equals(b));
    }

    // ------------------------------------------------------------------
    // hashCode
    // ------------------------------------------------------------------

    @Test
    public void hashCode_equalTaps_equalHash() {
        GestureSpec a = GestureSpec.tap(1, 100f, 200f);
        GestureSpec b = GestureSpec.tap(1, 100f, 200f);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCode_equalSwipes_equalHash() {
        GestureSpec a = GestureSpec.swipe(2, 10f, 20f, 30f, 40f, 500L);
        GestureSpec b = GestureSpec.swipe(2, 10f, 20f, 30f, 40f, 500L);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCode_differentDisplayId_likelyDifferentHash() {
        GestureSpec a = GestureSpec.tap(1, 100f, 200f);
        GestureSpec b = GestureSpec.tap(2, 100f, 200f);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    // ------------------------------------------------------------------
    // toString
    // ------------------------------------------------------------------

    @Test
    public void tap_toString_containsKindAndCoordinates() {
        GestureSpec g = GestureSpec.tap(1, 100f, 200f);
        String s = g.toString();
        assertTrue(s.contains("TAP"));
        assertTrue(s.contains("100.0"));
        assertTrue(s.contains("200.0"));
        assertTrue(s.startsWith("GestureSpec{"));
    }

    @Test
    public void swipe_toString_containsAllFields() {
        GestureSpec g = GestureSpec.swipe(2, 10f, 20f, 300f, 400f, 150L);
        String s = g.toString();
        assertTrue(s.contains("SWIPE"));
        assertTrue(s.contains("10.0"));
        assertTrue(s.contains("20.0"));
        assertTrue(s.contains("300.0"));
        assertTrue(s.contains("400.0"));
        assertTrue(s.contains("150"));
        assertTrue(s.startsWith("GestureSpec{"));
    }

    @Test
    public void tap_toString_doesNotContainX2Y2Separately() {
        // For TAP, the toString should not emit x2/y2/durationMs labels
        GestureSpec g = GestureSpec.tap(1, 100f, 200f);
        String s = g.toString();
        assertFalse(s.contains("x2="));
        assertFalse(s.contains("y2="));
        assertFalse(s.contains("durationMs="));
    }
}

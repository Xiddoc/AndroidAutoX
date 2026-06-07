package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link CoordinateTranslator} — no Android runtime required.
 *
 * <p>Covers: construction from {@link AutoXDisplaySpec} pairs and raw int dimensions,
 * validation (non-positive dimensions), translation at origin/far-corner/identity/
 * upscale/downscale, clamping above max and below 0, the {@link CoordinateTranslator.TranslatedPoint}
 * value object (equals/hashCode/toString), and the {@link #translate(float, float)}
 * convenience method.
 */
public class CoordinateTranslatorTest {

    private static final float DELTA = 0.0001f;

    // ------------------------------------------------------------------
    // Construction from AutoXDisplaySpec
    // ------------------------------------------------------------------

    @Test
    public void constructFromSpecs_identity() {
        AutoXDisplaySpec car  = new AutoXDisplaySpec(1280, 720, 160);
        AutoXDisplaySpec virt = new AutoXDisplaySpec(1280, 720, 160);
        CoordinateTranslator t = new CoordinateTranslator(car, virt);
        assertEquals(640f, t.translateX(640f), DELTA);
        assertEquals(360f, t.translateY(360f), DELTA);
    }

    @Test(expected = NullPointerException.class)
    public void constructFromSpecs_nullCarSpec_throws() {
        new CoordinateTranslator(null, new AutoXDisplaySpec(1280, 720, 160));
    }

    @Test(expected = NullPointerException.class)
    public void constructFromSpecs_nullVirtSpec_throws() {
        new CoordinateTranslator(new AutoXDisplaySpec(1280, 720, 160), null);
    }

    // ------------------------------------------------------------------
    // Construction from raw ints — validation
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void zeroCarWidth_throws() {
        new CoordinateTranslator(0, 720, 1280, 720);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeCarWidth_throws() {
        new CoordinateTranslator(-1, 720, 1280, 720);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroCarHeight_throws() {
        new CoordinateTranslator(1280, 0, 1280, 720);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeCarHeight_throws() {
        new CoordinateTranslator(1280, -1, 1280, 720);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroVirtWidth_throws() {
        new CoordinateTranslator(1280, 720, 0, 720);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeVirtWidth_throws() {
        new CoordinateTranslator(1280, 720, -1, 720);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroVirtHeight_throws() {
        new CoordinateTranslator(1280, 720, 1280, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeVirtHeight_throws() {
        new CoordinateTranslator(1280, 720, 1280, -1);
    }

    // ------------------------------------------------------------------
    // Identity mapping (same dimensions)
    // ------------------------------------------------------------------

    @Test
    public void identity_origin_mapsToOrigin() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 1280, 720);
        assertEquals(0f, t.translateX(0f), DELTA);
        assertEquals(0f, t.translateY(0f), DELTA);
    }

    @Test
    public void identity_farCorner_mapsToFarCorner() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 1280, 720);
        assertEquals(1280f, t.translateX(1280f), DELTA);
        assertEquals(720f, t.translateY(720f), DELTA);
    }

    @Test
    public void identity_midPoint_mapsStraightThrough() {
        CoordinateTranslator t = new CoordinateTranslator(1000, 500, 1000, 500);
        assertEquals(500f, t.translateX(500f), DELTA);
        assertEquals(250f, t.translateY(250f), DELTA);
    }

    // ------------------------------------------------------------------
    // Downscale (car > virt)
    // ------------------------------------------------------------------

    @Test
    public void downscale_halfResolution_halvesBothAxes() {
        // car 1280x720, virt 640x360 → scale=0.5
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 640, 360);
        assertEquals(320f, t.translateX(640f), DELTA);
        assertEquals(180f, t.translateY(360f), DELTA);
    }

    @Test
    public void downscale_origin_mapsToOrigin() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 640, 360);
        assertEquals(0f, t.translateX(0f), DELTA);
        assertEquals(0f, t.translateY(0f), DELTA);
    }

    // ------------------------------------------------------------------
    // Upscale (car < virt)
    // ------------------------------------------------------------------

    @Test
    public void upscale_doubleResolution_doublesBothAxes() {
        // car 640x360, virt 1280x720 → scale=2
        CoordinateTranslator t = new CoordinateTranslator(640, 360, 1280, 720);
        assertEquals(640f, t.translateX(320f), DELTA);
        assertEquals(360f, t.translateY(180f), DELTA);
    }

    @Test
    public void upscale_farCorner_mapsToFarCorner() {
        CoordinateTranslator t = new CoordinateTranslator(640, 360, 1280, 720);
        assertEquals(1280f, t.translateX(640f), DELTA);
        assertEquals(720f,  t.translateY(360f), DELTA);
    }

    // ------------------------------------------------------------------
    // Non-square aspect ratios (X and Y scales differ)
    // ------------------------------------------------------------------

    @Test
    public void nonSquare_xAndYScaleIndependently() {
        // carW=100, carH=200; virtW=200, virtH=100 → scaleX=2, scaleY=0.5
        CoordinateTranslator t = new CoordinateTranslator(100, 200, 200, 100);
        assertEquals(100f, t.translateX(50f), DELTA);   // 50 * 2 = 100
        assertEquals(25f,  t.translateY(50f), DELTA);   // 50 * 0.5 = 25
    }

    // ------------------------------------------------------------------
    // Clamping — below 0
    // ------------------------------------------------------------------

    @Test
    public void clampX_negativeInput_clampedToZero() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 1280, 720);
        assertEquals(0f, t.translateX(-10f), DELTA);
    }

    @Test
    public void clampY_negativeInput_clampedToZero() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 1280, 720);
        assertEquals(0f, t.translateY(-5f), DELTA);
    }

    // ------------------------------------------------------------------
    // Clamping — above max
    // ------------------------------------------------------------------

    @Test
    public void clampX_aboveCarWidth_clampedToVirtWidth() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 1280, 720);
        assertEquals(1280f, t.translateX(9999f), DELTA);
    }

    @Test
    public void clampY_aboveCarHeight_clampedToVirtHeight() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 1280, 720);
        assertEquals(720f, t.translateY(9999f), DELTA);
    }

    @Test
    public void clampX_exactlyAtCarWidth_notClamped() {
        // exactly at the edge — should map to virtWidth without clamping
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 640, 360);
        assertEquals(640f, t.translateX(1280f), DELTA);
    }

    @Test
    public void clampY_exactlyAtCarHeight_notClamped() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 640, 360);
        assertEquals(360f, t.translateY(720f), DELTA);
    }

    // ------------------------------------------------------------------
    // translate(x, y) convenience — returns TranslatedPoint
    // ------------------------------------------------------------------

    @Test
    public void translate_returnsCorrectPoint() {
        CoordinateTranslator t = new CoordinateTranslator(1000, 500, 2000, 1000);
        CoordinateTranslator.TranslatedPoint p = t.translate(250f, 125f);
        assertEquals(500f, p.getX(), DELTA);
        assertEquals(250f, p.getY(), DELTA);
    }

    @Test
    public void translate_origin_mapsToOrigin() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 1920, 1080);
        CoordinateTranslator.TranslatedPoint p = t.translate(0f, 0f);
        assertEquals(0f, p.getX(), DELTA);
        assertEquals(0f, p.getY(), DELTA);
    }

    @Test
    public void translate_clampsNegative() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 1280, 720);
        CoordinateTranslator.TranslatedPoint p = t.translate(-100f, -50f);
        assertEquals(0f, p.getX(), DELTA);
        assertEquals(0f, p.getY(), DELTA);
    }

    @Test
    public void translate_clampsAboveMax() {
        CoordinateTranslator t = new CoordinateTranslator(1280, 720, 1280, 720);
        CoordinateTranslator.TranslatedPoint p = t.translate(99999f, 99999f);
        assertEquals(1280f, p.getX(), DELTA);
        assertEquals(720f,  p.getY(), DELTA);
    }

    // ------------------------------------------------------------------
    // TranslatedPoint — equals
    // ------------------------------------------------------------------

    @Test
    public void translatedPoint_reflexive() {
        CoordinateTranslator.TranslatedPoint pt = new CoordinateTranslator.TranslatedPoint(1f, 2f);
        assertTrue(pt.equals(pt));
    }

    @Test
    public void translatedPoint_equals_symmetric_whenEqual() {
        CoordinateTranslator.TranslatedPoint a = new CoordinateTranslator.TranslatedPoint(3f, 4f);
        CoordinateTranslator.TranslatedPoint b = new CoordinateTranslator.TranslatedPoint(3f, 4f);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
    }

    @Test
    public void translatedPoint_equals_symmetric_whenNotEqual() {
        CoordinateTranslator.TranslatedPoint a = new CoordinateTranslator.TranslatedPoint(3f, 4f);
        CoordinateTranslator.TranslatedPoint b = new CoordinateTranslator.TranslatedPoint(5f, 4f);
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
    }

    @Test
    public void translatedPoint_equals_null_returnsFalse() {
        CoordinateTranslator.TranslatedPoint pt = new CoordinateTranslator.TranslatedPoint(1f, 2f);
        assertFalse(pt.equals(null));
    }

    @Test
    public void translatedPoint_equals_differentType_returnsFalse() {
        CoordinateTranslator.TranslatedPoint pt = new CoordinateTranslator.TranslatedPoint(1f, 2f);
        assertFalse(pt.equals("string"));
    }

    @Test
    public void translatedPoint_equals_differentX_returnsFalse() {
        CoordinateTranslator.TranslatedPoint a = new CoordinateTranslator.TranslatedPoint(1f, 2f);
        CoordinateTranslator.TranslatedPoint b = new CoordinateTranslator.TranslatedPoint(9f, 2f);
        assertFalse(a.equals(b));
    }

    @Test
    public void translatedPoint_equals_differentY_returnsFalse() {
        CoordinateTranslator.TranslatedPoint a = new CoordinateTranslator.TranslatedPoint(1f, 2f);
        CoordinateTranslator.TranslatedPoint b = new CoordinateTranslator.TranslatedPoint(1f, 9f);
        assertFalse(a.equals(b));
    }

    // ------------------------------------------------------------------
    // TranslatedPoint — hashCode
    // ------------------------------------------------------------------

    @Test
    public void translatedPoint_hashCode_equalObjects_equalHash() {
        CoordinateTranslator.TranslatedPoint a = new CoordinateTranslator.TranslatedPoint(3f, 4f);
        CoordinateTranslator.TranslatedPoint b = new CoordinateTranslator.TranslatedPoint(3f, 4f);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void translatedPoint_hashCode_differentX_likelyDifferentHash() {
        CoordinateTranslator.TranslatedPoint a = new CoordinateTranslator.TranslatedPoint(3f, 4f);
        CoordinateTranslator.TranslatedPoint b = new CoordinateTranslator.TranslatedPoint(5f, 4f);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    // ------------------------------------------------------------------
    // TranslatedPoint — toString
    // ------------------------------------------------------------------

    @Test
    public void translatedPoint_toString_containsCoordinates() {
        CoordinateTranslator.TranslatedPoint pt = new CoordinateTranslator.TranslatedPoint(12.5f, 34.0f);
        String s = pt.toString();
        assertTrue(s.contains("12.5"));
        assertTrue(s.contains("34.0"));
        assertTrue(s.startsWith("TranslatedPoint{"));
    }
}

package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link LaunchBoundsCalculator} — 100% line + branch coverage.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link LaunchBoundsCalculator.Bounds} value object (constructor, width/height,
 *       equals/hashCode/toString).</li>
 *   <li>{@link LaunchBoundsCalculator#forcedVertical} — portrait display (no shrink),
 *       square display (no shrink), landscape display (shrinks width), various aspect
 *       ratios, centering maths, and all validation failures.</li>
 *   <li>{@link LaunchBoundsCalculator#fullDisplay} — happy path + validation failures.</li>
 * </ul>
 */
public class LaunchBoundsCalculatorTest {

    // -----------------------------------------------------------------------
    // Bounds value object
    // -----------------------------------------------------------------------

    @Test
    public void bounds_widthAndHeight_computed() {
        LaunchBoundsCalculator.Bounds b = new LaunchBoundsCalculator.Bounds(10, 20, 50, 80);
        assertEquals(40, b.width());   // 50 - 10
        assertEquals(60, b.height());  // 80 - 20
    }

    @Test
    public void bounds_equals_reflexive() {
        LaunchBoundsCalculator.Bounds b = new LaunchBoundsCalculator.Bounds(0, 0, 100, 200);
        assertTrue(b.equals(b));
    }

    @Test
    public void bounds_equals_equalInstances() {
        LaunchBoundsCalculator.Bounds a = new LaunchBoundsCalculator.Bounds(1, 2, 3, 4);
        LaunchBoundsCalculator.Bounds b = new LaunchBoundsCalculator.Bounds(1, 2, 3, 4);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void bounds_equals_differentLeft() {
        LaunchBoundsCalculator.Bounds a = new LaunchBoundsCalculator.Bounds(0, 0, 100, 200);
        LaunchBoundsCalculator.Bounds b = new LaunchBoundsCalculator.Bounds(1, 0, 100, 200);
        assertNotEquals(a, b);
    }

    @Test
    public void bounds_equals_differentTop() {
        LaunchBoundsCalculator.Bounds a = new LaunchBoundsCalculator.Bounds(0, 0, 100, 200);
        LaunchBoundsCalculator.Bounds b = new LaunchBoundsCalculator.Bounds(0, 1, 100, 200);
        assertNotEquals(a, b);
    }

    @Test
    public void bounds_equals_differentRight() {
        LaunchBoundsCalculator.Bounds a = new LaunchBoundsCalculator.Bounds(0, 0, 100, 200);
        LaunchBoundsCalculator.Bounds b = new LaunchBoundsCalculator.Bounds(0, 0, 101, 200);
        assertNotEquals(a, b);
    }

    @Test
    public void bounds_equals_differentBottom() {
        LaunchBoundsCalculator.Bounds a = new LaunchBoundsCalculator.Bounds(0, 0, 100, 200);
        LaunchBoundsCalculator.Bounds b = new LaunchBoundsCalculator.Bounds(0, 0, 100, 201);
        assertNotEquals(a, b);
    }

    @Test
    public void bounds_equals_null_returnsFalse() {
        LaunchBoundsCalculator.Bounds a = new LaunchBoundsCalculator.Bounds(0, 0, 100, 200);
        //noinspection SimplifiableJUnitAssertion,ConstantConditions
        assertNotEquals(a, null);
    }

    @Test
    public void bounds_equals_differentType_returnsFalse() {
        LaunchBoundsCalculator.Bounds a = new LaunchBoundsCalculator.Bounds(0, 0, 100, 200);
        assertNotEquals(a, "string");
    }

    @Test
    public void bounds_toString_containsAllFields() {
        LaunchBoundsCalculator.Bounds b = new LaunchBoundsCalculator.Bounds(1, 2, 3, 4);
        String s = b.toString();
        assertTrue(s.contains("1"));
        assertTrue(s.contains("2"));
        assertTrue(s.contains("3"));
        assertTrue(s.contains("4"));
        assertTrue(s.startsWith("Bounds{"));
    }

    // -----------------------------------------------------------------------
    // forcedVertical — portrait display (height >= width): full area returned
    // -----------------------------------------------------------------------

    @Test
    public void forcedVertical_portraitDisplay_returnsFullArea() {
        // 1080 wide × 1920 tall — already portrait
        LaunchBoundsCalculator.Bounds b =
                LaunchBoundsCalculator.forcedVertical(1080, 1920, 240, 16.0 / 9.0);
        assertEquals(0, b.left);
        assertEquals(0, b.top);
        assertEquals(1080, b.right);
        assertEquals(1920, b.bottom);
    }

    @Test
    public void forcedVertical_squareDisplay_returnsFullArea() {
        // height == width: treated as portrait (>= condition)
        LaunchBoundsCalculator.Bounds b =
                LaunchBoundsCalculator.forcedVertical(600, 600, 160, 1.0);
        assertEquals(0, b.left);
        assertEquals(0, b.top);
        assertEquals(600, b.right);
        assertEquals(600, b.bottom);
    }

    @Test
    public void forcedVertical_tinyPortraitDisplay_returnsFullArea() {
        LaunchBoundsCalculator.Bounds b =
                LaunchBoundsCalculator.forcedVertical(1, 2, 1, 1.0);
        assertEquals(0, b.left);
        assertEquals(0, b.top);
        assertEquals(1, b.right);
        assertEquals(2, b.bottom);
    }

    // -----------------------------------------------------------------------
    // forcedVertical — landscape display: width shrunk and centred
    // -----------------------------------------------------------------------

    @Test
    public void forcedVertical_landscapeDisplay_aspectRatio1_shrinks() {
        // 1920 × 1080 landscape, aspect ratio = 1.0 (square window)
        // boundsWidth = floor(1080 / 1.0) = 1080
        // left = (1920 - 1080) / 2 = 420
        LaunchBoundsCalculator.Bounds b =
                LaunchBoundsCalculator.forcedVertical(1920, 1080, 240, 1.0);
        assertEquals(420, b.left);
        assertEquals(0, b.top);
        assertEquals(1500, b.right);  // 420 + 1080
        assertEquals(1080, b.bottom);
        assertEquals(1080, b.width());
        assertEquals(1080, b.height());
    }

    @Test
    public void forcedVertical_landscapeDisplay_aspect16_9_shrinks() {
        // 1920 × 1080 landscape, aspect 16/9 ≈ 1.778
        // boundsWidth = floor(1080 / (16/9)) = floor(1080 * 9 / 16) = floor(607.5) = 607
        // left = (1920 - 607) / 2 = 656
        int expectedWidth = (int) (1080 / (16.0 / 9.0)); // = 607
        int expectedLeft = (1920 - expectedWidth) / 2;
        LaunchBoundsCalculator.Bounds b =
                LaunchBoundsCalculator.forcedVertical(1920, 1080, 240, 16.0 / 9.0);
        assertEquals(expectedLeft, b.left);
        assertEquals(0, b.top);
        assertEquals(expectedLeft + expectedWidth, b.right);
        assertEquals(1080, b.bottom);
    }

    @Test
    public void forcedVertical_landscapeDisplay_verySmallAspectRatio_clampedToDisplayWidth() {
        // aspect ratio < 1 means boundsWidth would exceed displayWidth — must clamp.
        // 1920 × 1080, aspect = 0.5 → boundsWidth = floor(1080 / 0.5) = 2160 > 1920 → clamp to 1920
        LaunchBoundsCalculator.Bounds b =
                LaunchBoundsCalculator.forcedVertical(1920, 1080, 240, 0.5);
        assertEquals(0, b.left);
        assertEquals(1920, b.right);
        assertEquals(1920, b.width());
    }

    @Test
    public void forcedVertical_landscapeDisplay_exactAspect_centred() {
        // 800 wide × 400 tall, aspect = 1.0 → boundsWidth = 400
        // left = (800 - 400) / 2 = 200
        LaunchBoundsCalculator.Bounds b =
                LaunchBoundsCalculator.forcedVertical(800, 400, 160, 1.0);
        assertEquals(200, b.left);
        assertEquals(0, b.top);
        assertEquals(600, b.right);
        assertEquals(400, b.bottom);
    }

    @Test
    public void forcedVertical_oddWidthDifference_centeredWithIntegerDivision() {
        // 1001 wide × 500 tall, aspect = 1.0 → boundsWidth = 500
        // left = (1001 - 500) / 2 = 250 (integer division truncates)
        LaunchBoundsCalculator.Bounds b =
                LaunchBoundsCalculator.forcedVertical(1001, 500, 160, 1.0);
        assertEquals(250, b.left);
        assertEquals(750, b.right); // 250 + 500
    }

    // -----------------------------------------------------------------------
    // forcedVertical — validation failures
    // -----------------------------------------------------------------------

    @Test
    public void forcedVertical_zeroWidth_throws() {
        try {
            LaunchBoundsCalculator.forcedVertical(0, 1080, 240, 1.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("displayWidth"));
        }
    }

    @Test
    public void forcedVertical_negativeWidth_throws() {
        try {
            LaunchBoundsCalculator.forcedVertical(-1, 1080, 240, 1.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void forcedVertical_zeroHeight_throws() {
        try {
            LaunchBoundsCalculator.forcedVertical(1920, 0, 240, 1.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("displayHeight"));
        }
    }

    @Test
    public void forcedVertical_negativeHeight_throws() {
        try {
            LaunchBoundsCalculator.forcedVertical(1920, -5, 240, 1.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void forcedVertical_zeroDensity_throws() {
        try {
            LaunchBoundsCalculator.forcedVertical(1920, 1080, 0, 1.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("densityDpi"));
        }
    }

    @Test
    public void forcedVertical_negativeDensity_throws() {
        try {
            LaunchBoundsCalculator.forcedVertical(1920, 1080, -1, 1.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void forcedVertical_zeroAspectRatio_throws() {
        try {
            LaunchBoundsCalculator.forcedVertical(1920, 1080, 240, 0.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("targetAspectRatio"));
        }
    }

    @Test
    public void forcedVertical_negativeAspectRatio_throws() {
        try {
            LaunchBoundsCalculator.forcedVertical(1920, 1080, 240, -1.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void forcedVertical_nanAspectRatio_throws() {
        try {
            LaunchBoundsCalculator.forcedVertical(1920, 1080, 240, Double.NaN);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void forcedVertical_infiniteAspectRatio_throws() {
        try {
            LaunchBoundsCalculator.forcedVertical(1920, 1080, 240, Double.POSITIVE_INFINITY);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // fullDisplay — happy path
    // -----------------------------------------------------------------------

    @Test
    public void fullDisplay_returnsFullDisplayBounds() {
        LaunchBoundsCalculator.Bounds b =
                LaunchBoundsCalculator.fullDisplay(1920, 1080, 240);
        assertEquals(0, b.left);
        assertEquals(0, b.top);
        assertEquals(1920, b.right);
        assertEquals(1080, b.bottom);
        assertEquals(1920, b.width());
        assertEquals(1080, b.height());
    }

    @Test
    public void fullDisplay_minimalDimensions() {
        LaunchBoundsCalculator.Bounds b =
                LaunchBoundsCalculator.fullDisplay(1, 1, 1);
        assertEquals(new LaunchBoundsCalculator.Bounds(0, 0, 1, 1), b);
    }

    // -----------------------------------------------------------------------
    // fullDisplay — validation failures
    // -----------------------------------------------------------------------

    @Test
    public void fullDisplay_zeroWidth_throws() {
        try {
            LaunchBoundsCalculator.fullDisplay(0, 1080, 240);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("displayWidth"));
        }
    }

    @Test
    public void fullDisplay_negativeWidth_throws() {
        try {
            LaunchBoundsCalculator.fullDisplay(-1, 1080, 240);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void fullDisplay_zeroHeight_throws() {
        try {
            LaunchBoundsCalculator.fullDisplay(1920, 0, 240);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("displayHeight"));
        }
    }

    @Test
    public void fullDisplay_negativeHeight_throws() {
        try {
            LaunchBoundsCalculator.fullDisplay(1920, -1, 240);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void fullDisplay_zeroDensity_throws() {
        try {
            LaunchBoundsCalculator.fullDisplay(1920, 1080, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("densityDpi"));
        }
    }

    @Test
    public void fullDisplay_negativeDensity_throws() {
        try {
            LaunchBoundsCalculator.fullDisplay(1920, 1080, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}

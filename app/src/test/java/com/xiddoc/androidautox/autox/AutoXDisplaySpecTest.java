package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link AutoXDisplaySpec} — no Android runtime required.
 *
 * <p>Covers: construction + getters, validation (non-positive dimensions), equals/hashCode
 * contract (reflexive, symmetric, transitivity, null safety, different type, differing
 * fields), and toString.
 */
public class AutoXDisplaySpecTest {

    // ------------------------------------------------------------------
    // Construction and getters
    // ------------------------------------------------------------------

    @Test
    public void constructor_storesFields() {
        AutoXDisplaySpec spec = new AutoXDisplaySpec(1920, 1080, 240);
        assertEquals(1920, spec.getWidth());
        assertEquals(1080, spec.getHeight());
        assertEquals(240, spec.getDensityDpi());
    }

    @Test
    public void minimalValidSpec_widthOneHeightOneDpiOne() {
        AutoXDisplaySpec spec = new AutoXDisplaySpec(1, 1, 1);
        assertEquals(1, spec.getWidth());
        assertEquals(1, spec.getHeight());
        assertEquals(1, spec.getDensityDpi());
    }

    // ------------------------------------------------------------------
    // Validation — non-positive width
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void zeroWidth_throwsIllegalArgument() {
        new AutoXDisplaySpec(0, 1080, 240);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeWidth_throwsIllegalArgument() {
        new AutoXDisplaySpec(-1, 1080, 240);
    }

    // ------------------------------------------------------------------
    // Validation — non-positive height
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void zeroHeight_throwsIllegalArgument() {
        new AutoXDisplaySpec(1920, 0, 240);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeHeight_throwsIllegalArgument() {
        new AutoXDisplaySpec(1920, -5, 240);
    }

    // ------------------------------------------------------------------
    // Validation — non-positive densityDpi
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void zeroDensityDpi_throwsIllegalArgument() {
        new AutoXDisplaySpec(1920, 1080, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeDensityDpi_throwsIllegalArgument() {
        new AutoXDisplaySpec(1920, 1080, -1);
    }

    // ------------------------------------------------------------------
    // equals — reflexive
    // ------------------------------------------------------------------

    @Test
    public void equals_reflexive() {
        AutoXDisplaySpec spec = new AutoXDisplaySpec(800, 600, 160);
        assertTrue(spec.equals(spec));
    }

    // ------------------------------------------------------------------
    // equals — symmetric
    // ------------------------------------------------------------------

    @Test
    public void equals_symmetric_whenEqual() {
        AutoXDisplaySpec a = new AutoXDisplaySpec(800, 600, 160);
        AutoXDisplaySpec b = new AutoXDisplaySpec(800, 600, 160);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
    }

    @Test
    public void equals_symmetric_whenNotEqual() {
        AutoXDisplaySpec a = new AutoXDisplaySpec(800, 600, 160);
        AutoXDisplaySpec b = new AutoXDisplaySpec(1280, 720, 240);
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
    }

    // ------------------------------------------------------------------
    // equals — null and different type
    // ------------------------------------------------------------------

    @Test
    public void equals_null_returnsFalse() {
        AutoXDisplaySpec spec = new AutoXDisplaySpec(800, 600, 160);
        assertFalse(spec.equals(null));
    }

    @Test
    public void equals_differentType_returnsFalse() {
        AutoXDisplaySpec spec = new AutoXDisplaySpec(800, 600, 160);
        assertFalse(spec.equals("not a spec"));
    }

    // ------------------------------------------------------------------
    // equals — each field differs
    // ------------------------------------------------------------------

    @Test
    public void equals_differentWidth_returnsFalse() {
        AutoXDisplaySpec a = new AutoXDisplaySpec(800, 600, 160);
        AutoXDisplaySpec b = new AutoXDisplaySpec(801, 600, 160);
        assertFalse(a.equals(b));
    }

    @Test
    public void equals_differentHeight_returnsFalse() {
        AutoXDisplaySpec a = new AutoXDisplaySpec(800, 600, 160);
        AutoXDisplaySpec b = new AutoXDisplaySpec(800, 601, 160);
        assertFalse(a.equals(b));
    }

    @Test
    public void equals_differentDensityDpi_returnsFalse() {
        AutoXDisplaySpec a = new AutoXDisplaySpec(800, 600, 160);
        AutoXDisplaySpec b = new AutoXDisplaySpec(800, 600, 161);
        assertFalse(a.equals(b));
    }

    // ------------------------------------------------------------------
    // hashCode
    // ------------------------------------------------------------------

    @Test
    public void hashCode_equalObjects_equalHash() {
        AutoXDisplaySpec a = new AutoXDisplaySpec(800, 600, 160);
        AutoXDisplaySpec b = new AutoXDisplaySpec(800, 600, 160);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCode_differentWidth_likelyDifferentHash() {
        AutoXDisplaySpec a = new AutoXDisplaySpec(800, 600, 160);
        AutoXDisplaySpec b = new AutoXDisplaySpec(801, 600, 160);
        // Not guaranteed to differ by the contract, but these values do:
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    // ------------------------------------------------------------------
    // toString
    // ------------------------------------------------------------------

    @Test
    public void toString_containsAllFields() {
        AutoXDisplaySpec spec = new AutoXDisplaySpec(1920, 1080, 240);
        String s = spec.toString();
        assertTrue(s.contains("1920"));
        assertTrue(s.contains("1080"));
        assertTrue(s.contains("240"));
        assertTrue(s.startsWith("AutoXDisplaySpec{"));
    }
}

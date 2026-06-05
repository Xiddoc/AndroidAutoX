package com.xiddoc.androidautox.Utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link Version} dotted-number comparison. Covers the
 * constructor validation branches, {@code compareTo} ordering (including
 * unequal-length versions where the shorter is zero-padded), and {@code equals}.
 */
public class VersionTest {

    // --- constructor validation ------------------------------------------

    @Test
    public void ctor_storesVersionString() {
        assertEquals("1.2.3", new Version("1.2.3").get());
        assertEquals("10", new Version("10").get());
    }

    @Test
    public void ctor_nullThrows() {
        try {
            new Version(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Version can not be null", e.getMessage());
        }
    }

    @Test
    public void ctor_invalidFormatThrows() {
        for (String bad : new String[]{"", "1.", ".1", "1..2", "1.2a", "v1.2", "1-2"}) {
            try {
                new Version(bad);
                fail("expected IllegalArgumentException for: " + bad);
            } catch (IllegalArgumentException e) {
                assertEquals("Invalid version format", e.getMessage());
            }
        }
    }

    // --- compareTo --------------------------------------------------------

    @Test
    public void compareTo_null_returnsPositive() {
        assertEquals(1, new Version("1.0").compareTo(null));
    }

    @Test
    public void compareTo_lessThan() {
        assertTrue(new Version("1.0").compareTo(new Version("1.1")) < 0);
        assertTrue(new Version("1.9").compareTo(new Version("2.0")) < 0);
    }

    @Test
    public void compareTo_greaterThan() {
        assertTrue(new Version("2.0").compareTo(new Version("1.9")) > 0);
        assertTrue(new Version("1.10").compareTo(new Version("1.9")) > 0);
    }

    @Test
    public void compareTo_equal() {
        assertEquals(0, new Version("1.2.3").compareTo(new Version("1.2.3")));
    }

    @Test
    public void compareTo_unequalLength_shorterPaddedWithZeros() {
        // "1" vs "1.0.0" -> equal; "1" vs "1.0.1" -> less (this side padded).
        assertEquals(0, new Version("1").compareTo(new Version("1.0.0")));
        assertTrue(new Version("1").compareTo(new Version("1.0.1")) < 0);
        // The other side padded: "1.0.1" vs "1" -> greater.
        assertTrue(new Version("1.0.1").compareTo(new Version("1")) > 0);
    }

    @Test
    public void compareTo_unequalLength_extraZeroSegmentsAreEqual() {
        assertEquals(0, new Version("1.0").compareTo(new Version("1")));
        assertEquals(0, new Version("1").compareTo(new Version("1.0")));
    }

    // --- equals -----------------------------------------------------------

    @Test
    public void equals_sameInstance() {
        Version v = new Version("3.4");
        assertTrue(v.equals(v));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void equals_null_false() {
        assertFalse(new Version("1.0").equals(null));
    }

    @Test
    public void equals_differentClass_false() {
        assertFalse(new Version("1.0").equals("1.0"));
    }

    @Test
    public void equals_sameValue_true() {
        assertTrue(new Version("1.2.0").equals(new Version("1.2")));
        assertEquals(new Version("1.2"), new Version("1.2.0"));
    }

    @Test
    public void equals_differentValue_false() {
        assertNotEquals(new Version("1.2"), new Version("1.3"));
    }
}

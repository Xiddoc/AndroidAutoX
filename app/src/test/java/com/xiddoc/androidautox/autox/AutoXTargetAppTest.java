package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link AutoXTargetApp}. Covers:
 * <ul>
 *   <li>Valid construction (packageName + label stored verbatim).</li>
 *   <li>Label defaulting when label is {@code null} or blank.</li>
 *   <li>All validation failure paths ({@code null}, empty, no-dot, leading/trailing
 *       dot, doubled dot, numeric-leading segment, whitespace inside name).</li>
 *   <li>{@code equals}/{@code hashCode} contract: reflexive, symmetric, null,
 *       wrong type, and every distinguishing field.</li>
 *   <li>{@code toString} smoke-test for expected content.</li>
 * </ul>
 */
public class AutoXTargetAppTest {

    // ------------------------------------------------------------------
    // Valid construction
    // ------------------------------------------------------------------

    @Test
    public void constructor_storesPackageNameAndLabel() {
        AutoXTargetApp app = new AutoXTargetApp("com.example.app", "Example App");
        assertEquals("com.example.app", app.packageName);
        assertEquals("Example App", app.label);
    }

    @Test
    public void constructor_minimumValidPackageName_twoSegments() {
        AutoXTargetApp app = new AutoXTargetApp("a.b", "AB");
        assertEquals("a.b", app.packageName);
    }

    @Test
    public void constructor_threeSegmentPackageName() {
        AutoXTargetApp app = new AutoXTargetApp("com.google.android.youtube", "YouTube");
        assertEquals("com.google.android.youtube", app.packageName);
        assertEquals("YouTube", app.label);
    }

    // ------------------------------------------------------------------
    // Label defaulting
    // ------------------------------------------------------------------

    @Test
    public void constructor_nullLabel_defaultsToPackageName() {
        AutoXTargetApp app = new AutoXTargetApp("com.example.app", null);
        assertEquals("com.example.app", app.label);
    }

    @Test
    public void constructor_blankLabel_defaultsToPackageName() {
        AutoXTargetApp app = new AutoXTargetApp("com.example.app", "   ");
        assertEquals("com.example.app", app.label);
    }

    @Test
    public void constructor_emptyLabel_defaultsToPackageName() {
        AutoXTargetApp app = new AutoXTargetApp("com.example.app", "");
        assertEquals("com.example.app", app.label);
    }

    // ------------------------------------------------------------------
    // Validation: null / empty packageName
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullPackageName_throws() {
        new AutoXTargetApp(null, "label");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyPackageName_throws() {
        new AutoXTargetApp("", "label");
    }

    // ------------------------------------------------------------------
    // Validation: no dot
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void constructor_noDot_throws() {
        new AutoXTargetApp("singleidentifier", "label");
    }

    // ------------------------------------------------------------------
    // Validation: empty segments (leading, trailing, doubled dots)
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void constructor_leadingDot_throws() {
        new AutoXTargetApp(".com.example", "label");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_trailingDot_throws() {
        new AutoXTargetApp("com.example.", "label");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_doubledDot_throws() {
        new AutoXTargetApp("com..example", "label");
    }

    // ------------------------------------------------------------------
    // Validation: segment not starting with a valid Java identifier start
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void constructor_segmentStartsWithDigit_throws() {
        new AutoXTargetApp("com.123example", "label");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_segmentStartsWithHyphen_throws() {
        new AutoXTargetApp("com.-example", "label");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_segmentStartsWithSpace_throws() {
        // A space inside a package-name segment is not a valid Java identifier start.
        new AutoXTargetApp("com. example", "label");
    }

    // ------------------------------------------------------------------
    // equals — reflexive
    // ------------------------------------------------------------------

    @Test
    public void equals_reflexive() {
        AutoXTargetApp app = new AutoXTargetApp("com.foo.bar", "Foo");
        assertEquals(app, app);
    }

    // ------------------------------------------------------------------
    // equals — symmetric
    // ------------------------------------------------------------------

    @Test
    public void equals_symmetric_sameFields() {
        AutoXTargetApp a = new AutoXTargetApp("com.foo.bar", "Foo");
        AutoXTargetApp b = new AutoXTargetApp("com.foo.bar", "Foo");
        assertEquals(a, b);
        assertEquals(b, a);
    }

    // ------------------------------------------------------------------
    // equals — null and wrong type
    // ------------------------------------------------------------------

    @Test
    public void equals_null_returnsFalse() {
        AutoXTargetApp app = new AutoXTargetApp("com.foo.bar", "Foo");
        assertFalse(app.equals(null));
    }

    @Test
    public void equals_wrongType_returnsFalse() {
        AutoXTargetApp app = new AutoXTargetApp("com.foo.bar", "Foo");
        assertFalse(app.equals("com.foo.bar"));
    }

    // ------------------------------------------------------------------
    // equals — distinguishing fields
    // ------------------------------------------------------------------

    @Test
    public void equals_differentPackageName_returnsFalse() {
        AutoXTargetApp a = new AutoXTargetApp("com.foo.bar", "Foo");
        AutoXTargetApp b = new AutoXTargetApp("com.baz.bar", "Foo");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_differentLabel_returnsFalse() {
        AutoXTargetApp a = new AutoXTargetApp("com.foo.bar", "Alpha");
        AutoXTargetApp b = new AutoXTargetApp("com.foo.bar", "Beta");
        assertNotEquals(a, b);
    }

    // ------------------------------------------------------------------
    // hashCode — consistent with equals
    // ------------------------------------------------------------------

    @Test
    public void hashCode_equalObjects_sameHash() {
        AutoXTargetApp a = new AutoXTargetApp("com.foo.bar", "Foo");
        AutoXTargetApp b = new AutoXTargetApp("com.foo.bar", "Foo");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCode_differentPackageName_likelyDifferentHash() {
        // Not a strict contract, but a sanity check against a trivially broken impl.
        AutoXTargetApp a = new AutoXTargetApp("com.foo.bar", "Foo");
        AutoXTargetApp b = new AutoXTargetApp("com.baz.bar", "Foo");
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    // ------------------------------------------------------------------
    // toString
    // ------------------------------------------------------------------

    @Test
    public void toString_containsPackageNameAndLabel() {
        AutoXTargetApp app = new AutoXTargetApp("com.example.app", "Example");
        String s = app.toString();
        assertNotNull(s);
        assertTrue(s.contains("com.example.app"));
        assertTrue(s.contains("Example"));
    }

    @Test
    public void toString_defaultedLabel_containsPackageName() {
        AutoXTargetApp app = new AutoXTargetApp("com.example.app", null);
        String s = app.toString();
        assertTrue(s.contains("com.example.app"));
        // label defaults to packageName, so packageName appears at least twice
        assertEquals(2, countOccurrences(s, "com.example.app"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}

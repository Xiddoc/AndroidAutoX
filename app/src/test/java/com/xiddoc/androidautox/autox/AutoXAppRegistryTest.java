package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Plain-JUnit tests for {@link AutoXAppRegistry}. Covers:
 * <ul>
 *   <li>Well-known app constants (package name and label).</li>
 *   <li>{@link AutoXAppRegistry#defaults()} returns a non-empty, unmodifiable list
 *       containing every constant entry.</li>
 *   <li>{@link AutoXAppRegistry#isKnown(String)} — true for all entries, false for
 *       unknown / null.</li>
 *   <li>{@link AutoXAppRegistry#byPackage(String)} — returns the correct entry or
 *       {@code null} for unknown / null input.</li>
 *   <li>Unmodifiability: adding/removing from the returned list throws.</li>
 * </ul>
 */
public class AutoXAppRegistryTest {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    @Test
    public void instagram_hasExpectedFields() {
        assertEquals("com.instagram.android", AutoXAppRegistry.INSTAGRAM.packageName);
        assertEquals("Instagram", AutoXAppRegistry.INSTAGRAM.label);
    }

    @Test
    public void youtube_hasExpectedFields() {
        assertEquals("com.google.android.youtube", AutoXAppRegistry.YOUTUBE.packageName);
        assertEquals("YouTube", AutoXAppRegistry.YOUTUBE.label);
    }

    @Test
    public void googleMaps_hasExpectedFields() {
        assertEquals("com.google.android.apps.maps", AutoXAppRegistry.GOOGLE_MAPS.packageName);
        assertEquals("Google Maps", AutoXAppRegistry.GOOGLE_MAPS.label);
    }

    @Test
    public void claude_hasExpectedFields() {
        assertEquals("com.anthropic.claude", AutoXAppRegistry.CLAUDE.packageName);
        assertEquals("Claude", AutoXAppRegistry.CLAUDE.label);
    }

    // ------------------------------------------------------------------
    // defaults() — content
    // ------------------------------------------------------------------

    @Test
    public void defaults_isNotNull() {
        assertNotNull(AutoXAppRegistry.defaults());
    }

    @Test
    public void defaults_isNotEmpty() {
        assertFalse(AutoXAppRegistry.defaults().isEmpty());
    }

    @Test
    public void defaults_containsAllFourEntries() {
        List<AutoXTargetApp> list = AutoXAppRegistry.defaults();
        assertEquals(4, list.size());
    }

    @Test
    public void defaults_containsInstagram() {
        assertTrue(AutoXAppRegistry.defaults().contains(AutoXAppRegistry.INSTAGRAM));
    }

    @Test
    public void defaults_containsYoutube() {
        assertTrue(AutoXAppRegistry.defaults().contains(AutoXAppRegistry.YOUTUBE));
    }

    @Test
    public void defaults_containsGoogleMaps() {
        assertTrue(AutoXAppRegistry.defaults().contains(AutoXAppRegistry.GOOGLE_MAPS));
    }

    @Test
    public void defaults_containsClaude() {
        assertTrue(AutoXAppRegistry.defaults().contains(AutoXAppRegistry.CLAUDE));
    }

    // ------------------------------------------------------------------
    // defaults() — unmodifiability
    // ------------------------------------------------------------------

    @Test(expected = UnsupportedOperationException.class)
    public void defaults_add_throwsUnsupportedOperationException() {
        AutoXAppRegistry.defaults().add(new AutoXTargetApp("com.test.app", "Test"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void defaults_remove_throwsUnsupportedOperationException() {
        AutoXAppRegistry.defaults().remove(0);
    }

    // ------------------------------------------------------------------
    // isKnown — positive cases
    // ------------------------------------------------------------------

    @Test
    public void isKnown_instagram_true() {
        assertTrue(AutoXAppRegistry.isKnown("com.instagram.android"));
    }

    @Test
    public void isKnown_youtube_true() {
        assertTrue(AutoXAppRegistry.isKnown("com.google.android.youtube"));
    }

    @Test
    public void isKnown_googleMaps_true() {
        assertTrue(AutoXAppRegistry.isKnown("com.google.android.apps.maps"));
    }

    @Test
    public void isKnown_claude_true() {
        assertTrue(AutoXAppRegistry.isKnown("com.anthropic.claude"));
    }

    // ------------------------------------------------------------------
    // isKnown — negative cases
    // ------------------------------------------------------------------

    @Test
    public void isKnown_unknownPackage_false() {
        assertFalse(AutoXAppRegistry.isKnown("com.unknown.package"));
    }

    @Test
    public void isKnown_null_false() {
        assertFalse(AutoXAppRegistry.isKnown(null));
    }

    @Test
    public void isKnown_emptyString_false() {
        assertFalse(AutoXAppRegistry.isKnown(""));
    }

    @Test
    public void isKnown_caseInsensitiveMismatch_false() {
        // Lookup is case-sensitive; uppercase package name must NOT match.
        assertFalse(AutoXAppRegistry.isKnown("COM.INSTAGRAM.ANDROID"));
    }

    // ------------------------------------------------------------------
    // byPackage — positive cases
    // ------------------------------------------------------------------

    @Test
    public void byPackage_instagram_returnsInstagram() {
        AutoXTargetApp result = AutoXAppRegistry.byPackage("com.instagram.android");
        assertEquals(AutoXAppRegistry.INSTAGRAM, result);
    }

    @Test
    public void byPackage_youtube_returnsYoutube() {
        assertEquals(AutoXAppRegistry.YOUTUBE,
                AutoXAppRegistry.byPackage("com.google.android.youtube"));
    }

    @Test
    public void byPackage_googleMaps_returnsGoogleMaps() {
        assertEquals(AutoXAppRegistry.GOOGLE_MAPS,
                AutoXAppRegistry.byPackage("com.google.android.apps.maps"));
    }

    @Test
    public void byPackage_claude_returnsClaude() {
        assertEquals(AutoXAppRegistry.CLAUDE,
                AutoXAppRegistry.byPackage("com.anthropic.claude"));
    }

    // ------------------------------------------------------------------
    // byPackage — negative cases
    // ------------------------------------------------------------------

    @Test
    public void byPackage_unknown_returnsNull() {
        assertNull(AutoXAppRegistry.byPackage("com.not.registered"));
    }

    @Test
    public void byPackage_null_returnsNull() {
        assertNull(AutoXAppRegistry.byPackage(null));
    }

    // ------------------------------------------------------------------
    // defaults() — stable identity across calls
    // ------------------------------------------------------------------

    @Test
    public void defaults_calledTwice_returnsSameContent() {
        List<AutoXTargetApp> first = AutoXAppRegistry.defaults();
        List<AutoXTargetApp> second = AutoXAppRegistry.defaults();
        assertEquals(first, second);
    }
}

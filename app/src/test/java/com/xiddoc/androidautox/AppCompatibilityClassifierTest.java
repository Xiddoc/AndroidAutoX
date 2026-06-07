package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.xiddoc.androidautox.AppCompatibilityClassifier.Category;

import org.junit.Test;

import java.lang.reflect.Constructor;

/**
 * Plain-JUnit tests for the pure {@link AppCompatibilityClassifier}.
 */
public class AppCompatibilityClassifierTest {

    private static final String MIRROR_PKG = "ru.inceptive.screentwoauto";
    private static final String ARBITRARY_PKG = "com.anthropic.claude";

    // --- classify branches + precedence ---------------------------------

    @Test
    public void classify_declaresCarMetadata_isNativeAuto() {
        assertEquals(Category.NATIVE_AUTO, AppCompatibilityClassifier.classify(ARBITRARY_PKG, true));
    }

    @Test
    public void classify_mirrorPackageWithoutCarMetadata_isMirrorShim() {
        assertEquals(Category.MIRROR_SHIM, AppCompatibilityClassifier.classify(MIRROR_PKG, false));
    }

    @Test
    public void classify_arbitraryPackage_isNeedsBridge() {
        assertEquals(Category.NEEDS_BRIDGE, AppCompatibilityClassifier.classify(ARBITRARY_PKG, false));
    }

    @Test
    public void classify_carMetadataWins_overMirrorMembership() {
        // A mirror package that ALSO declares car metadata classifies as NATIVE_AUTO
        // (declared-metadata precedence beats mirror membership).
        assertEquals(Category.NATIVE_AUTO, AppCompatibilityClassifier.classify(MIRROR_PKG, true));
    }

    // --- hasCarMetadata boundary ----------------------------------------

    @Test
    public void hasCarMetadata_zero_isFalse() {
        assertFalse(AppCompatibilityClassifier.hasCarMetadata(0));
    }

    @Test
    public void hasCarMetadata_positive_isTrue() {
        assertTrue(AppCompatibilityClassifier.hasCarMetadata(7));
    }

    @Test
    public void hasCarMetadata_negative_isTrue() {
        assertTrue(AppCompatibilityClassifier.hasCarMetadata(-1));
    }

    // --- isKnownAa ------------------------------------------------------

    @Test
    public void isKnownAa_mirrorPackage_isTrue() {
        assertTrue(AppCompatibilityClassifier.isKnownAa(MIRROR_PKG));
    }

    @Test
    public void isKnownAa_arbitraryPackage_isFalse() {
        assertFalse(AppCompatibilityClassifier.isKnownAa(ARBITRARY_PKG));
    }

    // --- MIRROR_APPS set semantics --------------------------------------

    @Test
    public void mirrorApps_hasExactlyNineEntries() {
        assertEquals(9, AppCompatibilityClassifier.MIRROR_APPS.size());
    }

    @Test
    public void mirrorApps_isUnmodifiable() {
        try {
            AppCompatibilityClassifier.MIRROR_APPS.add("com.example.new");
            fail("MIRROR_APPS must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // --- coverage: private constructor ----------------------------------

    @Test
    public void privateConstructor_isInvocableForCoverage() throws Exception {
        Constructor<AppCompatibilityClassifier> ctor =
                AppCompatibilityClassifier.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        ctor.newInstance();
    }
}

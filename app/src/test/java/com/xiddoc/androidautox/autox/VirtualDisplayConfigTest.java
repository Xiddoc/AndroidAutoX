package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link VirtualDisplayConfig} — no Android runtime required.
 *
 * <p>Covers: named constants, every boolean combination of {@link VirtualDisplayConfig#flags},
 * the {@link VirtualDisplayConfig#defaultFlags()} convenience, and the display-name constant.
 * All 8 branches of the three {@code if} statements in {@link VirtualDisplayConfig#flags}
 * are exercised to satisfy the 100% branch-coverage requirement.
 */
public class VirtualDisplayConfigTest {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    @Test
    public void flagPublic_isOne() {
        assertEquals(1, VirtualDisplayConfig.FLAG_PUBLIC);
    }

    @Test
    public void flagOwnContentOnly_isEight() {
        assertEquals(8, VirtualDisplayConfig.FLAG_OWN_CONTENT_ONLY);
    }

    @Test
    public void flagTrusted_is1024() {
        // VIRTUAL_DISPLAY_FLAG_TRUSTED is a @hide constant with value 1 << 10 = 1024
        assertEquals(1024, VirtualDisplayConfig.FLAG_TRUSTED);
    }

    @Test
    public void displayName_isAutoXIsolatedCanvas() {
        assertEquals("AutoX_Isolated_Canvas", VirtualDisplayConfig.DISPLAY_NAME);
    }

    // ------------------------------------------------------------------
    // flags() — all 8 boolean combinations
    // ------------------------------------------------------------------

    @Test
    public void flags_allFalse_isZero() {
        assertEquals(0, VirtualDisplayConfig.flags(false, false, false));
    }

    @Test
    public void flags_publicOnly() {
        assertEquals(VirtualDisplayConfig.FLAG_PUBLIC,
                VirtualDisplayConfig.flags(true, false, false));
    }

    @Test
    public void flags_ownContentOnlyOnly() {
        assertEquals(VirtualDisplayConfig.FLAG_OWN_CONTENT_ONLY,
                VirtualDisplayConfig.flags(false, true, false));
    }

    @Test
    public void flags_trustedOnly() {
        assertEquals(VirtualDisplayConfig.FLAG_TRUSTED,
                VirtualDisplayConfig.flags(false, false, true));
    }

    @Test
    public void flags_publicAndOwnContentOnly() {
        int expected = VirtualDisplayConfig.FLAG_PUBLIC | VirtualDisplayConfig.FLAG_OWN_CONTENT_ONLY;
        assertEquals(expected, VirtualDisplayConfig.flags(true, true, false));
    }

    @Test
    public void flags_publicAndTrusted() {
        int expected = VirtualDisplayConfig.FLAG_PUBLIC | VirtualDisplayConfig.FLAG_TRUSTED;
        assertEquals(expected, VirtualDisplayConfig.flags(true, false, true));
    }

    @Test
    public void flags_ownContentOnlyAndTrusted() {
        int expected = VirtualDisplayConfig.FLAG_OWN_CONTENT_ONLY | VirtualDisplayConfig.FLAG_TRUSTED;
        assertEquals(expected, VirtualDisplayConfig.flags(false, true, true));
    }

    @Test
    public void flags_allTrue_isPublicOrOwnContentOnlyOrTrusted() {
        int expected = VirtualDisplayConfig.FLAG_PUBLIC
                | VirtualDisplayConfig.FLAG_OWN_CONTENT_ONLY
                | VirtualDisplayConfig.FLAG_TRUSTED;
        assertEquals(expected, VirtualDisplayConfig.flags(true, true, true));
    }

    // ------------------------------------------------------------------
    // defaultFlags()
    // ------------------------------------------------------------------

    @Test
    public void defaultFlags_equalsPublicOrOwnContentOnlyOrTrusted() {
        int expected = VirtualDisplayConfig.FLAG_PUBLIC
                | VirtualDisplayConfig.FLAG_OWN_CONTENT_ONLY
                | VirtualDisplayConfig.FLAG_TRUSTED;
        assertEquals(expected, VirtualDisplayConfig.defaultFlags());
    }

    @Test
    public void defaultFlags_includesPublicFlag() {
        assertTrue((VirtualDisplayConfig.defaultFlags() & VirtualDisplayConfig.FLAG_PUBLIC) != 0);
    }

    @Test
    public void defaultFlags_includesOwnContentOnlyFlag() {
        assertTrue((VirtualDisplayConfig.defaultFlags() & VirtualDisplayConfig.FLAG_OWN_CONTENT_ONLY) != 0);
    }

    @Test
    public void defaultFlags_includesTrustedFlag() {
        assertTrue((VirtualDisplayConfig.defaultFlags() & VirtualDisplayConfig.FLAG_TRUSTED) != 0);
    }

    @Test
    public void defaultFlags_matchesAllTrueFlags() {
        assertEquals(VirtualDisplayConfig.flags(true, true, true),
                VirtualDisplayConfig.defaultFlags());
    }
}

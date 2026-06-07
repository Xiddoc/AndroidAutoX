package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xiddoc.androidautox.autox.VirtualDisplayConfig;

import org.junit.Test;

/** Tests for the pure {@link TrustedFlagPolicy} flag math. */
public class TrustedFlagPolicyTest {

    @Test
    public void withTrusted_setsTheBit_fromZero() {
        int out = TrustedFlagPolicy.withTrusted(0);
        assertEquals(VirtualDisplayConfig.FLAG_TRUSTED, out);
        assertTrue(TrustedFlagPolicy.isTrusted(out));
    }

    @Test
    public void withTrusted_preservesOtherBits() {
        int base = VirtualDisplayConfig.FLAG_PUBLIC | VirtualDisplayConfig.FLAG_OWN_CONTENT_ONLY;
        int out = TrustedFlagPolicy.withTrusted(base);
        assertEquals(base | VirtualDisplayConfig.FLAG_TRUSTED, out);
        assertTrue(TrustedFlagPolicy.isTrusted(out));
    }

    @Test
    public void withTrusted_isIdempotentWhenAlreadyTrusted() {
        int already = VirtualDisplayConfig.FLAG_TRUSTED;
        assertEquals(already, TrustedFlagPolicy.withTrusted(already));
    }

    @Test
    public void isTrusted_falseWhenBitMissing() {
        assertFalse(TrustedFlagPolicy.isTrusted(0));
        assertFalse(TrustedFlagPolicy.isTrusted(VirtualDisplayConfig.FLAG_PUBLIC));
    }
}

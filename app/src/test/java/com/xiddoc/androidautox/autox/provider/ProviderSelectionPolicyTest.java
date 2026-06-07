package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.xiddoc.androidautox.autox.provider.ProviderSelectionPolicy.Decision;
import com.xiddoc.androidautox.autox.provider.ProviderSelectionPolicy.Provider;

import org.junit.Test;

/**
 * Exhaustive tests for {@link ProviderSelectionPolicy}. Covers every branch that changes
 * the outcome, both reason strings for ROOT_REFLECTION, all DEGRADED reasons, and the
 * null-guard.
 */
public class ProviderSelectionPolicyTest {

    private static ProviderCapabilities.Builder caps() {
        return ProviderCapabilities.builder();
    }

    @Test
    public void lsposedActive_winsRegardlessOfEverythingElse() {
        Decision d = ProviderSelectionPolicy.select(
                caps().lsposedModuleActive(true).build());
        assertEquals(Provider.LSPOSED, d.provider);
        assertTrue(d.reason.contains("LSPosed"));
    }

    @Test
    public void lsposedActive_evenWithNoOtherCapability() {
        // LSPosed active but trusted/injection false — still LSPOSED (hook relaxes them).
        Decision d = ProviderSelectionPolicy.select(
                caps().lsposedModuleActive(true)
                        .trustedDisplayHonored(false)
                        .inputInjectionHonored(false)
                        .build());
        assertEquals(Provider.LSPOSED, d.provider);
    }

    @Test
    public void noPrivilegedPath_degraded() {
        Decision d = ProviderSelectionPolicy.select(caps().build());
        assertEquals(Provider.DEGRADED, d.provider);
        assertTrue(d.reason.contains("No privileged path"));
    }

    @Test
    public void rootButTrustedNotHonored_degraded() {
        Decision d = ProviderSelectionPolicy.select(
                caps().rootAvailable(true)
                        .trustedDisplayHonored(false)
                        .inputInjectionHonored(true)
                        .build());
        assertEquals(Provider.DEGRADED, d.provider);
        assertTrue(d.reason.contains("TRUSTED"));
    }

    @Test
    public void rootTrustedButInjectionNotHonored_degraded() {
        Decision d = ProviderSelectionPolicy.select(
                caps().rootAvailable(true)
                        .trustedDisplayHonored(true)
                        .inputInjectionHonored(false)
                        .build());
        assertEquals(Provider.DEGRADED, d.provider);
        assertTrue(d.reason.contains("input injection"));
    }

    @Test
    public void rootFullyHonored_rootReflection_rootWording() {
        Decision d = ProviderSelectionPolicy.select(
                caps().rootAvailable(true)
                        .trustedDisplayHonored(true)
                        .inputInjectionHonored(true)
                        .build());
        assertEquals(Provider.ROOT_REFLECTION, d.provider);
        assertTrue(d.reason.contains("root reflection"));
    }

    @Test
    public void platformSignatureOnly_fullyHonored_rootReflection_signatureWording() {
        Decision d = ProviderSelectionPolicy.select(
                caps().platformSignature(true)   // root false -> signature wording branch
                        .trustedDisplayHonored(true)
                        .inputInjectionHonored(true)
                        .build());
        assertEquals(Provider.ROOT_REFLECTION, d.provider);
        assertTrue(d.reason.contains("platform-signature"));
    }

    @Test
    public void nullCaps_throws() {
        try {
            ProviderSelectionPolicy.select(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("caps"));
        }
    }

    @Test
    public void decision_equalsHashCodeToString() {
        Decision a = ProviderSelectionPolicy.select(caps().lsposedModuleActive(true).build());
        Decision b = ProviderSelectionPolicy.select(caps().lsposedModuleActive(true).build());
        Decision degraded = ProviderSelectionPolicy.select(caps().build());

        assertTrue(a.equals(a));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, degraded);                 // different provider
        assertFalse(a.equals(null));
        assertFalse(a.equals("x"));
        assertTrue(a.toString().contains("LSPOSED"));

        // same provider, different reason -> not equal
        Decision trustedFail = ProviderSelectionPolicy.select(
                caps().rootAvailable(true).inputInjectionHonored(true).build());
        Decision injectFail = ProviderSelectionPolicy.select(
                caps().rootAvailable(true).trustedDisplayHonored(true).build());
        assertEquals(Provider.DEGRADED, trustedFail.provider);
        assertEquals(Provider.DEGRADED, injectFail.provider);
        assertNotEquals(trustedFail, injectFail);
    }

    @Test
    public void providerEnum_isExercised() {
        assertEquals(Provider.LSPOSED, Provider.valueOf("LSPOSED"));
        assertEquals(3, Provider.values().length);
    }
}

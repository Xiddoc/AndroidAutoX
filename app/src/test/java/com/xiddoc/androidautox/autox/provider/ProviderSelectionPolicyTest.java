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
 * Exhaustive tests for the binary LSPosed-first {@link ProviderSelectionPolicy}. Covers every
 * branch that changes the outcome (LSPosed inactive, LSPosed active but trusted not honored,
 * LSPosed active but injection not honored, fully honored) and the null-guard.
 */
public class ProviderSelectionPolicyTest {

    private static ProviderCapabilities.Builder caps() {
        return ProviderCapabilities.builder();
    }

    /** A fully-honored LSPosed snapshot (the only LSPOSED outcome). */
    private static ProviderCapabilities.Builder lsposedHonored() {
        return caps().lsposedModuleActive(true)
                .trustedDisplayHonored(true)
                .inputInjectionHonored(true);
    }

    @Test
    public void lsposedInactive_blocked_reasonNamesLsposed() {
        Decision d = ProviderSelectionPolicy.select(caps().build());
        assertEquals(Provider.BLOCKED, d.provider);
        assertTrue(d.reason.contains("requires LSPosed"));
    }

    @Test
    public void lsposedInactive_blocked_evenWithRootAndHonoredFlags() {
        // Root + both flags honored but no LSPosed: still BLOCKED (root is not a provider path).
        Decision d = ProviderSelectionPolicy.select(
                caps().rootAvailable(true)
                        .platformSignature(true)
                        .trustedDisplayHonored(true)
                        .inputInjectionHonored(true)
                        .build());
        assertEquals(Provider.BLOCKED, d.provider);
        assertTrue(d.reason.contains("requires LSPosed"));
    }

    @Test
    public void lsposedActive_trustedNotHonored_blocked_namesTrustedHook() {
        Decision d = ProviderSelectionPolicy.select(
                caps().lsposedModuleActive(true)
                        .trustedDisplayHonored(false)
                        .inputInjectionHonored(true)
                        .build());
        assertEquals(Provider.BLOCKED, d.provider);
        assertTrue(d.reason.contains("trusted-display"));
    }

    @Test
    public void lsposedActive_injectionNotHonored_blocked_namesInjectionHook() {
        Decision d = ProviderSelectionPolicy.select(
                caps().lsposedModuleActive(true)
                        .trustedDisplayHonored(true)
                        .inputInjectionHonored(false)
                        .build());
        assertEquals(Provider.BLOCKED, d.provider);
        assertTrue(d.reason.contains("input-injection"));
    }

    @Test
    public void lsposedActive_bothHonored_lsposed() {
        Decision d = ProviderSelectionPolicy.select(lsposedHonored().build());
        assertEquals(Provider.LSPOSED, d.provider);
        assertTrue(d.reason.contains("LSPosed"));
    }

    /**
     * Locks in BLOCKER-1's fix: AutoXProviderFactory.probe(), when LSPosed is active, feeds the
     * two surface-time honored-flags OPTIMISTICALLY {@code true} (no surface exists yet, but
     * LSPosed is the privileged mechanism we trust until a device read proves otherwise). Running
     * those exact provisional inputs through CapabilityDecider + select(...) MUST resolve to
     * LSPOSED, not BLOCKED — otherwise the car surface would permanently show "requires LSPosed".
     */
    @Test
    public void provisionalLsposedInputs_resolveToLsposed() {
        // Mirror AutoXProviderFactory.probe()'s provisional inputs on an LSPosed-active device:
        //   lsposedActive = true; trusted/injection fed optimistically = lsposedActive (true);
        //   platformSigned / rootAvailable / settingsWritable do not affect the decision.
        boolean lsposedActive = true;
        ProviderCapabilities provisional = CapabilityDecider.decide(
                lsposedActive,
                /* platformSigned= */ false,
                /* rootAvailable= */ true,
                /* trustedDisplayHonored= */ lsposedActive,
                /* injectionHonored= */ lsposedActive,
                /* settingsWritable= */ true);
        Decision d = ProviderSelectionPolicy.select(provisional);
        assertEquals(Provider.LSPOSED, d.provider);
    }

    /**
     * Mirror image: with LSPosed inactive the factory's provisional inputs (optimistic flags
     * collapse to false) MUST resolve to BLOCKED with the "requires LSPosed" reason.
     */
    @Test
    public void provisionalNoLsposedInputs_resolveToBlocked() {
        boolean lsposedActive = false;
        ProviderCapabilities provisional = CapabilityDecider.decide(
                lsposedActive,
                /* platformSigned= */ false,
                /* rootAvailable= */ true,
                /* trustedDisplayHonored= */ lsposedActive,
                /* injectionHonored= */ lsposedActive,
                /* settingsWritable= */ true);
        Decision d = ProviderSelectionPolicy.select(provisional);
        assertEquals(Provider.BLOCKED, d.provider);
        assertTrue(d.reason.contains("requires LSPosed"));
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
        Decision a = ProviderSelectionPolicy.select(lsposedHonored().build());
        Decision b = ProviderSelectionPolicy.select(lsposedHonored().build());
        Decision blocked = ProviderSelectionPolicy.select(caps().build());

        assertTrue(a.equals(a));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, blocked);                 // different provider
        assertFalse(a.equals(null));
        assertFalse(a.equals("x"));
        assertTrue(a.toString().contains("LSPOSED"));

        // same provider (BLOCKED), different reason -> not equal
        Decision noLsposed = ProviderSelectionPolicy.select(caps().build());
        Decision trustedFail = ProviderSelectionPolicy.select(
                caps().lsposedModuleActive(true).inputInjectionHonored(true).build());
        Decision injectFail = ProviderSelectionPolicy.select(
                caps().lsposedModuleActive(true).trustedDisplayHonored(true).build());
        assertEquals(Provider.BLOCKED, noLsposed.provider);
        assertEquals(Provider.BLOCKED, trustedFail.provider);
        assertEquals(Provider.BLOCKED, injectFail.provider);
        assertNotEquals(noLsposed, trustedFail);
        assertNotEquals(trustedFail, injectFail);
    }

    @Test
    public void providerEnum_isExercised() {
        assertEquals(Provider.LSPOSED, Provider.valueOf("LSPOSED"));
        assertEquals(Provider.BLOCKED, Provider.valueOf("BLOCKED"));
        assertEquals(2, Provider.values().length);
    }
}

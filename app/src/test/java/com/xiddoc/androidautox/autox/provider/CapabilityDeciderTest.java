package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Tests for the pure {@link CapabilityDecider}. */
public class CapabilityDeciderTest {

    @Test
    public void decide_mapsEverySignalThrough() {
        ProviderCapabilities c = CapabilityDecider.decide(
                true, true, true, true, true, true);
        assertTrue(c.lsposedModuleActive);
        assertTrue(c.platformSignature);
        assertTrue(c.rootAvailable);
        assertTrue(c.trustedDisplayHonored);
        assertTrue(c.inputInjectionHonored);
        assertTrue(c.secureSettingsWritable);

        ProviderCapabilities none = CapabilityDecider.decide(
                false, false, false, false, false, false);
        assertEquals(ProviderCapabilities.none(), none);
    }

    @Test
    public void decide_eachSignalMappedToCorrectField() {
        assertTrue(CapabilityDecider.decide(true, false, false, false, false, false)
                .lsposedModuleActive);
        assertTrue(CapabilityDecider.decide(false, true, false, false, false, false)
                .platformSignature);
        assertTrue(CapabilityDecider.decide(false, false, true, false, false, false)
                .rootAvailable);
        assertTrue(CapabilityDecider.decide(false, false, false, true, false, false)
                .trustedDisplayHonored);
        assertTrue(CapabilityDecider.decide(false, false, false, false, true, false)
                .inputInjectionHonored);
        assertTrue(CapabilityDecider.decide(false, false, false, false, false, true)
                .secureSettingsWritable);
    }

    @Test
    public void hasAnyPrivilegedPath_trueIfAnyOfLsposedRootSignature() {
        assertTrue(CapabilityDecider.hasAnyPrivilegedPath(
                ProviderCapabilities.builder().lsposedModuleActive(true).build()));
        assertTrue(CapabilityDecider.hasAnyPrivilegedPath(
                ProviderCapabilities.builder().rootAvailable(true).build()));
        assertTrue(CapabilityDecider.hasAnyPrivilegedPath(
                ProviderCapabilities.builder().platformSignature(true).build()));
        assertFalse(CapabilityDecider.hasAnyPrivilegedPath(ProviderCapabilities.none()));
        // a snapshot with only honored-flags but no path -> false
        assertFalse(CapabilityDecider.hasAnyPrivilegedPath(
                ProviderCapabilities.builder()
                        .trustedDisplayHonored(true)
                        .inputInjectionHonored(true)
                        .secureSettingsWritable(true)
                        .build()));
    }

    @Test
    public void hasAnyPrivilegedPath_nullThrows() {
        try {
            CapabilityDecider.hasAnyPrivilegedPath(null);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("caps"));
        }
    }
}

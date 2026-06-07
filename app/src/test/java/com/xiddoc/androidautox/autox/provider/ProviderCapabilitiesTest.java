package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tests for the {@link ProviderCapabilities} builder, defaults, equality and toString. */
public class ProviderCapabilitiesTest {

    @Test
    public void none_allFalse() {
        ProviderCapabilities c = ProviderCapabilities.none();
        assertFalse(c.lsposedModuleActive);
        assertFalse(c.platformSignature);
        assertFalse(c.rootAvailable);
        assertFalse(c.trustedDisplayHonored);
        assertFalse(c.inputInjectionHonored);
        assertFalse(c.secureSettingsWritable);
    }

    @Test
    public void builder_setsEveryField() {
        ProviderCapabilities c = ProviderCapabilities.builder()
                .lsposedModuleActive(true)
                .platformSignature(true)
                .rootAvailable(true)
                .trustedDisplayHonored(true)
                .inputInjectionHonored(true)
                .secureSettingsWritable(true)
                .build();
        assertTrue(c.lsposedModuleActive);
        assertTrue(c.platformSignature);
        assertTrue(c.rootAvailable);
        assertTrue(c.trustedDisplayHonored);
        assertTrue(c.inputInjectionHonored);
        assertTrue(c.secureSettingsWritable);
    }

    @Test
    public void equalsHashCode_reflexiveSymmetricAndPerField() {
        ProviderCapabilities base = ProviderCapabilities.none();
        assertTrue(base.equals(base));
        assertFalse(base.equals(null));
        assertFalse(base.equals("x"));

        ProviderCapabilities same = ProviderCapabilities.none();
        assertEquals(base, same);
        assertEquals(base.hashCode(), same.hashCode());

        // hashCode on an all-true object covers the TRUE side of each ternary (the
        // all-false `base` above covers the FALSE side).
        ProviderCapabilities allTrue = ProviderCapabilities.builder()
                .lsposedModuleActive(true).platformSignature(true).rootAvailable(true)
                .trustedDisplayHonored(true).inputInjectionHonored(true)
                .secureSettingsWritable(true).build();
        ProviderCapabilities allTrue2 = ProviderCapabilities.builder()
                .lsposedModuleActive(true).platformSignature(true).rootAvailable(true)
                .trustedDisplayHonored(true).inputInjectionHonored(true)
                .secureSettingsWritable(true).build();
        assertEquals(allTrue.hashCode(), allTrue2.hashCode());

        // Flip exactly ONE field while keeping every OTHER field equal to `all`. This forces
        // the &&-chain in equals() to evaluate up to (and fail at) that specific field,
        // covering each branch of the chain.
        ProviderCapabilities all = ProviderCapabilities.builder()
                .lsposedModuleActive(true).platformSignature(true).rootAvailable(true)
                .trustedDisplayHonored(true).inputInjectionHonored(true)
                .secureSettingsWritable(true).build();
        assertNotEquals(all, flip(all).lsposedModuleActive(false).build());
        assertNotEquals(all, flip(all).platformSignature(false).build());
        assertNotEquals(all, flip(all).rootAvailable(false).build());
        assertNotEquals(all, flip(all).trustedDisplayHonored(false).build());
        assertNotEquals(all, flip(all).inputInjectionHonored(false).build());
        assertNotEquals(all, flip(all).secureSettingsWritable(false).build());
    }

    /** Builder pre-set to all-true, so a single setter call differs in exactly one field. */
    private static ProviderCapabilities.Builder flip(ProviderCapabilities ignored) {
        return ProviderCapabilities.builder()
                .lsposedModuleActive(true).platformSignature(true).rootAvailable(true)
                .trustedDisplayHonored(true).inputInjectionHonored(true)
                .secureSettingsWritable(true);
    }

    @Test
    public void toString_listsAllFlags() {
        String s = ProviderCapabilities.builder().rootAvailable(true).build().toString();
        assertTrue(s.contains("lsposedModuleActive"));
        assertTrue(s.contains("platformSignature"));
        assertTrue(s.contains("rootAvailable"));
        assertTrue(s.contains("trustedDisplayHonored"));
        assertTrue(s.contains("inputInjectionHonored"));
        assertTrue(s.contains("secureSettingsWritable"));
    }
}

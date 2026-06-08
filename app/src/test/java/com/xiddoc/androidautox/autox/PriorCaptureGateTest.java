package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * Plain-JUnit tests for {@link PriorCaptureGate}, including the RECREATE / process-death
 * round-trip reasoning the gate exists to protect.
 */
public class PriorCaptureGateTest {

    @Test
    public void capturesWhenNotYetEnabled() {
        // Fresh session: AutoX not enabled → the genuine original must be captured.
        assertTrue(PriorCaptureGate.shouldCapturePrior(false));
    }

    @Test
    public void skipsCaptureWhenAlreadyEnabled() {
        // Re-entrant apply (resume / recreate): AutoX already enabled → must NOT re-capture,
        // otherwise it would persist AutoX's own written value as the "prior".
        assertFalse(PriorCaptureGate.shouldCapturePrior(true));
    }

    /**
     * Models the full capture→apply→release→revert round-trip across a simulated process death
     * to prove the genuine original survives.
     *
     * <p>Scenario: the device's true original value is 0. A fresh session captures 0, applies 1.
     * The process dies and {@code createDisplay} re-runs; the persisted enabled flag is still
     * true, so the gate forbids re-capture (which would have read back the applied 1 and
     * poisoned the prior). On release the revert restores the still-correct 0.
     */
    @Test
    public void recreateRoundTrip_preservesGenuineOriginal() {
        final int genuineOriginal = 0;
        final int autoxValue = 1;

        // --- Fresh apply: not enabled yet → capture the genuine original. ---
        boolean enabled = false;
        Integer persistedPrior = null;
        int liveValue = genuineOriginal;

        if (PriorCaptureGate.shouldCapturePrior(enabled)) {
            persistedPrior = liveValue; // capture the genuine original
        }
        liveValue = autoxValue;         // apply the AutoX value
        enabled = true;                 // mark the session enabled (persisted)

        assertEquals(Integer.valueOf(genuineOriginal), persistedPrior);

        // --- Simulated process death + re-delivered surface: createDisplay runs again. ---
        // enabled is still true (it survived), liveValue is still the AutoX value.
        if (PriorCaptureGate.shouldCapturePrior(enabled)) {
            persistedPrior = liveValue; // would poison the prior with the applied value
        }
        // Gate forbade re-capture, so the genuine original is intact.
        assertEquals(Integer.valueOf(genuineOriginal), persistedPrior);
        liveValue = autoxValue;         // re-apply (idempotent)

        // --- Release: revert using the persisted prior. ---
        liveValue = persistedPrior;
        enabled = false;

        assertEquals(genuineOriginal, liveValue);
        assertFalse(enabled);
    }

    @Test
    public void constructorIsPrivate() throws Exception {
        Constructor<PriorCaptureGate> c = PriorCaptureGate.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(c.getModifiers()));
        c.setAccessible(true);
        c.newInstance(); // exercise the private ctor for coverage
    }
}

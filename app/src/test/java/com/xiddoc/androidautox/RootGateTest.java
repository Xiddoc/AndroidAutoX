package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Plain-JUnit tests for the pure {@link RootGate} decision logic (no Android deps).
 */
public class RootGateTest {

    // --- boolean overload -------------------------------------------------

    @Test
    public void incompleteRequest_waits_regardlessOfGranted() {
        assertEquals(RootGate.Decision.WAIT, RootGate.decide(false, false));
        // rootGranted is meaningless while the request is still running; still WAIT.
        assertEquals(RootGate.Decision.WAIT, RootGate.decide(false, true));
    }

    @Test
    public void completeAndGranted_proceeds() {
        assertEquals(RootGate.Decision.PROCEED, RootGate.decide(true, true));
    }

    @Test
    public void completeAndDenied_showsRetry() {
        assertEquals(RootGate.Decision.SHOW_RETRY, RootGate.decide(true, false));
    }

    // --- nullable Boolean overload ---------------------------------------

    @Test
    public void nullResult_isPending_waits() {
        assertEquals(RootGate.Decision.WAIT, RootGate.decide((Boolean) null));
    }

    @Test
    public void trueResult_proceeds() {
        assertEquals(RootGate.Decision.PROCEED, RootGate.decide(Boolean.TRUE));
    }

    @Test
    public void falseResult_showsRetry() {
        assertEquals(RootGate.Decision.SHOW_RETRY, RootGate.decide(Boolean.FALSE));
    }

    // --- guard against the original race bug -----------------------------

    // Regression guard for the original bug: a pending (null) result must NOT be
    // treated the same as a denied result. Pending => WAIT, denied => SHOW_RETRY.
    @Test
    public void pendingIsNotTreatedAsDenied() {
        assertEquals(RootGate.Decision.WAIT, RootGate.decide((Boolean) null));
        assertEquals(RootGate.Decision.SHOW_RETRY, RootGate.decide(Boolean.FALSE));
        org.junit.Assert.assertNotEquals(
                RootGate.decide((Boolean) null), RootGate.decide(Boolean.FALSE));
    }
}

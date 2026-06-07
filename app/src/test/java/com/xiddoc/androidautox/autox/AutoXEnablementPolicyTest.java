package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.xiddoc.androidautox.autox.AutoXEnablementPolicy.Decision;
import com.xiddoc.androidautox.autox.AutoXEnablementPolicy.Reason;

import org.junit.Test;

/**
 * Plain-JUnit (no Robolectric) exhaustive coverage for {@link AutoXEnablementPolicy}.
 *
 * <p>The policy is pure logic (no Android imports), so every branch is reachable on the
 * plain JVM classloader, making it fully visible to JaCoCo.
 *
 * <p>Truth table: 3 binary inputs → 8 combinations.
 * enabled | targetChosen | providerAvailable | expected canProject | expected Reason
 * --------|--------------|-------------------|---------------------|-----------------
 * false   | false        | false             | false               | NOT_ENABLED
 * false   | false        | true              | false               | NOT_ENABLED
 * false   | true         | false             | false               | NOT_ENABLED
 * false   | true         | true              | false               | NOT_ENABLED
 * true    | false        | false             | false               | NO_TARGET_CHOSEN
 * true    | false        | true              | false               | NO_TARGET_CHOSEN
 * true    | true         | false             | false               | PROVIDER_UNAVAILABLE
 * true    | true         | true              | true                | OK
 */
public class AutoXEnablementPolicyTest {

    // -------------------------------------------------------------------------
    // Helper: wraps evaluate() and makes assertions shorter
    // -------------------------------------------------------------------------

    private static Decision eval(boolean enabled, boolean targetChosen, boolean providerAvailable) {
        return AutoXEnablementPolicy.evaluate(enabled, targetChosen, providerAvailable);
    }

    // -------------------------------------------------------------------------
    // NOT_ENABLED branch — disabled overrides everything
    // -------------------------------------------------------------------------

    @Test
    public void disabled_noTarget_noProvider_isNotEnabled() {
        Decision d = eval(false, false, false);
        assertFalse(d.canProject);
        assertEquals(Reason.NOT_ENABLED, d.reason);
    }

    @Test
    public void disabled_noTarget_withProvider_isNotEnabled() {
        Decision d = eval(false, false, true);
        assertFalse(d.canProject);
        assertEquals(Reason.NOT_ENABLED, d.reason);
    }

    @Test
    public void disabled_withTarget_noProvider_isNotEnabled() {
        Decision d = eval(false, true, false);
        assertFalse(d.canProject);
        assertEquals(Reason.NOT_ENABLED, d.reason);
    }

    @Test
    public void disabled_withTarget_withProvider_isNotEnabled() {
        Decision d = eval(false, true, true);
        assertFalse(d.canProject);
        assertEquals(Reason.NOT_ENABLED, d.reason);
    }

    // -------------------------------------------------------------------------
    // NO_TARGET_CHOSEN branch — enabled but no target
    // -------------------------------------------------------------------------

    @Test
    public void enabled_noTarget_noProvider_isNoTargetChosen() {
        Decision d = eval(true, false, false);
        assertFalse(d.canProject);
        assertEquals(Reason.NO_TARGET_CHOSEN, d.reason);
    }

    @Test
    public void enabled_noTarget_withProvider_isNoTargetChosen() {
        Decision d = eval(true, false, true);
        assertFalse(d.canProject);
        assertEquals(Reason.NO_TARGET_CHOSEN, d.reason);
    }

    // -------------------------------------------------------------------------
    // PROVIDER_UNAVAILABLE branch — enabled + target chosen but no provider
    // -------------------------------------------------------------------------

    @Test
    public void enabled_withTarget_noProvider_isProviderUnavailable() {
        Decision d = eval(true, true, false);
        assertFalse(d.canProject);
        assertEquals(Reason.PROVIDER_UNAVAILABLE, d.reason);
    }

    // -------------------------------------------------------------------------
    // OK branch — all conditions satisfied
    // -------------------------------------------------------------------------

    @Test
    public void enabled_withTarget_withProvider_canProject() {
        Decision d = eval(true, true, true);
        assertTrue(d.canProject);
        assertEquals(Reason.OK, d.reason);
    }

    // -------------------------------------------------------------------------
    // Decision object contracts
    // -------------------------------------------------------------------------

    @Test
    public void decision_toString_containsCanProjectAndReason() {
        Decision d = eval(true, true, true);
        String s = d.toString();
        assertTrue(s.contains("canProject=true"));
        assertTrue(s.contains("OK"));
    }

    @Test
    public void decision_toString_blockingCase_containsReason() {
        Decision d = eval(false, false, false);
        String s = d.toString();
        assertTrue(s.contains("canProject=false"));
        assertTrue(s.contains("NOT_ENABLED"));
    }

    @Test
    public void decision_notNull() {
        assertNotNull(eval(true, true, true));
        assertNotNull(eval(false, false, false));
    }

    // -------------------------------------------------------------------------
    // Reason enum exhaustiveness — every Reason value is reachable
    // -------------------------------------------------------------------------

    @Test
    public void reason_ok_isReachable() {
        assertEquals(Reason.OK, eval(true, true, true).reason);
    }

    @Test
    public void reason_notEnabled_isReachable() {
        assertEquals(Reason.NOT_ENABLED, eval(false, true, true).reason);
    }

    @Test
    public void reason_noTargetChosen_isReachable() {
        assertEquals(Reason.NO_TARGET_CHOSEN, eval(true, false, true).reason);
    }

    @Test
    public void reason_providerUnavailable_isReachable() {
        assertEquals(Reason.PROVIDER_UNAVAILABLE, eval(true, true, false).reason);
    }
}

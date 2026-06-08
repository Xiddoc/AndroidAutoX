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
 * <p>Check order: enabled → targetChosen → rootAvailable → lsposedActive → providerAvailable.
 * The first failing check determines the reason; all five blocking reasons plus OK are covered.
 */
public class AutoXEnablementPolicyTest {

    // -------------------------------------------------------------------------
    // Helper: wraps evaluate() and makes assertions shorter
    // -------------------------------------------------------------------------

    private static Decision eval(boolean enabled, boolean targetChosen,
                                 boolean rootAvailable, boolean lsposedActive,
                                 boolean providerAvailable) {
        return AutoXEnablementPolicy.evaluate(
                enabled, targetChosen, rootAvailable, lsposedActive, providerAvailable);
    }

    // -------------------------------------------------------------------------
    // NOT_ENABLED branch — disabled overrides everything
    // -------------------------------------------------------------------------

    @Test
    public void disabled_overridesEverything_isNotEnabled() {
        Decision d = eval(false, true, true, true, true);
        assertFalse(d.canProject);
        assertEquals(Reason.NOT_ENABLED, d.reason);
    }

    @Test
    public void disabled_evenWithNothingElse_isNotEnabled() {
        Decision d = eval(false, false, false, false, false);
        assertFalse(d.canProject);
        assertEquals(Reason.NOT_ENABLED, d.reason);
    }

    // -------------------------------------------------------------------------
    // NO_TARGET_CHOSEN branch — enabled but no target
    // -------------------------------------------------------------------------

    @Test
    public void enabled_noTarget_isNoTargetChosen() {
        Decision d = eval(true, false, true, true, true);
        assertFalse(d.canProject);
        assertEquals(Reason.NO_TARGET_CHOSEN, d.reason);
    }

    // -------------------------------------------------------------------------
    // ROOT_UNAVAILABLE branch — enabled + target but no root
    // -------------------------------------------------------------------------

    @Test
    public void enabled_target_noRoot_isRootUnavailable() {
        Decision d = eval(true, true, false, true, true);
        assertFalse(d.canProject);
        assertEquals(Reason.ROOT_UNAVAILABLE, d.reason);
    }

    @Test
    public void noRoot_takesPrecedenceOverMissingLsposedAndProvider() {
        // Root checked before lsposed/provider — root reason wins.
        Decision d = eval(true, true, false, false, false);
        assertEquals(Reason.ROOT_UNAVAILABLE, d.reason);
    }

    // -------------------------------------------------------------------------
    // LSPOSED_UNAVAILABLE branch — root present but no LSPosed
    // -------------------------------------------------------------------------

    @Test
    public void enabled_target_root_noLsposed_isLsposedUnavailable() {
        Decision d = eval(true, true, true, false, true);
        assertFalse(d.canProject);
        assertEquals(Reason.LSPOSED_UNAVAILABLE, d.reason);
    }

    @Test
    public void noLsposed_takesPrecedenceOverMissingProvider() {
        Decision d = eval(true, true, true, false, false);
        assertEquals(Reason.LSPOSED_UNAVAILABLE, d.reason);
    }

    // -------------------------------------------------------------------------
    // PROVIDER_UNAVAILABLE branch — everything but the provider
    // -------------------------------------------------------------------------

    @Test
    public void enabled_target_root_lsposed_noProvider_isProviderUnavailable() {
        Decision d = eval(true, true, true, true, false);
        assertFalse(d.canProject);
        assertEquals(Reason.PROVIDER_UNAVAILABLE, d.reason);
    }

    // -------------------------------------------------------------------------
    // OK branch — all conditions satisfied
    // -------------------------------------------------------------------------

    @Test
    public void allSatisfied_canProject() {
        Decision d = eval(true, true, true, true, true);
        assertTrue(d.canProject);
        assertEquals(Reason.OK, d.reason);
    }

    // -------------------------------------------------------------------------
    // Decision object contracts
    // -------------------------------------------------------------------------

    @Test
    public void decision_toString_containsCanProjectAndReason() {
        Decision d = eval(true, true, true, true, true);
        String s = d.toString();
        assertTrue(s.contains("canProject=true"));
        assertTrue(s.contains("OK"));
    }

    @Test
    public void decision_toString_blockingCase_containsReason() {
        Decision d = eval(false, false, false, false, false);
        String s = d.toString();
        assertTrue(s.contains("canProject=false"));
        assertTrue(s.contains("NOT_ENABLED"));
    }

    @Test
    public void decision_notNull() {
        assertNotNull(eval(true, true, true, true, true));
        assertNotNull(eval(false, false, false, false, false));
    }

    // -------------------------------------------------------------------------
    // Reason enum exhaustiveness — every Reason value is reachable
    // -------------------------------------------------------------------------

    @Test
    public void reason_ok_isReachable() {
        assertEquals(Reason.OK, eval(true, true, true, true, true).reason);
    }

    @Test
    public void reason_notEnabled_isReachable() {
        assertEquals(Reason.NOT_ENABLED, eval(false, true, true, true, true).reason);
    }

    @Test
    public void reason_noTargetChosen_isReachable() {
        assertEquals(Reason.NO_TARGET_CHOSEN, eval(true, false, true, true, true).reason);
    }

    @Test
    public void reason_rootUnavailable_isReachable() {
        assertEquals(Reason.ROOT_UNAVAILABLE, eval(true, true, false, true, true).reason);
    }

    @Test
    public void reason_lsposedUnavailable_isReachable() {
        assertEquals(Reason.LSPOSED_UNAVAILABLE, eval(true, true, true, false, true).reason);
    }

    @Test
    public void reason_providerUnavailable_isReachable() {
        assertEquals(Reason.PROVIDER_UNAVAILABLE, eval(true, true, true, true, false).reason);
    }

    @Test
    public void reason_valueOf_allValues() {
        // Exercise the generated enum values()/valueOf for full coverage.
        for (Reason r : Reason.values()) {
            assertEquals(r, Reason.valueOf(r.name()));
        }
    }
}

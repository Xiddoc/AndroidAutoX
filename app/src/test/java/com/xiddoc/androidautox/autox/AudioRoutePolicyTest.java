package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.xiddoc.androidautox.autox.AudioRoutePolicy.CarAudioDevice;
import com.xiddoc.androidautox.autox.AudioRoutePolicy.RouteDecision;
import com.xiddoc.androidautox.autox.AudioRoutePolicy.RouteStep;
import com.xiddoc.androidautox.autox.AudioRoutePolicy.RouteStep.ClearAffinity;
import com.xiddoc.androidautox.autox.AudioRoutePolicy.RouteStep.NoRoute;
import com.xiddoc.androidautox.autox.AudioRoutePolicy.RouteStep.SetAffinity;

import org.junit.Test;

/**
 * Exhaustive plain-JUnit tests for {@link AudioRoutePolicy}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Every branch in {@link AudioRoutePolicy#decide} (null deviceType, invalid UID,
 *       NONE device, blank/null address, happy path for both BT_A2DP and USB).</li>
 *   <li>All three {@link RouteStep} sub-types: equals/hashCode/toString contracts.</li>
 *   <li>{@link RouteDecision} equals/hashCode/toString contracts.</li>
 *   <li>{@link CarAudioDevice} enum values (valueOf/values).</li>
 *   <li>Edge UIDs (0, -1, Integer.MIN_VALUE, 1, 1000).</li>
 *   <li>Edge addresses (null, empty string, whitespace, non-blank).</li>
 * </ul>
 */
public class AudioRoutePolicyTest {

    private static final int VALID_UID = 10100;
    private static final String VALID_ADDR = "01:23:45:67:89:AB";

    // ------------------------------------------------------------------
    // decide() — null deviceType guard
    // ------------------------------------------------------------------

    @Test
    public void decide_nullDeviceType_throwsIllegalArgument() {
        try {
            AudioRoutePolicy.decide(VALID_UID, null, VALID_ADDR);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("deviceType"));
        }
    }

    // ------------------------------------------------------------------
    // decide() — invalid UID (rule 1: uid <= 0)
    // ------------------------------------------------------------------

    @Test
    public void decide_uidZero_returnsNoRoute() {
        RouteDecision d = AudioRoutePolicy.decide(0, CarAudioDevice.BT_A2DP, VALID_ADDR);
        assertNoRoute(d.applyStep);
        assertNoRoute(d.revertStep);
        assertTrue(((NoRoute) d.applyStep).reason.contains("UID"));
    }

    @Test
    public void decide_uidNegativeOne_returnsNoRoute() {
        RouteDecision d = AudioRoutePolicy.decide(-1, CarAudioDevice.BT_A2DP, VALID_ADDR);
        assertNoRoute(d.applyStep);
        assertNoRoute(d.revertStep);
    }

    @Test
    public void decide_uidMinValue_returnsNoRoute() {
        RouteDecision d = AudioRoutePolicy.decide(Integer.MIN_VALUE, CarAudioDevice.USB, VALID_ADDR);
        assertNoRoute(d.applyStep);
        assertNoRoute(d.revertStep);
    }

    // ------------------------------------------------------------------
    // decide() — NONE device (rule 2: deviceType == NONE)
    // ------------------------------------------------------------------

    @Test
    public void decide_noneDevice_returnsNoRoute() {
        RouteDecision d = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.NONE, VALID_ADDR);
        assertNoRoute(d.applyStep);
        assertNoRoute(d.revertStep);
        assertTrue(((NoRoute) d.applyStep).reason.contains("NONE"));
    }

    @Test
    public void decide_noneDevice_nullAddress_stillNoRoute() {
        // NONE device check fires before address check
        RouteDecision d = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.NONE, null);
        assertNoRoute(d.applyStep);
        assertNoRoute(d.revertStep);
    }

    // ------------------------------------------------------------------
    // decide() — null / blank address (rule 3)
    // ------------------------------------------------------------------

    @Test
    public void decide_nullAddress_btA2dp_returnsNoRoute() {
        RouteDecision d = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.BT_A2DP, null);
        assertNoRoute(d.applyStep);
        assertNoRoute(d.revertStep);
        assertTrue(((NoRoute) d.applyStep).reason.contains("blank"));
    }

    @Test
    public void decide_emptyAddress_usb_returnsNoRoute() {
        RouteDecision d = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.USB, "");
        assertNoRoute(d.applyStep);
        assertNoRoute(d.revertStep);
    }

    @Test
    public void decide_whitespaceAddress_returnsNoRoute() {
        RouteDecision d = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.BT_A2DP, "   ");
        assertNoRoute(d.applyStep);
        assertNoRoute(d.revertStep);
    }

    // ------------------------------------------------------------------
    // decide() — happy path: BT_A2DP
    // ------------------------------------------------------------------

    @Test
    public void decide_btA2dp_validUidAndAddress_returnsSetAndClear() {
        RouteDecision d = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.BT_A2DP, VALID_ADDR);
        assertTrue(d.applyStep instanceof SetAffinity);
        SetAffinity sa = (SetAffinity) d.applyStep;
        assertEquals(VALID_UID, sa.uid);
        assertEquals(VALID_ADDR, sa.deviceAddress);

        assertTrue(d.revertStep instanceof ClearAffinity);
        ClearAffinity ca = (ClearAffinity) d.revertStep;
        assertEquals(VALID_UID, ca.uid);
    }

    // ------------------------------------------------------------------
    // decide() — happy path: USB
    // ------------------------------------------------------------------

    @Test
    public void decide_usb_validUidAndAddress_returnsSetAndClear() {
        RouteDecision d = AudioRoutePolicy.decide(1000, CarAudioDevice.USB, "bus0");
        assertTrue(d.applyStep instanceof SetAffinity);
        assertEquals(1000, ((SetAffinity) d.applyStep).uid);
        assertEquals("bus0", ((SetAffinity) d.applyStep).deviceAddress);

        assertTrue(d.revertStep instanceof ClearAffinity);
        assertEquals(1000, ((ClearAffinity) d.revertStep).uid);
    }

    // ------------------------------------------------------------------
    // decide() — boundary UIDs that ARE valid (uid == 1, uid == 1000)
    // ------------------------------------------------------------------

    @Test
    public void decide_uid1_btA2dp_valid_returnsSetAffinity() {
        RouteDecision d = AudioRoutePolicy.decide(1, CarAudioDevice.BT_A2DP, VALID_ADDR);
        assertTrue(d.applyStep instanceof SetAffinity);
    }

    @Test
    public void decide_uid1000_usb_valid_returnsSetAffinity() {
        RouteDecision d = AudioRoutePolicy.decide(1000, CarAudioDevice.USB, "usb:0");
        assertTrue(d.applyStep instanceof SetAffinity);
    }

    // ------------------------------------------------------------------
    // RouteStep.SetAffinity — equals / hashCode / toString
    // ------------------------------------------------------------------

    @Test
    public void setAffinity_equals_reflexive() {
        SetAffinity sa = new SetAffinity(100, "addr");
        assertEquals(sa, sa);
    }

    @Test
    public void setAffinity_equals_symmetric() {
        SetAffinity a = new SetAffinity(100, "addr");
        SetAffinity b = new SetAffinity(100, "addr");
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    public void setAffinity_equals_differentUid_notEqual() {
        assertNotEquals(new SetAffinity(1, "addr"), new SetAffinity(2, "addr"));
    }

    @Test
    public void setAffinity_equals_differentAddress_notEqual() {
        assertNotEquals(new SetAffinity(1, "a"), new SetAffinity(1, "b"));
    }

    @Test
    public void setAffinity_equals_null_returnsFalse() {
        assertFalse(new SetAffinity(1, "a").equals(null));
    }

    @Test
    public void setAffinity_equals_wrongType_returnsFalse() {
        assertFalse(new SetAffinity(1, "a").equals("a"));
    }

    @Test
    public void setAffinity_hashCode_equalObjects_sameHash() {
        SetAffinity a = new SetAffinity(100, "addr");
        SetAffinity b = new SetAffinity(100, "addr");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void setAffinity_toString_containsUidAndAddress() {
        String s = new SetAffinity(42, "dev:1").toString();
        assertTrue(s.contains("42"));
        assertTrue(s.contains("dev:1"));
    }

    // ------------------------------------------------------------------
    // RouteStep.ClearAffinity — equals / hashCode / toString
    // ------------------------------------------------------------------

    @Test
    public void clearAffinity_equals_reflexive() {
        ClearAffinity ca = new ClearAffinity(200);
        assertEquals(ca, ca);
    }

    @Test
    public void clearAffinity_equals_symmetric() {
        ClearAffinity a = new ClearAffinity(200);
        ClearAffinity b = new ClearAffinity(200);
        assertEquals(a, b);
    }

    @Test
    public void clearAffinity_equals_differentUid_notEqual() {
        assertNotEquals(new ClearAffinity(1), new ClearAffinity(2));
    }

    @Test
    public void clearAffinity_equals_null_returnsFalse() {
        assertFalse(new ClearAffinity(1).equals(null));
    }

    @Test
    public void clearAffinity_equals_wrongType_returnsFalse() {
        assertFalse(new ClearAffinity(1).equals("x"));
    }

    @Test
    public void clearAffinity_hashCode_equalObjects_sameHash() {
        assertEquals(new ClearAffinity(5).hashCode(), new ClearAffinity(5).hashCode());
    }

    @Test
    public void clearAffinity_toString_containsUid() {
        assertTrue(new ClearAffinity(99).toString().contains("99"));
    }

    // ------------------------------------------------------------------
    // RouteStep.NoRoute — equals / hashCode / toString
    // ------------------------------------------------------------------

    @Test
    public void noRoute_equals_reflexive() {
        NoRoute nr = new NoRoute("reason");
        assertEquals(nr, nr);
    }

    @Test
    public void noRoute_equals_symmetric() {
        NoRoute a = new NoRoute("r");
        NoRoute b = new NoRoute("r");
        assertEquals(a, b);
    }

    @Test
    public void noRoute_equals_differentReason_notEqual() {
        assertNotEquals(new NoRoute("a"), new NoRoute("b"));
    }

    @Test
    public void noRoute_equals_null_returnsFalse() {
        assertFalse(new NoRoute("r").equals(null));
    }

    @Test
    public void noRoute_equals_wrongType_returnsFalse() {
        assertFalse(new NoRoute("r").equals(42));
    }

    @Test
    public void noRoute_hashCode_equalObjects_sameHash() {
        assertEquals(new NoRoute("r").hashCode(), new NoRoute("r").hashCode());
    }

    @Test
    public void noRoute_toString_containsReason() {
        assertTrue(new NoRoute("bad uid").toString().contains("bad uid"));
    }

    // ------------------------------------------------------------------
    // RouteStep cross-type inequality (SetAffinity != ClearAffinity != NoRoute)
    // ------------------------------------------------------------------

    @Test
    public void setAffinity_notEqualTo_clearAffinity() {
        // Same uid, same hashCode territory — must still differ by type
        assertFalse(new SetAffinity(1, "x").equals(new ClearAffinity(1)));
    }

    @Test
    public void setAffinity_notEqualTo_noRoute() {
        assertFalse(new SetAffinity(1, "x").equals(new NoRoute("x")));
    }

    @Test
    public void clearAffinity_notEqualTo_noRoute() {
        assertFalse(new ClearAffinity(1).equals(new NoRoute("1")));
    }

    // ------------------------------------------------------------------
    // RouteDecision — equals / hashCode / toString
    // ------------------------------------------------------------------

    @Test
    public void routeDecision_equals_reflexive() {
        RouteDecision d = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.BT_A2DP, VALID_ADDR);
        assertEquals(d, d);
    }

    @Test
    public void routeDecision_equals_twoHappyPath_equal() {
        RouteDecision a = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.BT_A2DP, VALID_ADDR);
        RouteDecision b = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.BT_A2DP, VALID_ADDR);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void routeDecision_equals_twoNoRoute_equal() {
        RouteDecision a = AudioRoutePolicy.decide(0, CarAudioDevice.BT_A2DP, VALID_ADDR);
        RouteDecision b = AudioRoutePolicy.decide(0, CarAudioDevice.BT_A2DP, VALID_ADDR);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void routeDecision_equals_happyVsNoRoute_notEqual() {
        RouteDecision happy = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.BT_A2DP, VALID_ADDR);
        RouteDecision noRoute = AudioRoutePolicy.decide(0, CarAudioDevice.BT_A2DP, VALID_ADDR);
        assertNotEquals(happy, noRoute);
    }

    @Test
    public void routeDecision_equals_differentUid_notEqual() {
        RouteDecision a = AudioRoutePolicy.decide(1000, CarAudioDevice.BT_A2DP, VALID_ADDR);
        RouteDecision b = AudioRoutePolicy.decide(2000, CarAudioDevice.BT_A2DP, VALID_ADDR);
        assertNotEquals(a, b);
    }

    @Test
    public void routeDecision_equals_differentAddress_notEqual() {
        RouteDecision a = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.BT_A2DP, "addr1");
        RouteDecision b = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.BT_A2DP, "addr2");
        assertNotEquals(a, b);
    }

    @Test
    public void routeDecision_equals_null_returnsFalse() {
        RouteDecision d = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.USB, VALID_ADDR);
        assertFalse(d.equals(null));
    }

    @Test
    public void routeDecision_equals_wrongType_returnsFalse() {
        RouteDecision d = AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.USB, VALID_ADDR);
        assertFalse(d.equals("x"));
    }

    @Test
    public void routeDecision_equals_sameApplyDifferentRevert_notEqual() {
        // Covers the second branch of the && in RouteDecision.equals (applyStep equal,
        // but revertStep differs → reaches and evaluates the right-hand side).
        RouteDecision a = new RouteDecision(
                new RouteStep.SetAffinity(VALID_UID, "addr"),
                new RouteStep.ClearAffinity(VALID_UID));
        RouteDecision b = new RouteDecision(
                new RouteStep.SetAffinity(VALID_UID, "addr"),
                new RouteStep.ClearAffinity(VALID_UID + 1));
        assertNotEquals(a, b);
    }

    @Test
    public void routeDecision_toString_isNotNull() {
        assertNotNull(AudioRoutePolicy.decide(VALID_UID, CarAudioDevice.BT_A2DP, VALID_ADDR).toString());
        assertNotNull(AudioRoutePolicy.decide(0, CarAudioDevice.NONE, null).toString());
    }

    // ------------------------------------------------------------------
    // CarAudioDevice enum — valueOf / values
    // ------------------------------------------------------------------

    @Test
    public void carAudioDevice_enumValues_count() {
        assertEquals(3, CarAudioDevice.values().length);
    }

    @Test
    public void carAudioDevice_valueOf_btA2dp() {
        assertEquals(CarAudioDevice.BT_A2DP, CarAudioDevice.valueOf("BT_A2DP"));
    }

    @Test
    public void carAudioDevice_valueOf_usb() {
        assertEquals(CarAudioDevice.USB, CarAudioDevice.valueOf("USB"));
    }

    @Test
    public void carAudioDevice_valueOf_none() {
        assertEquals(CarAudioDevice.NONE, CarAudioDevice.valueOf("NONE"));
    }

    // ------------------------------------------------------------------
    // Helper: assert a RouteStep is a NoRoute with non-null reason
    // ------------------------------------------------------------------

    private static void assertNoRoute(RouteStep step) {
        assertTrue("expected NoRoute but got " + step.getClass().getSimpleName(),
                step instanceof NoRoute);
        assertNotNull(((NoRoute) step).reason);
    }
}

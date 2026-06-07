package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xiddoc.androidautox.autox.AudioRoutePolicy.CarAudioDevice;
import com.xiddoc.androidautox.autox.AudioRoutePolicy.RouteDecision;
import com.xiddoc.androidautox.autox.AudioRoutePolicy.RouteStep;
import com.xiddoc.androidautox.autox.provider.AudioRouter;

import org.junit.Before;
import org.junit.Test;

/**
 * Exhaustive plain-JUnit tests for {@link AudioRouteApplier}.
 *
 * <p>Uses a simple in-process {@link FakeAudioRouter} that records which calls were made
 * and controls per-call success/failure flags, so no Mockito or Android runtime is needed.
 *
 * <p>Covers every branch in {@link AudioRouteApplier#apply} and
 * {@link AudioRouteApplier#revert}:
 * <ul>
 *   <li>Null decision / null router guards (both methods).</li>
 *   <li>{@link AudioRoutePolicy.RouteStep.SetAffinity} apply step → delegates to
 *       {@link AudioRouter#setUidAffinity}.</li>
 *   <li>{@link AudioRoutePolicy.RouteStep.ClearAffinity} apply step → returns false
 *       (caller error guard).</li>
 *   <li>{@link AudioRoutePolicy.RouteStep.NoRoute} apply step → returns false.</li>
 *   <li>{@link AudioRoutePolicy.RouteStep.ClearAffinity} revert step → delegates to
 *       {@link AudioRouter#clearUidAffinity}.</li>
 *   <li>{@link AudioRoutePolicy.RouteStep.NoRoute} revert step → returns false.</li>
 *   <li>Router returning {@code false} is propagated correctly.</li>
 *   <li>UID/address values are forwarded unmodified.</li>
 * </ul>
 */
public class AudioRouteApplierTest {

    private static final int UID = 10200;
    private static final String ADDR = "AA:BB:CC:DD:EE:FF";

    private FakeAudioRouter fakeRouter;

    @Before
    public void setUp() {
        fakeRouter = new FakeAudioRouter();
    }

    // ------------------------------------------------------------------
    // apply() — null guards
    // ------------------------------------------------------------------

    @Test
    public void apply_nullDecision_returnsFalse() {
        assertFalse(AudioRouteApplier.apply(null, fakeRouter));
    }

    @Test
    public void apply_nullRouter_returnsFalse() {
        RouteDecision d = happyDecision();
        assertFalse(AudioRouteApplier.apply(d, null));
    }

    @Test
    public void apply_bothNull_returnsFalse() {
        assertFalse(AudioRouteApplier.apply(null, null));
    }

    // ------------------------------------------------------------------
    // apply() — SetAffinity step
    // ------------------------------------------------------------------

    @Test
    public void apply_setAffinityStep_routerSucceeds_returnsTrue() {
        fakeRouter.setAffinityResult = true;
        assertTrue(AudioRouteApplier.apply(happyDecision(), fakeRouter));
        assertTrue(fakeRouter.setAffinityCalled);
        assertEquals(UID, fakeRouter.lastSetUid);
        assertEquals(ADDR, fakeRouter.lastSetAddress);
    }

    @Test
    public void apply_setAffinityStep_routerFails_returnsFalse() {
        fakeRouter.setAffinityResult = false;
        assertFalse(AudioRouteApplier.apply(happyDecision(), fakeRouter));
        assertTrue(fakeRouter.setAffinityCalled);
    }

    @Test
    public void apply_setAffinityStep_doesNotCallClear() {
        fakeRouter.setAffinityResult = true;
        AudioRouteApplier.apply(happyDecision(), fakeRouter);
        assertFalse(fakeRouter.clearAffinityCalled);
    }

    // ------------------------------------------------------------------
    // apply() — NoRoute step (no device available)
    // ------------------------------------------------------------------

    @Test
    public void apply_noRouteStep_returnsFalse_noCallsMade() {
        RouteDecision d = AudioRoutePolicy.decide(UID, CarAudioDevice.NONE, ADDR);
        assertFalse(AudioRouteApplier.apply(d, fakeRouter));
        assertFalse(fakeRouter.setAffinityCalled);
        assertFalse(fakeRouter.clearAffinityCalled);
    }

    @Test
    public void apply_noRouteStep_invalidUid_returnsFalse() {
        RouteDecision d = AudioRoutePolicy.decide(0, CarAudioDevice.BT_A2DP, ADDR);
        assertFalse(AudioRouteApplier.apply(d, fakeRouter));
        assertFalse(fakeRouter.setAffinityCalled);
    }

    @Test
    public void apply_noRouteStep_blankAddress_returnsFalse() {
        RouteDecision d = AudioRoutePolicy.decide(UID, CarAudioDevice.BT_A2DP, "");
        assertFalse(AudioRouteApplier.apply(d, fakeRouter));
        assertFalse(fakeRouter.setAffinityCalled);
    }

    // ------------------------------------------------------------------
    // apply() — ClearAffinity as the applyStep (manual construction — caller error guard)
    // ------------------------------------------------------------------

    @Test
    public void apply_clearAffinityApplyStep_returnsFalse() {
        // A RouteDecision with a ClearAffinity apply-step is a caller error;
        // AudioRouteApplier must not call setUidAffinity or clearUidAffinity.
        RouteDecision d = new RouteDecision(
                new RouteStep.ClearAffinity(UID),
                new RouteStep.ClearAffinity(UID));
        assertFalse(AudioRouteApplier.apply(d, fakeRouter));
        assertFalse(fakeRouter.setAffinityCalled);
        assertFalse(fakeRouter.clearAffinityCalled);
    }

    // ------------------------------------------------------------------
    // revert() — null guards
    // ------------------------------------------------------------------

    @Test
    public void revert_nullDecision_returnsFalse() {
        assertFalse(AudioRouteApplier.revert(null, fakeRouter));
    }

    @Test
    public void revert_nullRouter_returnsFalse() {
        RouteDecision d = happyDecision();
        assertFalse(AudioRouteApplier.revert(d, null));
    }

    @Test
    public void revert_bothNull_returnsFalse() {
        assertFalse(AudioRouteApplier.revert(null, null));
    }

    // ------------------------------------------------------------------
    // revert() — ClearAffinity revert step
    // ------------------------------------------------------------------

    @Test
    public void revert_clearAffinityStep_routerSucceeds_returnsTrue() {
        fakeRouter.clearAffinityResult = true;
        assertTrue(AudioRouteApplier.revert(happyDecision(), fakeRouter));
        assertTrue(fakeRouter.clearAffinityCalled);
        assertEquals(UID, fakeRouter.lastClearUid);
    }

    @Test
    public void revert_clearAffinityStep_routerFails_returnsFalse() {
        fakeRouter.clearAffinityResult = false;
        assertFalse(AudioRouteApplier.revert(happyDecision(), fakeRouter));
        assertTrue(fakeRouter.clearAffinityCalled);
    }

    @Test
    public void revert_clearAffinityStep_doesNotCallSet() {
        fakeRouter.clearAffinityResult = true;
        AudioRouteApplier.revert(happyDecision(), fakeRouter);
        assertFalse(fakeRouter.setAffinityCalled);
    }

    // ------------------------------------------------------------------
    // revert() — NoRoute revert step
    // ------------------------------------------------------------------

    @Test
    public void revert_noRouteStep_returnsFalse_noCallsMade() {
        RouteDecision d = AudioRoutePolicy.decide(UID, CarAudioDevice.NONE, ADDR);
        assertFalse(AudioRouteApplier.revert(d, fakeRouter));
        assertFalse(fakeRouter.setAffinityCalled);
        assertFalse(fakeRouter.clearAffinityCalled);
    }

    @Test
    public void revert_noRouteStep_invalidUid_returnsFalse() {
        RouteDecision d = AudioRoutePolicy.decide(-1, CarAudioDevice.USB, ADDR);
        assertFalse(AudioRouteApplier.revert(d, fakeRouter));
        assertFalse(fakeRouter.clearAffinityCalled);
    }

    // ------------------------------------------------------------------
    // revert() — SetAffinity as revert step (manual construction)
    // ------------------------------------------------------------------

    @Test
    public void revert_setAffinityRevertStep_returnsFalse() {
        // SetAffinity in revert position: applier must not dispatch it via clear.
        RouteDecision d = new RouteDecision(
                new RouteStep.SetAffinity(UID, ADDR),
                new RouteStep.SetAffinity(UID, ADDR));
        assertFalse(AudioRouteApplier.revert(d, fakeRouter));
        assertFalse(fakeRouter.clearAffinityCalled);
        assertFalse(fakeRouter.setAffinityCalled);
    }

    // ------------------------------------------------------------------
    // Integration: apply then revert round-trip
    // ------------------------------------------------------------------

    @Test
    public void applyThenRevert_happyPath_bothSucceed() {
        fakeRouter.setAffinityResult = true;
        fakeRouter.clearAffinityResult = true;
        RouteDecision d = happyDecision();

        assertTrue(AudioRouteApplier.apply(d, fakeRouter));
        assertTrue(fakeRouter.setAffinityCalled);
        assertEquals(UID, fakeRouter.lastSetUid);
        assertEquals(ADDR, fakeRouter.lastSetAddress);

        assertTrue(AudioRouteApplier.revert(d, fakeRouter));
        assertTrue(fakeRouter.clearAffinityCalled);
        assertEquals(UID, fakeRouter.lastClearUid);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RouteDecision happyDecision() {
        return AudioRoutePolicy.decide(UID, CarAudioDevice.BT_A2DP, ADDR);
    }

    // ------------------------------------------------------------------
    // Fake AudioRouter (in-process, no Mockito needed)
    // ------------------------------------------------------------------

    private static final class FakeAudioRouter implements AudioRouter {
        boolean setAffinityResult  = true;
        boolean clearAffinityResult = true;

        boolean setAffinityCalled  = false;
        boolean clearAffinityCalled = false;

        int    lastSetUid;
        String lastSetAddress;
        int    lastClearUid;

        @Override
        public boolean setUidAffinity(int uid, String deviceAddress) {
            setAffinityCalled = true;
            lastSetUid = uid;
            lastSetAddress = deviceAddress;
            return setAffinityResult;
        }

        @Override
        public boolean clearUidAffinity(int uid) {
            clearAffinityCalled = true;
            lastClearUid = uid;
            return clearAffinityResult;
        }
    }
}

package com.xiddoc.androidautox.autox.provider.lsposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/** Tests for the pure {@link HookGatePolicy} act/no-act decision logic. */
public class HookGatePolicyTest {

    // ------------------------------------------------------------------
    // shouldActForDisplayName
    // ------------------------------------------------------------------

    @Test
    public void displayName_actsWhenEnabledAndNamesMatch() {
        assertTrue(HookGatePolicy.shouldActForDisplayName(
                true, "AutoX_Isolated_Canvas", "AutoX_Isolated_Canvas"));
    }

    @Test
    public void displayName_noActWhenIpcDisabled() {
        assertFalse(HookGatePolicy.shouldActForDisplayName(
                false, "AutoX_Isolated_Canvas", "AutoX_Isolated_Canvas"));
    }

    @Test
    public void displayName_noActWhenNamesDiffer() {
        assertFalse(HookGatePolicy.shouldActForDisplayName(
                true, "SomeOtherDisplay", "AutoX_Isolated_Canvas"));
    }

    @Test
    public void displayName_noActWhenHookedNameNull() {
        assertFalse(HookGatePolicy.shouldActForDisplayName(
                true, null, "AutoX_Isolated_Canvas"));
    }

    @Test
    public void displayName_noActWhenExpectedNull() {
        assertFalse(HookGatePolicy.shouldActForDisplayName(true, "AutoX", null));
    }

    @Test
    public void displayName_noActWhenExpectedBlank() {
        assertFalse(HookGatePolicy.shouldActForDisplayName(true, "AutoX", "   "));
    }

    // ------------------------------------------------------------------
    // shouldActForDisplayId
    // ------------------------------------------------------------------

    @Test
    public void displayId_actsWhenEnabledAndIdsMatch() {
        assertTrue(HookGatePolicy.shouldActForDisplayId(true, 7, 7));
    }

    @Test
    public void displayId_actsForZeroIds() {
        // 0 is a valid display id (>= 0) — the gate must accept it.
        assertTrue(HookGatePolicy.shouldActForDisplayId(true, 0, 0));
    }

    @Test
    public void displayId_noActWhenIpcDisabled() {
        assertFalse(HookGatePolicy.shouldActForDisplayId(false, 7, 7));
    }

    @Test
    public void displayId_noActWhenIdsDiffer() {
        assertFalse(HookGatePolicy.shouldActForDisplayId(true, 7, 9));
    }

    @Test
    public void displayId_noActWhenAutoxIdUnknown() {
        assertFalse(HookGatePolicy.shouldActForDisplayId(
                true, 7, HookGatePolicy.NO_DISPLAY_ID));
    }

    @Test
    public void displayId_noActWhenHookedIdNegative() {
        assertFalse(HookGatePolicy.shouldActForDisplayId(true, -5, 7));
    }

    // ------------------------------------------------------------------
    // Misc
    // ------------------------------------------------------------------

    @Test
    public void noDisplayIdSentinelIsNegative() {
        assertEquals(-1, HookGatePolicy.NO_DISPLAY_ID);
        assertTrue(HookGatePolicy.NO_DISPLAY_ID < 0);
    }

    @Test
    public void privateConstructor_isInvocableForCoverage() throws Exception {
        Constructor<HookGatePolicy> c = HookGatePolicy.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(c.getModifiers()));
        c.setAccessible(true);
        c.newInstance();
    }
}

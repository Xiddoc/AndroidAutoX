package com.xiddoc.androidautox;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pure JUnit tests for {@link ScreenSetupTweaks} — the breakpoint-to-trigger-name
 * mapping extracted from {@code MainActivity}'s screen-setup handler. No Android
 * runtime. Covers every {@code switch} branch including the default.
 */
public class ScreenSetupTweaksTest {

    @Test
    public void breakpointTweakName_470_isForceWs() {
        assertEquals("force_ws", ScreenSetupTweaks.breakpointTweakName(470));
    }

    @Test
    public void breakpointTweakName_1920_isForceNoWs() {
        assertEquals("force_no_ws", ScreenSetupTweaks.breakpointTweakName(1920));
    }

    @Test
    public void breakpointTweakName_10_isForcePortrait() {
        assertEquals("force_portrait", ScreenSetupTweaks.breakpointTweakName(10));
    }

    @Test
    public void breakpointTweakName_unknown_isEmpty() {
        assertEquals("", ScreenSetupTweaks.breakpointTweakName(0));
        assertEquals("", ScreenSetupTweaks.breakpointTweakName(999));
    }
}

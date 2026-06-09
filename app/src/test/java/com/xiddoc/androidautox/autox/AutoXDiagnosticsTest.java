package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/** Tests for the pure {@link AutoXDiagnostics} environment-snapshot formatter. */
public class AutoXDiagnosticsTest {

    @Test
    public void report_includesEveryFieldAndThePhaseLabel() {
        String r = AutoXDiagnostics.report(
                "createDisplay", 34, "LSPOSED", 7, 1920, 1080, 160, true);
        assertTrue(r.contains("[createDisplay]"));
        assertTrue(r.contains("sdkInt           = 34"));
        assertTrue(r.contains("providerDecision = LSPOSED"));
        assertTrue(r.contains("displayId        = 7"));
        assertTrue(r.contains("geometry         = 1920x1080 @ 160dpi"));
        assertTrue(r.contains("injectionHonored = true"));
    }

    @Test
    public void report_lsposed_rendersOkVerdict() {
        String r = AutoXDiagnostics.report(
                "createDisplay", 33, "LSPOSED", 5, 800, 480, 160, false);
        assertTrue(r.contains("verdict          = OK — LSPosed path active"));
    }

    @Test
    public void report_nonLsposed_rendersBlockedVerdict() {
        String r = AutoXDiagnostics.report(
                "createDisplay", 33, "BLOCKED", -1, 0, 0, 0, false);
        assertTrue(r.contains("BLOCKED — AutoX requires the LSPosed module"));
        assertTrue(r.contains("decision=BLOCKED"));
    }

    @Test
    public void verdict_nullDecision_isBlocked() {
        // A null provider decision means the factory probe itself failed — render BLOCKED, not OK.
        assertTrue(AutoXDiagnostics.verdict(null).startsWith("BLOCKED"));
    }

    @Test
    public void verdict_lsposed_isOk() {
        assertTrue(AutoXDiagnostics.verdict("LSPOSED").startsWith("OK"));
    }

    @Test
    public void privateConstructor_isInvocableForCoverage() throws Exception {
        Constructor<AutoXDiagnostics> c = AutoXDiagnostics.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(c.getModifiers()));
        c.setAccessible(true);
        c.newInstance();
    }
}

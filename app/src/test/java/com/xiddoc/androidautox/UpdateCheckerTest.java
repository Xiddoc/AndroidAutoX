package com.xiddoc.androidautox;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pure JUnit tests for {@link UpdateChecker} — the update-availability decision and
 * countdown-text math extracted from {@code SplashActivity}. No Android runtime.
 */
public class UpdateCheckerTest {

    // ------------------------------------------------------------------
    // stripTagPrefix
    // ------------------------------------------------------------------

    @Test
    public void stripTagPrefix_dropsLeadingV() {
        assertEquals("1.2.3", UpdateChecker.stripTagPrefix("v1.2.3"));
    }

    @Test
    public void stripTagPrefix_dropsAnyFirstChar() {
        assertEquals("2.0", UpdateChecker.stripTagPrefix("r2.0"));
    }

    @Test
    public void stripTagPrefix_null_returnsNull() {
        assertNull(UpdateChecker.stripTagPrefix(null));
    }

    @Test
    public void stripTagPrefix_empty_returnsNull() {
        assertNull(UpdateChecker.stripTagPrefix(""));
    }

    @Test
    public void stripTagPrefix_singleChar_returnsEmpty() {
        assertEquals("", UpdateChecker.stripTagPrefix("v"));
    }

    // ------------------------------------------------------------------
    // evaluate — outcomes
    // ------------------------------------------------------------------

    @Test
    public void evaluate_newerRelease_updateAvailable() {
        UpdateChecker.Result r = UpdateChecker.evaluate("1.0.0", "v1.0.1");
        assertEquals(UpdateChecker.Outcome.UPDATE_AVAILABLE, r.outcome);
        assertEquals("1.0.1", r.newVersionName);
    }

    @Test
    public void evaluate_sameVersion_upToDate() {
        UpdateChecker.Result r = UpdateChecker.evaluate("1.0.1", "v1.0.1");
        assertEquals(UpdateChecker.Outcome.UP_TO_DATE, r.outcome);
        assertNull(r.newVersionName);
    }

    @Test
    public void evaluate_currentNewerThanRelease_upToDate() {
        UpdateChecker.Result r = UpdateChecker.evaluate("2.0.0", "v1.9.9");
        assertEquals(UpdateChecker.Outcome.UP_TO_DATE, r.outcome);
        assertNull(r.newVersionName);
    }

    @Test
    public void evaluate_invalidCurrentVersion_reportedAsInvalidCurrent() {
        // a non-numeric (debug) build name fails Version parsing
        UpdateChecker.Result r = UpdateChecker.evaluate("debug-SNAPSHOT", "v1.0.0");
        assertEquals(UpdateChecker.Outcome.INVALID_CURRENT_VERSION, r.outcome);
        assertNull(r.newVersionName);
    }

    @Test
    public void evaluate_malformedTag_invalidFetched() {
        UpdateChecker.Result r = UpdateChecker.evaluate("1.0.0", "vNOT.A.VERSION");
        assertEquals(UpdateChecker.Outcome.INVALID_FETCHED_VERSION, r.outcome);
        assertNull(r.newVersionName);
    }

    @Test
    public void evaluate_nullTag_invalidFetched() {
        // stripTagPrefix(null) -> null -> new Version(null) throws -> INVALID_FETCHED_VERSION
        UpdateChecker.Result r = UpdateChecker.evaluate("1.0.0", null);
        assertEquals(UpdateChecker.Outcome.INVALID_FETCHED_VERSION, r.outcome);
    }

    @Test
    public void evaluate_emptyTag_invalidFetched() {
        UpdateChecker.Result r = UpdateChecker.evaluate("1.0.0", "");
        assertEquals(UpdateChecker.Outcome.INVALID_FETCHED_VERSION, r.outcome);
    }

    @Test
    public void evaluate_fetchedInvalidTakesPrecedenceOverInvalidCurrent() {
        // fetched is parsed first; both invalid -> fetched wins (matches inline order)
        UpdateChecker.Result r = UpdateChecker.evaluate("debug", "vbad");
        assertEquals(UpdateChecker.Outcome.INVALID_FETCHED_VERSION, r.outcome);
    }

    @Test
    public void evaluate_shorterCurrentTreatedAsZeroPadded_updateAvailable() {
        // current "1" vs fetched "1.0.1": Version pads → 1.0.0 < 1.0.1
        UpdateChecker.Result r = UpdateChecker.evaluate("1", "v1.0.1");
        assertEquals(UpdateChecker.Outcome.UPDATE_AVAILABLE, r.outcome);
        assertEquals("1.0.1", r.newVersionName);
    }

    // ------------------------------------------------------------------
    // countdownSeconds
    // ------------------------------------------------------------------

    @Test
    public void countdownSeconds_fullSecondBoundary() {
        // 5000ms -> 1 + 5 = 6 (the button starts showing "6" then ticks down)
        assertEquals(6, UpdateChecker.countdownSeconds(5000));
    }

    @Test
    public void countdownSeconds_midSecond() {
        // 4500ms -> 1 + 4 = 5
        assertEquals(5, UpdateChecker.countdownSeconds(4500));
    }

    @Test
    public void countdownSeconds_almostZero() {
        // 10ms -> 1 + 0 = 1
        assertEquals(1, UpdateChecker.countdownSeconds(10));
    }

    @Test
    public void countdownSeconds_zero() {
        assertEquals(1, UpdateChecker.countdownSeconds(0));
    }

    @Test
    public void countdownSeconds_tenSeconds() {
        assertEquals(11, UpdateChecker.countdownSeconds(10000));
    }
}

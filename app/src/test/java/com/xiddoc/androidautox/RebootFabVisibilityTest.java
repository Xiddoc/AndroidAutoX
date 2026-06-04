package com.xiddoc.androidautox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link RebootFabVisibility#shouldShow(int, int, boolean)}.
 *
 * <p>Covers the full truth table across the Tweaks page (index 0) and the Logs
 * page ({@link #LOGS_PAGE_INDEX}, index 1) for both reveal states.
 */
public class RebootFabVisibilityTest {

    private static final int TWEAKS_PAGE_INDEX = 0;
    private static final int LOGS_PAGE_INDEX = 1;

    @Test
    public void shownOnTweaksPageWhenRevealed() {
        assertTrue(RebootFabVisibility.shouldShow(TWEAKS_PAGE_INDEX, LOGS_PAGE_INDEX, true));
    }

    @Test
    public void hiddenOnTweaksPageWhenNotRevealed() {
        assertFalse(RebootFabVisibility.shouldShow(TWEAKS_PAGE_INDEX, LOGS_PAGE_INDEX, false));
    }

    @Test
    public void hiddenOnLogsPageWhenRevealed() {
        assertFalse(RebootFabVisibility.shouldShow(LOGS_PAGE_INDEX, LOGS_PAGE_INDEX, true));
    }

    @Test
    public void hiddenOnLogsPageWhenNotRevealed() {
        assertFalse(RebootFabVisibility.shouldShow(LOGS_PAGE_INDEX, LOGS_PAGE_INDEX, false));
    }

    // The following pin the "ONLY the Logs page hides" semantics so a future
    // `<` vs `!=` regression (e.g. hiding every page at-or-after Logs) is caught.

    @Test
    public void shownOnPageAfterLogsWhenRevealed() {
        // Page index 2 is past the Logs page (index 1); it must still show.
        assertTrue(RebootFabVisibility.shouldShow(2, LOGS_PAGE_INDEX, true));
    }

    @Test
    public void shownOnNonZeroNonLogsPageWhenRevealed() {
        // A non-zero page that isn't the Logs page stays shown. With logsPageIndex
        // at 2, page 3 must show even though it is greater than the Logs index.
        assertTrue(RebootFabVisibility.shouldShow(3, 2, true));
    }

    @Test
    public void hiddenOnlyOnTheLogsPageIndex() {
        // Only the exact Logs index hides; neighbours on both sides show.
        int logs = 2;
        assertTrue(RebootFabVisibility.shouldShow(1, logs, true));
        assertFalse(RebootFabVisibility.shouldShow(2, logs, true));
        assertTrue(RebootFabVisibility.shouldShow(3, logs, true));
    }
}

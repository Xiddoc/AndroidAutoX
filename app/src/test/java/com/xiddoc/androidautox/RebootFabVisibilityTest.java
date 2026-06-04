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
}

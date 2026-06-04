package com.xiddoc.androidautox;

/**
 * Pure-Java policy for the floating "Reboot to apply" action button's visibility.
 *
 * <p>The reboot FAB is anchored to the bottom-end of the whole activity, so it
 * floats over every {@code ViewPager} page. On the Logs page it would land on top
 * of the "Copy Logs" button, which is also anchored bottom-end. To avoid that
 * overlap the FAB must only ever be visible on the Tweaks page.
 *
 * <p>Policy: show the FAB only when a reboot has been revealed (i.e. at least one
 * tweak has been applied and the entrance animation has run) <em>and</em> the page
 * the user is currently looking at is not the Logs page.
 *
 * <p>This class has no {@code android.*} dependencies so it can be exercised with
 * plain JUnit, keeping the overlap logic single-sourced and unit-tested.
 */
public final class RebootFabVisibility {

    private RebootFabVisibility() {
        // Utility class; not instantiable.
    }

    /**
     * Decide whether the reboot FAB should currently be shown.
     *
     * @param currentPage    the index of the page the user is currently viewing
     * @param logsPageIndex  the index of the Logs page (the page that hosts
     *                       the overlapping "Copy Logs" button)
     * @param rebootRevealed whether a reboot has been revealed/pending (a tweak
     *                       has been applied and the FAB entrance has run)
     * @return {@code true} only when a reboot is revealed and the current page is
     *         not the Logs page; {@code false} otherwise
     */
    public static boolean shouldShow(int currentPage, int logsPageIndex, boolean rebootRevealed) {
        return rebootRevealed && currentPage != logsPageIndex;
    }
}

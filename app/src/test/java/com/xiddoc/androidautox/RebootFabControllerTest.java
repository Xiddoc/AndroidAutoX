package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Robolectric unit tests for {@link RebootFabController}.
 *
 * <p>Exercises the controller's apply path directly with a real (lightweight)
 * {@link View} and counting {@link Runnable} fakes for the entrance animation and
 * glow, so the behaviour is covered WITHOUT standing up the root-heavy
 * {@code MainActivity}.
 */
@RunWith(RobolectricTestRunner.class)
public class RebootFabControllerTest {

    private static final int TWEAKS = 0;
    private static final int LOGS = 1;

    /** Counts how many times it has been run. */
    private static final class CountingRunnable implements Runnable {
        int count;

        @Override
        public void run() {
            count++;
        }
    }

    private View newFab() {
        Application app = RuntimeEnvironment.getApplication();
        View v = new View(app);
        v.setVisibility(View.GONE);
        return v;
    }

    @Test
    public void revealWhileOnLogs_keepsFabGone_noAnimation() {
        View fab = newFab();
        CountingRunnable entrance = new CountingRunnable();
        CountingRunnable glow = new CountingRunnable();
        RebootFabController c = new RebootFabController(fab, entrance, glow, LOGS, /*initialPage=*/LOGS);

        c.reveal();

        assertEquals(View.GONE, fab.getVisibility());
        assertEquals(0, entrance.count);
        assertEquals(0, glow.count);
    }

    @Test
    public void revealOnLogs_thenReturnToTweaks_showsAndAnimatesOnce() {
        View fab = newFab();
        CountingRunnable entrance = new CountingRunnable();
        CountingRunnable glow = new CountingRunnable();
        RebootFabController c = new RebootFabController(fab, entrance, glow, LOGS, /*initialPage=*/LOGS);

        c.reveal();                  // pending, but hidden on Logs
        c.onPageChanged(TWEAKS);     // now visible + animated for the first time

        assertEquals(View.VISIBLE, fab.getVisibility());
        assertEquals(1, entrance.count);
        assertEquals(1, glow.count);
    }

    @Test
    public void tweaksToLogs_hidesFab() {
        View fab = newFab();
        CountingRunnable entrance = new CountingRunnable();
        CountingRunnable glow = new CountingRunnable();
        RebootFabController c = new RebootFabController(fab, entrance, glow, LOGS, /*initialPage=*/TWEAKS);

        c.reveal();                  // visible on Tweaks
        assertEquals(View.VISIBLE, fab.getVisibility());

        c.onPageChanged(LOGS);       // moving to Logs hides it
        assertEquals(View.GONE, fab.getVisibility());
    }

    @Test
    public void revealOnTweaks_showsAndAnimatesImmediately() {
        View fab = newFab();
        CountingRunnable entrance = new CountingRunnable();
        CountingRunnable glow = new CountingRunnable();
        RebootFabController c = new RebootFabController(fab, entrance, glow, LOGS, /*initialPage=*/TWEAKS);

        c.reveal();

        assertEquals(View.VISIBLE, fab.getVisibility());
        assertEquals(1, entrance.count);
        assertEquals(1, glow.count);
    }

    @Test
    public void animationRunsOnlyOnce_acrossManyPageSwaps() {
        View fab = newFab();
        CountingRunnable entrance = new CountingRunnable();
        CountingRunnable glow = new CountingRunnable();
        RebootFabController c = new RebootFabController(fab, entrance, glow, LOGS, /*initialPage=*/TWEAKS);

        c.reveal();
        c.onPageChanged(LOGS);
        c.onPageChanged(TWEAKS);
        c.onPageChanged(LOGS);
        c.onPageChanged(TWEAKS);

        assertEquals(View.VISIBLE, fab.getVisibility());
        assertEquals(1, entrance.count);
        assertEquals(1, glow.count);
    }

    @Test
    public void restoredOnLogsWithRebootPending_doesNotShow() {
        // Guards the recreate/rotation bug: restored to the Logs page with a
        // reboot pending must NOT re-introduce the Copy Logs overlap.
        View fab = newFab();
        CountingRunnable entrance = new CountingRunnable();
        CountingRunnable glow = new CountingRunnable();
        RebootFabController c = new RebootFabController(fab, entrance, glow, LOGS, /*initialPage=*/LOGS);

        c.restoreRevealed(true);

        assertEquals(View.GONE, fab.getVisibility());
        assertEquals(0, entrance.count);
        assertEquals(0, glow.count);
    }

    @Test
    public void restoredOnTweaksWithRebootPending_showsAndAnimatesOnce() {
        View fab = newFab();
        CountingRunnable entrance = new CountingRunnable();
        CountingRunnable glow = new CountingRunnable();
        RebootFabController c = new RebootFabController(fab, entrance, glow, LOGS, /*initialPage=*/TWEAKS);

        c.restoreRevealed(true);

        assertEquals(View.VISIBLE, fab.getVisibility());
        assertEquals(1, entrance.count);
        assertEquals(1, glow.count);
    }

    @Test
    public void resolveFabRoot_prefersContainer_fallsBackToButton() {
        Application app = RuntimeEnvironment.getApplication();
        View container = new View(app);
        View button = new View(app);

        assertEquals(container, RebootFabController.resolveFabRoot(container, button));
        assertEquals(button, RebootFabController.resolveFabRoot(null, button));
    }

    /**
     * {@code isRebootRevealed} mirrors the reveal flag for state save/restore: it
     * starts false, flips true on {@link RebootFabController#reveal()}, and tracks
     * the value passed to {@link RebootFabController#restoreRevealed(boolean)}.
     */
    @Test
    public void isRebootRevealed_tracksRevealAndRestore() {
        View fab = newFab();
        CountingRunnable entrance = new CountingRunnable();
        CountingRunnable glow = new CountingRunnable();
        RebootFabController c = new RebootFabController(fab, entrance, glow, LOGS, /*initialPage=*/TWEAKS);

        assertFalse("fresh controller has nothing revealed", c.isRebootRevealed());

        c.reveal();
        assertTrue("reveal() sets the revealed flag", c.isRebootRevealed());

        c.restoreRevealed(false);
        assertFalse("restoreRevealed(false) clears the flag", c.isRebootRevealed());

        c.restoreRevealed(true);
        assertTrue("restoreRevealed(true) sets the flag", c.isRebootRevealed());
    }
}

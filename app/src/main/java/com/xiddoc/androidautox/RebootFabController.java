package com.xiddoc.androidautox;

import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Owns the floating "Reboot to apply" action button's view(s) and visibility
 * state, and is the single place that <em>applies</em> the {@link RebootFabVisibility}
 * policy to the real views.
 *
 * <p>Previously the apply logic (VISIBLE/GONE + entrance animation + glow
 * breathing) was duplicated and divergent between {@code MainActivity}'s
 * {@code ViewPager} page-change listener and {@code showRebootButton()}. This
 * controller centralises it so both call sites just delegate:
 * <ul>
 *   <li>{@link #reveal()} — a reboot became pending (a tweak was applied).</li>
 *   <li>{@link #onPageChanged(int)} — the user swiped to another pager page.</li>
 * </ul>
 *
 * <p>The entrance animation and the glow's breathing pulse run <em>exactly once</em>,
 * the first time the FAB actually becomes visible — whether that is immediately
 * (revealed while on the Tweaks page) or deferred (revealed on the Logs page and
 * first shown on returning to Tweaks). This fixes the regression where the glow's
 * first breathing cycle was wasted off-screen and the FAB popped in with no
 * entrance animation.
 *
 * <p><b>Threading:</b> all mutating methods are confined to the UI thread (callers
 * invoke them via {@code runOnUiThread}); the {@code currentPage}/{@code rebootRevealed}
 * fields are therefore not volatile by design. Hence {@link MainThread}.
 *
 * <p>Animations are injected as {@link Runnable}s so the controller carries no
 * direct dependency on the animation framework and can be unit-tested with a
 * lightweight (Robolectric) {@link View} plus plain fakes.
 */
public final class RebootFabController {

    /** The resolved FAB root view (the glow container if present, else the button). */
    @NonNull
    private final View fabRoot;

    /** Starts the entrance animation on the FAB root; run once on first real show. */
    @NonNull
    private final Runnable entranceAnimation;

    /** Starts the glow's breathing pulse; run once on first real show. */
    @NonNull
    private final Runnable startGlow;

    /** Index of the Logs page, where the FAB must stay hidden to avoid overlap. */
    private final int logsPageIndex;

    /** The page the user is currently viewing; the single source of truth for it. */
    private int currentPage;

    /** True once a reboot has been revealed (a tweak applied). */
    private boolean rebootRevealed;

    /** True once the entrance animation + glow have been started (one-shot). */
    private boolean entranceShown;

    /**
     * @param fabRoot           the FAB root view to toggle (glow container, or the
     *                          button itself when there is no container)
     * @param entranceAnimation runs the FAB entrance animation; invoked once, the
     *                          first time the FAB actually becomes visible
     * @param startGlow         starts the glow breathing pulse; invoked once,
     *                          together with {@code entranceAnimation}
     * @param logsPageIndex     index of the Logs page (FAB stays hidden there)
     * @param initialPage       the page currently shown (from the pager), so a
     *                          restored Logs page is honoured immediately
     */
    public RebootFabController(@NonNull View fabRoot,
                               @NonNull Runnable entranceAnimation,
                               @NonNull Runnable startGlow,
                               int logsPageIndex,
                               int initialPage) {
        this.fabRoot = fabRoot;
        this.entranceAnimation = entranceAnimation;
        this.startGlow = startGlow;
        this.logsPageIndex = logsPageIndex;
        this.currentPage = initialPage;
    }

    /** Whether a reboot has been revealed (mainly for state save/restore). */
    public boolean isRebootRevealed() {
        return rebootRevealed;
    }

    /**
     * Restore the reveal state across an activity recreate, then reconcile the
     * FAB's visibility against the current page. No animation replays here unless
     * the FAB genuinely becomes visible for the first time.
     */
    @MainThread
    public void restoreRevealed(boolean revealed) {
        this.rebootRevealed = revealed;
        apply();
    }

    /**
     * A reboot is now pending. Remember it and apply the policy; if the FAB
     * should show now (i.e. not on the Logs page) it animates in immediately,
     * otherwise it stays hidden until {@link #onPageChanged(int)} brings the user
     * back to a non-Logs page.
     */
    @MainThread
    public void reveal() {
        this.rebootRevealed = true;
        apply();
    }

    /**
     * The user moved to {@code page}. Track it (single source of truth) and
     * re-apply the policy so the FAB appears/disappears as needed.
     */
    @MainThread
    public void onPageChanged(int page) {
        this.currentPage = page;
        apply();
    }

    /**
     * Apply {@link RebootFabVisibility#shouldShow} to the real view. The first
     * time the FAB actually becomes visible, fire the entrance animation and the
     * glow breathing exactly once.
     */
    @MainThread
    private void apply() {
        boolean show = RebootFabVisibility.shouldShow(currentPage, logsPageIndex, rebootRevealed);
        if (show) {
            fabRoot.setVisibility(View.VISIBLE);
            if (!entranceShown) {
                entranceShown = true;
                entranceAnimation.run();
                startGlow.run();
            }
        } else {
            fabRoot.setVisibility(View.GONE);
        }
    }

    /** Resolve the FAB root: prefer the glow container, fall back to the button. */
    @NonNull
    public static View resolveFabRoot(@Nullable View container, @NonNull View button) {
        return container != null ? container : button;
    }
}

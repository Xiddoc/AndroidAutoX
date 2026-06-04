package com.xiddoc.androidautox;

/**
 * Pure, Android-free decision logic for the root-acquisition "proceed" gate used by
 * {@link SplashActivity}.
 *
 * <p>The splash screen kicks off an asynchronous root request and the user can tap
 * "Proceed" at any time. Three inputs decide what should happen:
 * <ul>
 *   <li>{@code requestComplete} — has the async root request finished yet?</li>
 *   <li>{@code rootGranted} — did the (completed) request actually obtain root?</li>
 * </ul>
 *
 * <p>Keeping this logic here (with no Android imports) makes it unit-testable with plain
 * JUnit; the Android glue in {@link SplashActivity}/{@link NoRootDialog} stays thin.
 */
public final class RootGate {

    private RootGate() {
    }

    /** What the UI should do given the current root-request state. */
    public enum Decision {
        /** Root is confirmed granted: continue into the app. */
        PROCEED,
        /** The async root request is still running: keep waiting (show a loading state). */
        WAIT,
        /** The request finished without root: offer the user a way to retry. */
        SHOW_RETRY
    }

    /**
     * Decides what the proceed gate should do.
     *
     * @param requestComplete whether the asynchronous root request has finished
     * @param rootGranted     whether root was actually granted (only meaningful once complete)
     * @return the {@link Decision} the UI should act on
     */
    public static Decision decide(boolean requestComplete, boolean rootGranted) {
        if (!requestComplete) {
            return Decision.WAIT;
        }
        return rootGranted ? Decision.PROCEED : Decision.SHOW_RETRY;
    }

    /**
     * Convenience overload that mirrors how {@link SplashActivity} stores its result: a
     * nullable "rooted" flag where {@code null} means the async request has not finished,
     * {@code Boolean.TRUE} means root granted and {@code Boolean.FALSE} means denied.
     *
     * @param rooted tri-state root result ({@code null} = pending)
     * @return the {@link Decision} the UI should act on
     */
    public static Decision decide(Boolean rooted) {
        return decide(rooted != null, Boolean.TRUE.equals(rooted));
    }
}

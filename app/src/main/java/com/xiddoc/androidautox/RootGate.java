package com.xiddoc.androidautox;

/**
 * Pure, Android-free decision logic for the root-acquisition "proceed" gate used by
 * {@link SplashActivity}.
 *
 * <p>The splash screen kicks off an asynchronous root request and the user can tap
 * "Proceed" at any time. The request has three observable states — still running,
 * finished with root, finished without root — and the UI must react differently to
 * each. Conflating "still running" with "denied" is exactly the original race bug
 * this class guards against.
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
     * Core decision. This two-boolean form is the primitive the logic is expressed in and
     * the one the unit tests drive directly: it is impossible to express an illegal state
     * ({@code rootGranted} is simply ignored while {@code requestComplete} is false), so
     * every input combination has a well-defined output.
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
     * Convenience overload that mirrors exactly how {@link SplashActivity} stores its
     * result: a single nullable "rooted" flag standing in for the three caller states
     * (pending / granted / denied). This exists so callers don't have to unpack the flag
     * into two booleans (and risk getting the {@code null} case wrong — the original bug)
     * at every call site.
     *
     * @param rooted tri-state root result: {@code null} = pending, {@code TRUE} = granted,
     *               {@code FALSE} = denied
     * @return the {@link Decision} the UI should act on
     */
    public static Decision decide(Boolean rooted) {
        return decide(rooted != null, Boolean.TRUE.equals(rooted));
    }
}

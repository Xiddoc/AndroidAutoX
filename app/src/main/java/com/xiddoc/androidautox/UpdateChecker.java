package com.xiddoc.androidautox;

import com.xiddoc.androidautox.Utils.Version;

/**
 * Pure update-availability decision extracted from {@code SplashActivity}'s
 * {@code requestLatest()} response handler, plus the disclaimer countdown text
 * formatting. {@code SplashActivity} is excluded from the coverage gate; this
 * helper carries the real branching so it can be unit-tested without Volley or the
 * Activity.
 *
 * <p>The Activity feeds the GitHub release {@code tag_name} (e.g. {@code "v1.2.3"})
 * and its own {@code BuildConfig.VERSION_NAME} in, then reacts to the
 * {@link Outcome} (set the new-version banner, or show the matching toast).
 */
public final class UpdateChecker {

    /** Which branch the update check landed in — drives the Activity's UI reaction. */
    public enum Outcome {
        /** A strictly newer release exists; {@link Result#newVersionName} is set. */
        UPDATE_AVAILABLE,
        /** The installed build is already current (or newer); no banner, no toast. */
        UP_TO_DATE,
        /** The installed version string was unparseable (e.g. a debug build). */
        INVALID_CURRENT_VERSION,
        /** The release tag was missing/unparseable (treated like a fetch failure). */
        INVALID_FETCHED_VERSION
    }

    /** Outcome plus the resolved new-version name (null unless {@link Outcome#UPDATE_AVAILABLE}). */
    public static final class Result {
        public final Outcome outcome;
        public final String newVersionName;

        Result(Outcome outcome, String newVersionName) {
            this.outcome = outcome;
            this.newVersionName = newVersionName;
        }
    }

    private UpdateChecker() {
    }

    /**
     * Strip the leading {@code 'v'} (or any single prefix char) off a GitHub release
     * tag — mirrors the inline {@code fetchedVersion.substring(1)}. Returns
     * {@code null} for null/empty input so callers don't NPE on a missing tag.
     */
    public static String stripTagPrefix(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return null;
        }
        return tagName.substring(1);
    }

    /**
     * Decide whether {@code fetchedTag} represents a newer release than
     * {@code currentVersion}.
     *
     * <p>Mirrors {@code SplashActivity.requestLatest()}'s handler:
     * <ul>
     *   <li>parse the fetched tag (after stripping its prefix); if it is missing or
     *       malformed → {@link Outcome#INVALID_FETCHED_VERSION} (the old code's
     *       {@code JSONException}/"could not check" path),</li>
     *   <li>parse the current version; if it is malformed → {@link
     *       Outcome#INVALID_CURRENT_VERSION} (the old "Debug build" path),</li>
     *   <li>if {@code current.compareTo(fetched) == -1} (strictly older) →
     *       {@link Outcome#UPDATE_AVAILABLE} carrying the stripped tag,</li>
     *   <li>otherwise → {@link Outcome#UP_TO_DATE}.</li>
     * </ul>
     */
    public static Result evaluate(String currentVersion, String fetchedTag) {
        String stripped = stripTagPrefix(fetchedTag);
        Version fetched;
        try {
            fetched = new Version(stripped);
        } catch (IllegalArgumentException e) {
            return new Result(Outcome.INVALID_FETCHED_VERSION, null);
        }

        Version current;
        try {
            current = new Version(currentVersion);
        } catch (IllegalArgumentException e) {
            return new Result(Outcome.INVALID_CURRENT_VERSION, null);
        }

        // Pin the original "== -1" comparison (strictly older), not "< 0".
        if (current.compareTo(fetched) == -1) {
            return new Result(Outcome.UPDATE_AVAILABLE, stripped);
        }
        return new Result(Outcome.UP_TO_DATE, null);
    }

    /**
     * Seconds shown on a disclaimer countdown button for a given remaining-ms value.
     * Mirrors the inline {@code (int)(1 + (millisUntilFinished / 1000))} used by both
     * countdown timers so the label math is testable in isolation.
     */
    public static int countdownSeconds(long millisUntilFinished) {
        return (int) (1 + (millisUntilFinished / 1000));
    }
}

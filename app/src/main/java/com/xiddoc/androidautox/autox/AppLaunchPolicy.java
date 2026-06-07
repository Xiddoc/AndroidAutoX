package com.xiddoc.androidautox.autox;

/**
 * Pure decision logic for launching a target app onto a specific virtual display via
 * {@code ActivityOptions.setLaunchDisplayId}.
 *
 * <p>All methods are {@code static} and have no side-effects — they only compute and
 * return values. No Android framework objects are created here, so every method can
 * be exercised in a plain JUnit test on the JVM without Robolectric.
 *
 * <p>The intent flags returned by {@link #launchIntentFlags()} are the raw integer
 * values defined by {@code android.content.Intent}. Using their literal values avoids
 * an Android-runtime dependency while remaining fully compatible with production code
 * that passes the result directly to {@code Intent.setFlags(int)}.
 */
public final class AppLaunchPolicy {

    /**
     * {@code android.content.Intent.FLAG_ACTIVITY_NEW_TASK} — numeric value {@code 0x10000000}.
     *
     * <p>Required when starting an activity from outside an existing activity task (e.g.
     * from a service or a non-UI context). On a secondary display this flag ensures
     * Android creates a new task root for the launched app rather than trying to attach
     * it to the caller's task, which would fail without a valid task affinity match.
     */
    static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;

    /**
     * {@code android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK} — numeric value {@code 0x08000000}.
     *
     * <p>Paired with {@link #FLAG_ACTIVITY_NEW_TASK} to force a fresh task even when the
     * target app already has a running task. Without this flag, Android would bring the
     * existing task to the foreground instead of creating an isolated execution context
     * on the target virtual display. Used here to guarantee each projection session gets
     * its own independent task stack on display {@code displayId}.
     */
    static final int FLAG_ACTIVITY_MULTIPLE_TASK = 0x08000000;

    private AppLaunchPolicy() {
    }

    /**
     * Returns the intent flags that must be set on every app-launch intent directed at
     * a virtual display.
     *
     * <ul>
     *   <li>{@code FLAG_ACTIVITY_NEW_TASK} (0x10000000) — required to start an activity
     *       outside the caller's task, which is the only valid mode when targeting a
     *       secondary display from a non-activity context.</li>
     *   <li>{@code FLAG_ACTIVITY_MULTIPLE_TASK} (0x08000000) — forces a new task even when
     *       the package is already running, isolating each display's execution context.</li>
     * </ul>
     *
     * @return the combined flag bitmask to pass to {@code Intent.setFlags(int)}.
     */
    public static int launchIntentFlags() {
        return FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK;
    }

    /**
     * Determines whether a package can legitimately be launched onto the given display.
     *
     * <p>Returns {@code true} only when both conditions hold:
     * <ol>
     *   <li>{@code packageName} is non-null and non-blank.</li>
     *   <li>{@code displayId} is strictly greater than 0. Display 0 is the default
     *       (primary) display; the AutoX projection layer must always target a secondary
     *       virtual display to avoid overwriting the device's main screen.</li>
     * </ol>
     *
     * @param packageName the Android package name of the app to launch; may be null.
     * @param displayId   the target virtual display identifier; must be &gt; 0.
     * @return {@code true} iff the launch parameters are valid.
     */
    public static boolean canLaunch(String packageName, int displayId) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        return displayId > 0;
    }

    /**
     * Immutable value object that pairs a validated package name with a validated
     * display id, forming a ready-to-use launch request.
     *
     * <p>Use {@link #of(String, int)} to construct an instance; the factory performs
     * the same validation as {@link AppLaunchPolicy#canLaunch} and throws on failure
     * so callers never hold an invalid request.
     */
    public static final class LaunchRequest {

        /** Android package name of the app to launch. Never null or blank. */
        public final String packageName;

        /**
         * Target virtual display id. Always &gt; 0 (display 0 is the primary display
         * and is never a valid AutoX target).
         */
        public final int displayId;

        private LaunchRequest(String packageName, int displayId) {
            this.packageName = packageName;
            this.displayId = displayId;
        }

        /**
         * Factory that constructs a {@link LaunchRequest} after validating both
         * parameters against the {@link AppLaunchPolicy#canLaunch} rules.
         *
         * @param packageName non-null, non-blank Android package name.
         * @param displayId   virtual display id; must be &gt; 0.
         * @return a validated, immutable {@link LaunchRequest}.
         * @throws IllegalArgumentException if {@code packageName} is null or blank, or if
         *                                  {@code displayId} is &le; 0.
         */
        public static LaunchRequest of(String packageName, int displayId) {
            if (packageName == null || packageName.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "packageName must be non-null and non-blank");
            }
            if (displayId <= 0) {
                throw new IllegalArgumentException(
                        "displayId must be > 0 (primary display 0 is never a valid AutoX target); "
                                + "got: " + displayId);
            }
            return new LaunchRequest(packageName, displayId);
        }

        /**
         * Returns {@code true} when {@code o} is a {@link LaunchRequest} with the same
         * {@link #packageName} and {@link #displayId}.
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LaunchRequest)) return false;
            LaunchRequest other = (LaunchRequest) o;
            return displayId == other.displayId && packageName.equals(other.packageName);
        }

        /** Hash code consistent with {@link #equals}. */
        @Override
        public int hashCode() {
            return 31 * packageName.hashCode() + displayId;
        }

        /**
         * Returns a developer-friendly representation:
         * {@code LaunchRequest{packageName='...', displayId=...}}.
         */
        @Override
        public String toString() {
            return "LaunchRequest{packageName='" + packageName + "', displayId=" + displayId + "}";
        }
    }
}

package com.xiddoc.androidautox;

import android.content.Intent;

/**
 * Pure predicate extracted from {@link BootReceiver}: decides whether a received
 * broadcast action should trigger re-establishing the re-apply job.
 *
 * <p>The {@link BootReceiver} shell stays a thin framework wrapper that simply
 * routes to the scheduler when this returns true, keeping the boot-trigger
 * decision unit-testable without a real broadcast.
 *
 * <p>(Named to avoid the {@code BootReceiver*} coverage-exclusion glob so this
 * helper is measured and held to 100%.)
 */
public final class BootScheduleGate {

    private BootScheduleGate() {}

    /** The lock-screen-time boot-completed action (not a public constant pre-N). */
    static final String ACTION_LOCKED_BOOT_COMPLETED =
            "android.intent.action.LOCKED_BOOT_COMPLETED";

    /**
     * True if the given broadcast action is one that should re-establish the
     * periodic re-apply job and trigger a one-shot re-apply.
     */
    public static boolean shouldSchedule(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
    }

    /**
     * Convenience over {@link #shouldSchedule(String)} that tolerates a null
     * intent (as a real {@code onReceive} can, in theory, hand us one).
     */
    public static boolean shouldSchedule(Intent intent) {
        return intent != null && shouldSchedule(intent.getAction());
    }

    /**
     * True only for a genuine device reboot ({@code BOOT_COMPLETED} /
     * {@code LOCKED_BOOT_COMPLETED}) — NOT an in-place app update
     * ({@code MY_PACKAGE_REPLACED}). A reboot is what satisfies a per-tweak
     * "reboot pending" marker, so the receiver clears those markers only when
     * this is true.
     */
    public static boolean isReboot(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || ACTION_LOCKED_BOOT_COMPLETED.equals(action);
    }

    /** Null-tolerant overload of {@link #isReboot(String)}. */
    public static boolean isReboot(Intent intent) {
        return intent != null && isReboot(intent.getAction());
    }
}

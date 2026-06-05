package com.xiddoc.androidautox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Re-establishes the periodic re-apply job after a reboot (or in-place app update) and
 * triggers one prompt re-apply, so enabled tweaks come back without the user opening the app.
 *
 * <p>Additionally, on a genuine device reboot ({@code BOOT_COMPLETED} /
 * {@code LOCKED_BOOT_COMPLETED}) it clears the per-tweak "reboot pending" markers, since a
 * reboot is exactly what those markers were waiting for. An in-place app update
 * ({@code MY_PACKAGE_REPLACED}) is deliberately NOT treated as a reboot for this purpose.
 *
 * <p>The action-routing decisions live in the unit-tested {@link BootScheduleGate} so this
 * framework shell stays thin.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!BootScheduleGate.shouldSchedule(intent)) return;

        // Only a genuine device reboot satisfies a "reboot pending" (yellow) marker.
        // An in-place app update (MY_PACKAGE_REPLACED) is NOT a reboot — clearing the
        // markers there would prematurely turn a not-yet-rebooted tweak green. So we clear
        // them ONLY on BOOT_COMPLETED / LOCKED_BOOT_COMPLETED, after which a tweak that was
        // yellow can resolve to green (or red only if its flags actually drifted) once
        // MainActivity reconciles against the phenotype DB.
        if (BootScheduleGate.isReboot(intent)) {
            TweakStateStore store = new TweakStateStore(ctx);
            for (String key : TweakRegistry.ALL_KEYS) {
                store.setRebootPending(key, false);
            }
        }

        // Re-establish the periodic re-apply job and kick one prompt re-apply for all three
        // actions (including MY_PACKAGE_REPLACED), so enabled tweaks come back without the
        // user opening the app.
        ReapplyScheduler.sync(ctx);
        ReapplyScheduler.runOnceSoon(ctx);
    }
}

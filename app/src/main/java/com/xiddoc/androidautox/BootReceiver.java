package com.xiddoc.androidautox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Re-establishes the periodic re-apply job after a reboot and triggers one prompt
 * re-apply, so enabled tweaks come back without the user opening the app.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            // A reboot has occurred, so every "reboot pending" (yellow) marker is now satisfied:
            // clear them so a tweak that was yellow can resolve to green (or red only if its flags
            // actually drifted) once MainActivity reconciles against the phenotype DB.
            TweakStateStore store = new TweakStateStore(ctx);
            for (String key : TweakRegistry.ALL_KEYS) {
                store.setRebootPending(key, false);
            }
            ReapplyScheduler.sync(ctx);
            ReapplyScheduler.runOnceSoon(ctx);
        }
    }
}

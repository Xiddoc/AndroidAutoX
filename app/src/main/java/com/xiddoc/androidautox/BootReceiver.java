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
            ReapplyScheduler.sync(ctx);
            ReapplyScheduler.runOnceSoon(ctx);
        }
    }
}

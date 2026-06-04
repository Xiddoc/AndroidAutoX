package com.xiddoc.androidautox;

import android.app.Application;

import com.topjohnwu.superuser.Shell;

/**
 * Application entry point. Configures libsu's global root shell (one persistent
 * mount-master root shell, with a timeout) before anything requests root, and wires
 * up {@link RootDb} so the {@link PhixitRootService} can be bound on demand.
 */
public class AaxApp extends Application {

    static {
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(30));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        RootDb.init(this);
    }
}

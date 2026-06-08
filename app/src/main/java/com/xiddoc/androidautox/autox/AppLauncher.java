package com.xiddoc.androidautox.autox;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.util.Log;

/**
 * Launches a target app onto a specific virtual display using only native Android APIs.
 *
 * <h2>Launch pipeline</h2>
 * <ol>
 *   <li>Validates the request via {@link AppLaunchPolicy#canLaunch(String, int)}.</li>
 *   <li>Obtains the launch {@link Intent} via
 *       {@link PackageManager#getLaunchIntentForPackage(String)}.</li>
 *   <li>Applies the required flags via {@link AppLaunchPolicy#launchIntentFlags()}.</li>
 *   <li>Builds {@link ActivityOptions#setLaunchDisplayId(int)} to target the virtual
 *       display.</li>
 *   <li>Calls {@link Context#startActivity(Intent, android.os.Bundle)}.</li>
 * </ol>
 *
 * <h2>No-launch-intent handling</h2>
 * <p>When {@link PackageManager#getLaunchIntentForPackage} returns {@code null} (the
 * package is not installed or has no launchable activity), this class logs a warning
 * and returns {@code false} — it does <em>not</em> throw. Callers can inspect the
 * return value and surface an appropriate message in the UI.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>No shell commands, no {@code Runtime.exec}: all launch logic uses the
 *       {@link ActivityOptions} API introduced in API 26.</li>
 *   <li>This class is framework-coupled and excluded from the JaCoCo coverage gate.
 *       All decision logic (flag values, policy) lives in the testable
 *       {@link AppLaunchPolicy} class.</li>
 * </ul>
 */
public final class AppLauncher {

    private static final String TAG = "AndroidAutoX";

    private final Context context;

    /**
     * Constructs an {@code AppLauncher}.
     *
     * @param context an application or service {@link Context} used to obtain the
     *                {@link PackageManager} and to call {@code startActivity}
     */
    public AppLauncher(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.context = context.getApplicationContext();
    }

    /**
     * Attempts to launch the given package onto the specified virtual display.
     *
     * <p>Returns {@code false} (with a warning logged) if:
     * <ul>
     *   <li>{@link AppLaunchPolicy#canLaunch(String, int)} returns {@code false}
     *       (invalid package name or display id &le; 0).</li>
     *   <li>The {@link PackageManager} has no launch intent for the package (package
     *       not installed or has no main activity).</li>
     * </ul>
     *
     * @param packageName the Android package name of the app to launch
     * @param displayId   the virtual display id (&gt; 0) to launch onto
     * @return {@code true} if {@code startActivity} was called; {@code false} otherwise
     */
    public boolean launch(String packageName, int displayId) {
        return launch(packageName, displayId, null);
    }

    /**
     * Attempts to launch the given package onto the specified virtual display, optionally
     * constraining the launched window to {@code launchBounds} (the WS3 forced-vertical case).
     *
     * <p>When {@code launchBounds} is non-null it is applied via
     * {@link ActivityOptions#setLaunchBounds(Rect)} — this is only honored by the platform when
     * freeform windowing is enabled (see {@link FreeformGlobalSettingsSpec#KEY_ENABLE_FREEFORM}),
     * which AutoX turns on at projection start. When {@code null}, the app launches full-display
     * (the original no-bounds behaviour). The {@link Rect} itself is built in this excluded glue
     * from the pure {@link LaunchBoundsCalculator.Bounds} produced by
     * {@link LaunchBoundsCalculator#forcedVertical}; no bounds <em>policy</em> lives here.
     *
     * <p>Returns {@code false} (with a warning logged) under the same conditions as
     * {@link #launch(String, int)} (policy rejection or no launch intent).
     *
     * @param packageName  the Android package name of the app to launch
     * @param displayId    the virtual display id (&gt; 0) to launch onto
     * @param launchBounds the window bounds to request, or {@code null} for full-display
     * @return {@code true} if {@code startActivity} was called; {@code false} otherwise
     */
    public boolean launch(String packageName, int displayId, Rect launchBounds) {
        if (!AppLaunchPolicy.canLaunch(packageName, displayId)) {
            Log.w(TAG, "AppLauncher.launch: policy rejected launch of '"
                    + packageName + "' on display " + displayId);
            return false;
        }

        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent == null) {
            Log.w(TAG, "AppLauncher.launch: no launch intent for package '" + packageName
                    + "' — app may not be installed or has no launchable activity");
            return false;
        }

        intent.setFlags(AppLaunchPolicy.launchIntentFlags());

        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        if (launchBounds != null) {
            // Honored only when freeform windowing is enabled (FreeformGlobalSettingsSpec).
            options.setLaunchBounds(launchBounds);
        }

        Log.d(TAG, "AppLauncher: launching '" + packageName + "' on display " + displayId
                + (launchBounds != null ? " bounds=" + launchBounds : ""));
        context.startActivity(intent, options.toBundle());
        return true;
    }
}

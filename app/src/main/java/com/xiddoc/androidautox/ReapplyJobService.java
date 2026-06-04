package com.xiddoc.androidautox;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Background job that re-asserts the user's enabled tweaks if GMS has re-synced and
 * overwritten them. Runs headlessly via the extracted {@link PhixitEngine}; only
 * touches the DB (and restarts GMS) when the flags have actually drifted, so the
 * common "nothing changed" case is a cheap read-only check.
 */
public class ReapplyJobService extends JobService {

    static final String TAG = "AndroidAutoX";
    public static final int JOB_ID = 0xAA70;
    public static final int JOB_ID_ONESHOT = 0xAA71;

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread() {
            @Override
            public void run() {
                try {
                    reapplyNow(getApplicationContext());
                } catch (Throwable t) {
                    Log.e(TAG, "re-apply failed", t);
                }
                jobFinished(params, false);
            }
        }.start();
        return true; // work continues on the background thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // reschedule if interrupted
    }

    /** Re-applies enabled tweaks if drifted. Safe to call from any background context. */
    static void reapplyNow(Context ctx) {
        if (!ReapplyScheduler.isAutoReapplyEnabled(ctx)) return;
        List<FlagSpec> specs = TweakRegistry.enabledSpecs(ctx);
        if (specs.isEmpty()) return;

        StringBuilder log = new StringBuilder();
        PhixitEngine engine = new PhixitEngine(ctx, log);
        // Cheap read-only drift check first: no GMS restart, no SELinux change. The
        // common "nothing changed" case bails out here.
        if (engine.isApplied(specs)) {
            Log.i(TAG, "Tweaks still applied (" + specs.size() + " flags); nothing to do.");
            return;
        }

        // Drift detected: re-applying requires force-stopping GMS, which restarts
        // Android Auto. If the user is actively projecting (e.g. driving), defer rather
        // than yank the screen out from under them — a tweak being briefly un-applied is
        // far less bad than Android Auto restarting mid-drive.
        if (isAndroidAutoProjecting(ctx)) {
            Log.i(TAG, "Drift detected but Android Auto is projecting; deferring re-apply.");
            // The periodic job retries on its own 6h cadence, but the one-shot path (e.g.
            // post-boot) has no follow-up of its own, so queue another short-latency
            // attempt. JobScheduler de-dupes by JOB_ID_ONESHOT, so re-queuing from the
            // periodic path is harmless.
            ReapplyScheduler.runOnceSoon(ctx);
            return;
        }

        boolean ok = engine.applySpecs(specs, false); // don't recapture baselines
        Log.i(TAG, "Re-applied " + specs.size() + " flags, ok=" + ok + "\n" + log);
    }

    /**
     * Best-effort root detector for "Android Auto is actively projecting right now".
     *
     * <p>There is no perfect signal, so we use the most reliable one available: the
     * gearhead app hosts a <em>foreground</em> service (the car projection / connection
     * service) for the entire duration of an active projection session, and tears it down
     * when the head unit disconnects. We dump gearhead's services and look for a live
     * foreground-service entry:
     *
     * <pre>dumpsys activity services com.google.android.projection.gearhead</pre>
     *
     * and scan the output for the foreground-service markers {@code ActiveServices} prints
     * while one is running ({@code isForeground=true} on modern Android, or a
     * {@code foregroundServiceType=...} line). We chose {@code dumpsys activity services}
     * scoped to the gearhead package over the alternatives because:
     * <ul>
     *   <li>{@code dumpsys activity activities} (RESUMED activity) misses the very common
     *       case where the phone screen is off/locked while still projecting to the car;</li>
     *   <li>a bare "is the process running" check is a false positive — gearhead's process
     *       lingers cached long after a drive ends;</li>
     *   <li>{@code dumpsys car_service} is absent on most non-AAOS phones.</li>
     * </ul>
     * The foreground-service lifetime tracks the projection session closely, making it the
     * most faithful root-visible proxy.
     *
     * <p><b>Conservative default:</b> if the command is unavailable, returns nothing, or
     * errors, we return {@code true} (treat as "projecting" and defer). The cost of a false
     * "projecting" is only a delayed re-apply; the cost of a false "not projecting" is
     * restarting Android Auto mid-drive, which we must avoid.
     */
    static boolean isAndroidAutoProjecting(Context ctx) {
        StreamLogs r = MainActivity.runSuWithCmd(
                "dumpsys activity services " + FlagSpec.PKG_GEARHEAD);
        String out = r.getInputStreamLog();

        // No root / command missing / errored -> we cannot tell, so be conservative.
        if (out.isEmpty() || !r.getErrorStreamLog().isEmpty()) {
            Log.w(TAG, "Projection check inconclusive (empty/error output); assuming projecting.");
            return true;
        }

        String lower = out.toLowerCase();
        // "(nothing)" is what dumpsys prints when gearhead has no running services at all,
        // i.e. it is definitely not projecting.
        if (lower.contains("(nothing)")) return false;

        // A live foreground service is the projection-session marker. Require an explicit
        // foreground indicator so a merely cached gearhead process with idle services
        // doesn't count as projecting.
        if (lower.contains("isforeground=true") || lower.contains("foregroundservicetype")) {
            return true;
        }

        // Service records exist but none is foreground: gearhead is up but not actively
        // projecting, so allow the normal background re-apply to proceed. (Genuinely
        // unparseable output would have returned true above.)
        return false;
    }
}

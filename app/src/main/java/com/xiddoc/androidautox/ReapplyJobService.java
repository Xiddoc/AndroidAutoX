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
        if (engine.isApplied(specs)) {
            Log.i(TAG, "Tweaks still applied (" + specs.size() + " flags); nothing to do.");
            return;
        }
        boolean ok = engine.applySpecs(specs, false); // don't recapture baselines
        Log.i(TAG, "Re-applied " + specs.size() + " flags, ok=" + ok + "\n" + log);
    }
}

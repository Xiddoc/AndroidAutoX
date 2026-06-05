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

    /**
     * Re-applies enabled tweaks if drifted. Safe to call from any background context.
     *
     * <p>All branching lives in the pure {@link ReapplyDecision}; this only wires the
     * production collaborators (root engine + scheduler) into its {@link
     * ReapplyDecision.Env} seam. The "drift detected but projecting" path force-stops
     * GMS otherwise, so deferring is intentional — see {@link
     * ReapplyScheduler#scheduleDeferredRetry}.
     */
    static void reapplyNow(Context ctx) {
        final Context app = ctx.getApplicationContext();
        final StringBuilder log = new StringBuilder();
        final PhixitEngine engine = new PhixitEngine(app, log);

        // Diagnostic detail captured as the decision runs (log-only; does not affect
        // control flow or the ReapplyDecision/Outcome model). Mirrors the per-path
        // detail the pre-extraction log carried: the enabled-spec count, and for the
        // re-applied path the apply result boolean.
        final int[] specCount = {0};
        final boolean[] applyResult = {false};
        final boolean[] applied = {false};

        ReapplyDecision.Outcome outcome = ReapplyDecision.evaluate(new ReapplyDecision.Env() {
            @Override
            public boolean isAutoReapplyEnabled() {
                return ReapplyScheduler.isAutoReapplyEnabled(app);
            }

            @Override
            public List<FlagSpec> enabledSpecs() {
                List<FlagSpec> specs = TweakRegistry.enabledSpecs(app);
                specCount[0] = specs == null ? 0 : specs.size();
                return specs;
            }

            @Override
            public boolean isApplied(List<FlagSpec> specs) {
                return engine.isApplied(specs);
            }

            @Override
            public boolean isAndroidAutoProjecting() {
                return engine.isAndroidAutoProjecting();
            }

            @Override
            public void scheduleDeferredRetry() {
                ReapplyScheduler.scheduleDeferredRetry(app);
            }

            @Override
            public boolean applySpecs(List<FlagSpec> specs) {
                boolean ok = engine.applySpecs(specs, false); // don't recapture baselines
                applyResult[0] = ok;
                applied[0] = true;
                return ok;
            }
        });
        String detail = "specs=" + specCount[0];
        if (applied[0]) {
            detail += ", ok=" + applyResult[0];
        }
        Log.i(TAG, "Re-apply outcome=" + outcome + " (" + detail + ")\n" + log);
    }
}

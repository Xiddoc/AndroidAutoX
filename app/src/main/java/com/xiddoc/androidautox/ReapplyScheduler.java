package com.xiddoc.androidautox;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Schedules the periodic background re-apply job. The job is persisted (survives
 * reboot) and only exists while auto-reapply is on and at least one tweak is enabled.
 */
public final class ReapplyScheduler {

    private ReapplyScheduler() {}

    public static final String PREF_AUTO = "auto_reapply_enabled";

    /** Periodic interval. Kept modest to limit how often GMS might be restarted. */
    static final long INTERVAL_MS = 6L * 60 * 60 * 1000; // 6 hours

    public static boolean isAutoReapplyEnabled(Context ctx) {
        return prefs(ctx).getBoolean(PREF_AUTO, true); // on by default
    }

    public static void setAutoReapplyEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(PREF_AUTO, on).apply();
        sync(ctx);
    }

    /** Schedule or cancel the periodic job to match the current state. */
    public static void sync(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (isAutoReapplyEnabled(ctx) && TweakRegistry.anyEnabled(ctx)) {
            JobInfo job = new JobInfo.Builder(
                    ReapplyJobService.JOB_ID, new ComponentName(ctx, ReapplyJobService.class))
                    .setPersisted(true)
                    .setPeriodic(INTERVAL_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .build();
            js.schedule(job);
        } else {
            js.cancel(ReapplyJobService.JOB_ID);
        }
    }

    /** Re-check interval after a re-apply was deferred (AA projecting). Modest, not a loop. */
    static final long RETRY_LATENCY_MS = 15L * 60 * 1000; // 15 minutes

    /** One-shot re-apply shortly from now (e.g. right after boot). */
    public static void runOnceSoon(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!(isAutoReapplyEnabled(ctx) && TweakRegistry.anyEnabled(ctx))) return;
        JobInfo job = new JobInfo.Builder(
                ReapplyJobService.JOB_ID_ONESHOT, new ComponentName(ctx, ReapplyJobService.class))
                .setMinimumLatency(10_000)
                .setOverrideDeadline(120_000)
                .build();
        js.schedule(job);
    }

    /**
     * Schedules a single deferred re-check ~{@link #RETRY_LATENCY_MS} out, used when a
     * re-apply was postponed because Android Auto is projecting. Shares JOB_ID_ONESHOT so
     * repeated deferrals collapse into one pending job (no job storm); the cadence stays at
     * the retry interval rather than the boot-path's short latency.
     */
    public static void scheduleDeferredRetry(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!(isAutoReapplyEnabled(ctx) && TweakRegistry.anyEnabled(ctx))) return;
        JobInfo job = new JobInfo.Builder(
                ReapplyJobService.JOB_ID_ONESHOT, new ComponentName(ctx, ReapplyJobService.class))
                .setMinimumLatency(RETRY_LATENCY_MS)
                .setOverrideDeadline(2 * RETRY_LATENCY_MS)
                .build();
        js.schedule(job);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE);
    }
}

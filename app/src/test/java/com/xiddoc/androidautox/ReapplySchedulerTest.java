package com.xiddoc.androidautox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;

/**
 * Plain-JUnit + Mockito tests for {@link ReapplyScheduler}.
 *
 * <p>Deliberately avoids the Robolectric runner: in this environment Robolectric's
 * sandbox classloader drops JaCoCo's on-the-fly probes, zeroing coverage. Instead
 * the Android collaborators are mocked directly — {@code Context},
 * {@code SharedPreferences}, {@code JobScheduler} — and {@code new JobInfo.Builder}/
 * {@code new ComponentName} are intercepted with {@link Mockito#mockConstruction},
 * so {@link ReapplyScheduler}'s real body executes under the system classloader and
 * its probes register. We then verify which jobs were scheduled/cancelled.
 */
public class ReapplySchedulerTest {

    private Context ctx;
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private JobScheduler js;

    @Before
    public void setUp() {
        ctx = mock(Context.class);
        prefs = mock(SharedPreferences.class);
        editor = mock(SharedPreferences.Editor.class);
        js = mock(JobScheduler.class);

        when(ctx.getApplicationContext()).thenReturn(ctx);
        when(ctx.getPackageName()).thenReturn("com.xiddoc.androidautox");
        when(ctx.getSharedPreferences(eq(PhixitEngine.PREFS), anyInt())).thenReturn(prefs);
        // A separate prefs file is read by patchedAppsSpecs etc., but anyEnabled only
        // touches PhixitEngine.PREFS; route any other name to the same mock harmlessly.
        when(ctx.getSharedPreferences(any(String.class), anyInt())).thenReturn(prefs);
        when(ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(js);

        when(prefs.edit()).thenReturn(editor);
        when(editor.putBoolean(any(String.class), anyBoolean())).thenReturn(editor);

        // Defaults: auto on, nothing enabled.
        when(prefs.getBoolean(any(String.class), anyBoolean())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            boolean def = inv.getArgument(1);
            return def; // honour each call's default unless overridden per-test
        });
    }

    /** auto_reapply on (default true) + one flag-mapped tweak enabled. */
    private void autoOnTweakEnabled() {
        when(prefs.getBoolean(eq(ReapplyScheduler.PREF_AUTO), anyBoolean())).thenReturn(true);
        when(prefs.getBoolean(eq("aa_speed_hack"), anyBoolean())).thenReturn(true);
    }

    private void autoOff() {
        when(prefs.getBoolean(eq(ReapplyScheduler.PREF_AUTO), anyBoolean())).thenReturn(false);
    }

    /** auto on, but no tweak enabled (all default false). */
    private void autoOnNoTweak() {
        when(prefs.getBoolean(eq(ReapplyScheduler.PREF_AUTO), anyBoolean())).thenReturn(true);
        // no tweak keys overridden -> anyEnabled false
    }

    // --- isAutoReapplyEnabled / setAutoReapplyEnabled ---

    @Test
    public void isAutoReapplyEnabled_defaultsTrue() {
        // getBoolean returns the supplied default (true) when unset.
        assertTrue(ReapplyScheduler.isAutoReapplyEnabled(ctx));
    }

    @Test
    public void isAutoReapplyEnabled_reflectsStoredFalse() {
        autoOff();
        assertFalse(ReapplyScheduler.isAutoReapplyEnabled(ctx));
    }

    @Test
    public void setAutoReapplyEnabled_persistsAndSyncs() {
        autoOnTweakEnabled();
        try (MockedConstruction<JobInfo.Builder> b = jobInfoBuilder();
             MockedConstruction<ComponentName> c = mockConstruction(ComponentName.class)) {
            ReapplyScheduler.setAutoReapplyEnabled(ctx, true);
        }
        verify(editor).putBoolean(ReapplyScheduler.PREF_AUTO, true);
        verify(editor).apply();
        // sync() ran: eligible -> scheduled the periodic job.
        verify(js).schedule(any(JobInfo.class));
    }

    // --- sync ---

    @Test
    public void sync_schedulesPeriodic_whenEligible() {
        autoOnTweakEnabled();
        try (MockedConstruction<JobInfo.Builder> b = jobInfoBuilder();
             MockedConstruction<ComponentName> c = mockConstruction(ComponentName.class)) {
            ReapplyScheduler.sync(ctx);

            JobInfo.Builder built = b.constructed().get(0);
            verify(built).setPersisted(true);
            verify(built).setPeriodic(ReapplyScheduler.INTERVAL_MS);
            verify(built).setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        }
        verify(js).schedule(any(JobInfo.class));
        verify(js, never()).cancel(anyInt());
    }

    @Test
    public void sync_cancels_whenAutoDisabled() {
        autoOff();
        ReapplyScheduler.sync(ctx);
        verify(js).cancel(ReapplyJobService.JOB_ID);
        verify(js, never()).schedule(any(JobInfo.class));
    }

    @Test
    public void sync_cancels_whenNoTweakEnabled() {
        autoOnNoTweak();
        ReapplyScheduler.sync(ctx);
        verify(js).cancel(ReapplyJobService.JOB_ID);
        verify(js, never()).schedule(any(JobInfo.class));
    }

    @Test
    public void sync_noOp_whenNoJobScheduler() {
        when(ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(null);
        autoOnTweakEnabled();
        ReapplyScheduler.sync(ctx); // returns before touching js
        verify(js, never()).schedule(any(JobInfo.class));
        verify(js, never()).cancel(anyInt());
    }

    // --- runOnceSoon ---

    @Test
    public void runOnceSoon_schedulesOneShot_whenEligible() {
        autoOnTweakEnabled();
        try (MockedConstruction<JobInfo.Builder> b = jobInfoBuilder();
             MockedConstruction<ComponentName> c = mockConstruction(ComponentName.class)) {
            ReapplyScheduler.runOnceSoon(ctx);

            JobInfo.Builder built = b.constructed().get(0);
            verify(built).setMinimumLatency(10_000L);
            verify(built).setOverrideDeadline(120_000L);
        }
        verify(js).schedule(any(JobInfo.class));
    }

    @Test
    public void runOnceSoon_noOp_whenAutoDisabled() {
        autoOff();
        ReapplyScheduler.runOnceSoon(ctx);
        verify(js, never()).schedule(any(JobInfo.class));
    }

    @Test
    public void runOnceSoon_noOp_whenNoTweakEnabled() {
        autoOnNoTweak();
        ReapplyScheduler.runOnceSoon(ctx);
        verify(js, never()).schedule(any(JobInfo.class));
    }

    @Test
    public void runOnceSoon_noOp_whenNoJobScheduler() {
        when(ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(null);
        autoOnTweakEnabled();
        ReapplyScheduler.runOnceSoon(ctx);
        verify(js, never()).schedule(any(JobInfo.class));
    }

    // --- scheduleDeferredRetry ---

    @Test
    public void scheduleDeferredRetry_usesRetryLatency_whenEligible() {
        autoOnTweakEnabled();
        try (MockedConstruction<JobInfo.Builder> b = jobInfoBuilder();
             MockedConstruction<ComponentName> c = mockConstruction(ComponentName.class)) {
            ReapplyScheduler.scheduleDeferredRetry(ctx);

            JobInfo.Builder built = b.constructed().get(0);
            verify(built).setMinimumLatency(ReapplyScheduler.RETRY_LATENCY_MS);
            verify(built).setOverrideDeadline(2 * ReapplyScheduler.RETRY_LATENCY_MS);
        }
        verify(js).schedule(any(JobInfo.class));
    }

    @Test
    public void scheduleDeferredRetry_noOp_whenAutoDisabled() {
        autoOff();
        ReapplyScheduler.scheduleDeferredRetry(ctx);
        verify(js, never()).schedule(any(JobInfo.class));
    }

    @Test
    public void scheduleDeferredRetry_noOp_whenNoTweakEnabled() {
        autoOnNoTweak();
        ReapplyScheduler.scheduleDeferredRetry(ctx);
        verify(js, never()).schedule(any(JobInfo.class));
    }

    @Test
    public void scheduleDeferredRetry_noOp_whenNoJobScheduler() {
        when(ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(null);
        autoOnTweakEnabled();
        ReapplyScheduler.scheduleDeferredRetry(ctx);
        verify(js, never()).schedule(any(JobInfo.class));
    }

    /**
     * Intercepts {@code new JobInfo.Builder(...)} so the fluent setters return the
     * same mock and {@code build()} yields a mock {@link JobInfo}. Lets the real
     * scheduler body run without a live Android JobScheduler stack.
     */
    private static MockedConstruction<JobInfo.Builder> jobInfoBuilder() {
        return mockConstruction(JobInfo.Builder.class, (b, ctx) -> {
            when(b.setPersisted(anyBoolean())).thenReturn(b);
            when(b.setPeriodic(anyLong())).thenReturn(b);
            when(b.setRequiredNetworkType(anyInt())).thenReturn(b);
            when(b.setMinimumLatency(anyLong())).thenReturn(b);
            when(b.setOverrideDeadline(anyLong())).thenReturn(b);
            when(b.build()).thenReturn(mock(JobInfo.class));
        });
    }
}

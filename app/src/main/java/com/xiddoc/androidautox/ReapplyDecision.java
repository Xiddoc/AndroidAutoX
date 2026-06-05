package com.xiddoc.androidautox;

import java.util.List;

/**
 * Pure, testable re-apply decision logic extracted from {@link ReapplyJobService}.
 *
 * <p>The {@code JobService} shell stays a thin framework wrapper; all the real
 * branching (auto-reapply off, nothing enabled, no drift, projecting -&gt; defer,
 * drift -&gt; re-apply) lives here behind the {@link Env} seam so it can be unit
 * tested without root, JobScheduler, or a real {@link PhixitEngine}.
 */
public final class ReapplyDecision {

    private ReapplyDecision() {}

    /** The outcome of a single re-apply evaluation. Useful for assertions/logging. */
    public enum Outcome {
        /** Auto-reapply is turned off. */
        DISABLED,
        /** Auto-reapply is on but no tweaks are enabled. */
        NOTHING_ENABLED,
        /** Enabled tweaks are still applied; nothing to do. */
        NO_DRIFT,
        /** Drift detected but Android Auto is projecting; a deferred retry was scheduled. */
        DEFERRED,
        /** Drift detected and re-applied. */
        REAPPLIED
    }

    /**
     * Seam over the side-effecting collaborators used by the decision. Production
     * wiring delegates to the real {@link ReapplyScheduler}/{@link TweakRegistry}/
     * {@link PhixitEngine}; tests provide fakes.
     */
    public interface Env {
        boolean isAutoReapplyEnabled();

        /** Specs for every currently-enabled tweak (empty if none). */
        List<FlagSpec> enabledSpecs();

        /** Cheap read-only drift check: true if the specs are already applied. */
        boolean isApplied(List<FlagSpec> specs);

        /** True if Android Auto is actively projecting (re-apply would yank the screen). */
        boolean isAndroidAutoProjecting();

        /** Schedule a single deferred retry (collapses repeated deferrals). */
        void scheduleDeferredRetry();

        /** Re-apply the specs (force-stops GMS); returns whether it succeeded. */
        boolean applySpecs(List<FlagSpec> specs);
    }

    /**
     * Runs the re-apply decision. Mirrors {@code ReapplyJobService.reapplyNow} but
     * with no Android/root dependencies, returning the {@link Outcome} taken.
     */
    public static Outcome evaluate(Env env) {
        if (!env.isAutoReapplyEnabled()) {
            return Outcome.DISABLED;
        }
        List<FlagSpec> specs = env.enabledSpecs();
        if (specs == null || specs.isEmpty()) {
            return Outcome.NOTHING_ENABLED;
        }
        if (env.isApplied(specs)) {
            return Outcome.NO_DRIFT;
        }
        if (env.isAndroidAutoProjecting()) {
            env.scheduleDeferredRetry();
            return Outcome.DEFERRED;
        }
        env.applySpecs(specs);
        return Outcome.REAPPLIED;
    }
}

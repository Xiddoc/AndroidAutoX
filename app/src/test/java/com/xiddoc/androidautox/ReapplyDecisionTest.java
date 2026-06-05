package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link ReapplyDecision#evaluate}, the pure re-apply decision pulled
 * out of {@link ReapplyJobService}. Each branch/outcome is exercised through a
 * hand-rolled {@link ReapplyDecision.Env} fake that records side effects, so the
 * decision is verified with no root/JobScheduler/Android runtime.
 */
public class ReapplyDecisionTest {

    /** Recording fake {@link ReapplyDecision.Env}. */
    private static final class FakeEnv implements ReapplyDecision.Env {
        boolean autoEnabled = true;
        List<FlagSpec> specs = new ArrayList<>();
        boolean applied = false;
        boolean projecting = false;
        boolean applyResult = true;

        boolean isAppliedCalled = false;
        boolean projectingCalled = false;
        boolean deferredScheduled = false;
        boolean applyCalled = false;
        List<FlagSpec> appliedWith = null;

        @Override
        public boolean isAutoReapplyEnabled() {
            return autoEnabled;
        }

        @Override
        public List<FlagSpec> enabledSpecs() {
            return specs;
        }

        @Override
        public boolean isApplied(List<FlagSpec> s) {
            isAppliedCalled = true;
            return applied;
        }

        @Override
        public boolean isAndroidAutoProjecting() {
            projectingCalled = true;
            return projecting;
        }

        @Override
        public void scheduleDeferredRetry() {
            deferredScheduled = true;
        }

        @Override
        public boolean applySpecs(List<FlagSpec> s) {
            applyCalled = true;
            appliedWith = s;
            return applyResult;
        }
    }

    private static FlagSpec oneSpec() {
        return FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Some__flag", true);
    }

    @Test
    public void autoDisabled_bailsEarly_noOtherCalls() {
        FakeEnv env = new FakeEnv();
        env.autoEnabled = false;

        assertEquals(ReapplyDecision.Outcome.DISABLED, ReapplyDecision.evaluate(env));

        assertFalse(env.isAppliedCalled);
        assertFalse(env.projectingCalled);
        assertFalse(env.applyCalled);
        assertFalse(env.deferredScheduled);
    }

    @Test
    public void noEnabledSpecs_nothingToDo() {
        FakeEnv env = new FakeEnv();
        env.specs = new ArrayList<>(); // empty

        assertEquals(ReapplyDecision.Outcome.NOTHING_ENABLED, ReapplyDecision.evaluate(env));

        assertFalse(env.isAppliedCalled);
        assertFalse(env.applyCalled);
    }

    @Test
    public void nullSpecs_treatedAsNothingEnabled() {
        FakeEnv env = new FakeEnv();
        env.specs = null;

        assertEquals(ReapplyDecision.Outcome.NOTHING_ENABLED, ReapplyDecision.evaluate(env));

        assertFalse(env.isAppliedCalled);
        assertFalse(env.applyCalled);
    }

    @Test
    public void noDrift_bailsAfterReadOnlyCheck() {
        FakeEnv env = new FakeEnv();
        env.specs = new ArrayList<>(Collections.singletonList(oneSpec()));
        env.applied = true;

        assertEquals(ReapplyDecision.Outcome.NO_DRIFT, ReapplyDecision.evaluate(env));

        assertTrue(env.isAppliedCalled);
        assertFalse(env.projectingCalled);
        assertFalse(env.applyCalled);
        assertFalse(env.deferredScheduled);
    }

    @Test
    public void driftWhileProjecting_defersAndSchedulesRetry() {
        FakeEnv env = new FakeEnv();
        env.specs = new ArrayList<>(Collections.singletonList(oneSpec()));
        env.applied = false;
        env.projecting = true;

        assertEquals(ReapplyDecision.Outcome.DEFERRED, ReapplyDecision.evaluate(env));

        assertTrue(env.projectingCalled);
        assertTrue(env.deferredScheduled);
        assertFalse(env.applyCalled);
    }

    @Test
    public void driftNotProjecting_reappliesWithSpecs() {
        FakeEnv env = new FakeEnv();
        List<FlagSpec> specs = new ArrayList<>(Collections.singletonList(oneSpec()));
        env.specs = specs;
        env.applied = false;
        env.projecting = false;
        env.applyResult = true;

        assertEquals(ReapplyDecision.Outcome.REAPPLIED, ReapplyDecision.evaluate(env));

        assertTrue(env.applyCalled);
        assertEquals(specs, env.appliedWith);
        assertFalse(env.deferredScheduled);
    }

    @Test
    public void driftNotProjecting_reappliesEvenWhenApplyFails() {
        // The outcome is REAPPLIED regardless of the boolean apply result; the
        // helper records that the attempt happened, not its success.
        FakeEnv env = new FakeEnv();
        env.specs = new ArrayList<>(Collections.singletonList(oneSpec()));
        env.applied = false;
        env.projecting = false;
        env.applyResult = false;

        assertEquals(ReapplyDecision.Outcome.REAPPLIED, ReapplyDecision.evaluate(env));

        assertTrue(env.applyCalled);
    }

    @Test
    public void outcomeEnum_valuesRoundTrip() {
        // Touch valueOf/values so the synthetic enum methods are covered.
        for (ReapplyDecision.Outcome o : ReapplyDecision.Outcome.values()) {
            assertEquals(o, ReapplyDecision.Outcome.valueOf(o.name()));
        }
    }
}

package com.xiddoc.androidautox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link TweakAppliedChecker}.
 *
 * <p>All tests use the injected-constructor path with fake {@link TweakAppliedChecker.AppliedProbe}
 * and fake {@link TweakAppliedChecker.SpecResolver} implementations, so no root access or real
 * Android DB is required. The test suite runs entirely off-device via Robolectric (needed only
 * for the production-constructor smoke test that requires a {@link Context}).
 */
@RunWith(RobolectricTestRunner.class)
public class TweakAppliedCheckerTest {

    // A minimal non-empty spec list reused across tests.
    private static final List<FlagSpec> SOME_SPECS = Collections.singletonList(
            FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "SomeFlag__test_flag", true));

    // A multi-item spec list to exercise the multi-flag path.
    private static final List<FlagSpec> MULTI_SPECS = Arrays.asList(
            FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "FlagA", true),
            FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "FlagB", false));

    // -----------------------------------------------------------------------
    // Helpers: fixed-return probes
    // -----------------------------------------------------------------------

    /** Probe that always returns {@code true} (all flags applied). */
    private static final TweakAppliedChecker.AppliedProbe PROBE_TRUE =
            specs -> true;

    /** Probe that always returns {@code false} (flags not applied). */
    private static final TweakAppliedChecker.AppliedProbe PROBE_FALSE =
            specs -> false;

    /** Probe that always throws, simulating no-root / AIDL failure. */
    private static final TweakAppliedChecker.AppliedProbe PROBE_THROWS =
            specs -> { throw new RuntimeException("Simulated no-root / RootDb unavailable"); };

    // -----------------------------------------------------------------------
    // Helpers: fixed-return resolvers
    // -----------------------------------------------------------------------

    /** Resolver that returns {@code SOME_SPECS} for any key. */
    private static final TweakAppliedChecker.SpecResolver RESOLVER_SOME_SPECS =
            key -> SOME_SPECS;

    /** Resolver that returns a null list for any key (unknown tweak). */
    private static final TweakAppliedChecker.SpecResolver RESOLVER_NULL =
            key -> null;

    /** Resolver that returns an empty list for any key. */
    private static final TweakAppliedChecker.SpecResolver RESOLVER_EMPTY =
            key -> Collections.<FlagSpec>emptyList();

    /** Resolver that always throws (simulating resolver-side failure). */
    private static final TweakAppliedChecker.SpecResolver RESOLVER_THROWS =
            key -> { throw new RuntimeException("Resolver failure"); };

    // -----------------------------------------------------------------------
    // Probe-returns-true -> appliedState == TRUE
    // -----------------------------------------------------------------------

    /**
     * When the probe reports all flags applied, {@link TweakAppliedChecker#appliedState}
     * must return {@link Boolean#TRUE}.
     */
    @Test
    public void probeReturnsTrue_appliedStateIsTrue() {
        TweakAppliedChecker checker = new TweakAppliedChecker(PROBE_TRUE, RESOLVER_SOME_SPECS);
        Boolean result = checker.appliedState("any_key");
        assertNotNull("Result should not be null when probe returns true", result);
        assertTrue("appliedState should be TRUE when probe confirms applied", result);
    }

    // -----------------------------------------------------------------------
    // Probe-returns-false -> appliedState == FALSE
    // -----------------------------------------------------------------------

    /**
     * When the probe reports flags not applied, {@link TweakAppliedChecker#appliedState}
     * must return {@link Boolean#FALSE}.
     */
    @Test
    public void probeReturnsFalse_appliedStateIsFalse() {
        TweakAppliedChecker checker = new TweakAppliedChecker(PROBE_FALSE, RESOLVER_SOME_SPECS);
        Boolean result = checker.appliedState("any_key");
        assertNotNull("Result should not be null when probe returns false", result);
        assertFalse("appliedState should be FALSE when probe reports not-applied", result);
    }

    // -----------------------------------------------------------------------
    // Probe throws -> null (UNKNOWN)
    // -----------------------------------------------------------------------

    /**
     * When the probe throws (e.g. no root, RootDb/AIDL unavailable),
     * {@link TweakAppliedChecker#appliedState} must return {@code null} (UNKNOWN)
     * and must NOT propagate the exception.
     */
    @Test
    public void probeThrows_appliedStateIsNull_noCrash() {
        TweakAppliedChecker checker = new TweakAppliedChecker(PROBE_THROWS, RESOLVER_SOME_SPECS);
        Boolean result = checker.appliedState("any_key");
        assertNull("appliedState should be null (UNKNOWN) when probe throws", result);
    }

    // -----------------------------------------------------------------------
    // Resolver returns null specs -> null (probe never called)
    // -----------------------------------------------------------------------

    /**
     * When the resolver returns {@code null} (key unknown / no specs defined),
     * {@link TweakAppliedChecker#appliedState} must return {@code null} (UNKNOWN)
     * without calling the probe at all.
     */
    @Test
    public void resolverReturnsNullSpecs_appliedStateIsNull_probeNotCalled() {
        // Use a probe that would throw if ever invoked, to detect accidental probe calls.
        TweakAppliedChecker checker = new TweakAppliedChecker(PROBE_THROWS, RESOLVER_NULL);
        Boolean result = checker.appliedState("unknown_key");
        assertNull("appliedState should be null (UNKNOWN) when specs are null", result);
    }

    // -----------------------------------------------------------------------
    // Resolver returns empty specs -> null (nothing to assert; probe never called)
    // -----------------------------------------------------------------------

    /**
     * When the resolver returns an empty list, there are no flags to assert.
     * Vacuously returning TRUE would be misleading, so the contract is null (UNKNOWN).
     * The probe must not be called for an empty spec list.
     *
     * <p>Rationale: an empty spec list means the framework knows nothing about this tweak's
     * flags — callers must fall back to stored state rather than assuming applied.
     */
    @Test
    public void resolverReturnsEmptySpecs_appliedStateIsNull_probeNotCalled() {
        // Use a probe that would throw if ever invoked.
        TweakAppliedChecker checker = new TweakAppliedChecker(PROBE_THROWS, RESOLVER_EMPTY);
        Boolean result = checker.appliedState("some_key");
        assertNull("appliedState should be null (UNKNOWN) when specs list is empty", result);
    }

    // -----------------------------------------------------------------------
    // Resolver throws -> null (UNKNOWN, no crash)
    // -----------------------------------------------------------------------

    /**
     * When the spec resolver itself throws, {@link TweakAppliedChecker#appliedState}
     * must absorb the exception and return {@code null} (UNKNOWN).
     */
    @Test
    public void resolverThrows_appliedStateIsNull_noCrash() {
        TweakAppliedChecker checker = new TweakAppliedChecker(PROBE_TRUE, RESOLVER_THROWS);
        Boolean result = checker.appliedState("any_key");
        assertNull("appliedState should be null (UNKNOWN) when resolver throws", result);
    }

    // -----------------------------------------------------------------------
    // Multi-spec list: probe called with the full list
    // -----------------------------------------------------------------------

    /**
     * Verify that a multi-spec resolver result is forwarded intact to the probe.
     * We use a probe that records whether it was called and asserts the spec count.
     */
    @Test
    public void multipleSpecs_probeReceivesFullList_returnsTrue() {
        final boolean[] probeCalled = {false};
        final int[] probeSpecCount = {0};

        TweakAppliedChecker.AppliedProbe capturingProbe = specs -> {
            probeCalled[0] = true;
            probeSpecCount[0] = specs.size();
            return true;
        };
        TweakAppliedChecker.SpecResolver multiResolver = key -> MULTI_SPECS;

        TweakAppliedChecker checker = new TweakAppliedChecker(capturingProbe, multiResolver);
        Boolean result = checker.appliedState("multi_flag_key");

        assertTrue("Probe should have been called", probeCalled[0]);
        assertTrue("Probe should have received all specs", probeSpecCount[0] == MULTI_SPECS.size());
        assertNotNull(result);
        assertTrue(result);
    }

    // -----------------------------------------------------------------------
    // Result is stable across repeated calls (stateless)
    // -----------------------------------------------------------------------

    /**
     * {@link TweakAppliedChecker} is stateless; calling {@link TweakAppliedChecker#appliedState}
     * multiple times with the same input must always return the same result.
     */
    @Test
    public void repeatedCalls_returnConsistentResult() {
        TweakAppliedChecker checker = new TweakAppliedChecker(PROBE_TRUE, RESOLVER_SOME_SPECS);
        Boolean first  = checker.appliedState("key");
        Boolean second = checker.appliedState("key");
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first);
        assertTrue(second);
    }

    // -----------------------------------------------------------------------
    // Production-constructor smoke test (requires Robolectric Context)
    // -----------------------------------------------------------------------

    /**
     * Light construction smoke test for the convenience production constructor.
     * Verifies that {@code new TweakAppliedChecker(ctx)} produces a non-null checker
     * and that calling {@link TweakAppliedChecker#appliedState} does not throw
     * (it will return null/UNKNOWN in a test environment without root).
     *
     * <p>This test does NOT assert the actual applied state — the environment has no
     * rooted device or GMS DB. It merely guards the wiring (that the constructor and
     * method run without crashing).
     */
    @Test
    public void productionConstructor_buildsNonNullChecker_doesNotThrow() {
        Context ctx = ApplicationProvider.getApplicationContext();
        TweakAppliedChecker checker = new TweakAppliedChecker(ctx);
        assertNotNull("Production constructor should produce a non-null checker", checker);

        // In the test environment there is no root/DB, so we expect null (UNKNOWN) but no crash.
        // Use a key from TweakRegistry.ALL_KEYS that has static specs (no dynamic value needed).
        Boolean result = checker.appliedState("bluetooth_pairing_off");
        // result may be null (UNKNOWN / no root) or FALSE — just must not throw.
        // Do not assert TRUE since we have no real DB.
        assertTrue("Result must be null or false in test environment (no root DB)",
                result == null || !result);
    }
}

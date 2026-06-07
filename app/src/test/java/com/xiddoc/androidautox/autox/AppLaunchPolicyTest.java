package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link AppLaunchPolicy} and its inner {@link AppLaunchPolicy.LaunchRequest}.
 *
 * <p>All methods are pure functions, so no Android runtime or Robolectric is required.
 * Covers:
 * <ul>
 *   <li>{@link AppLaunchPolicy#launchIntentFlags()} — correct bitmask and individual flags.</li>
 *   <li>{@link AppLaunchPolicy#canLaunch(String, int)} — all four branches (null pkg,
 *       blank pkg, displayId == 0, displayId > 0).</li>
 *   <li>{@link AppLaunchPolicy.LaunchRequest#of(String, int)} — valid construction plus
 *       both validation paths (null/blank pkg, non-positive displayId).</li>
 *   <li>{@link AppLaunchPolicy.LaunchRequest} — {@code equals}/{@code hashCode} contract
 *       (reflexive, symmetric, null, wrong type, each distinguishing field), and
 *       {@code toString}.</li>
 * </ul>
 */
public class AppLaunchPolicyTest {

    // android.content.Intent public constants — values are part of the stable API.
    private static final int EXPECTED_FLAG_NEW_TASK       = 0x10000000;
    private static final int EXPECTED_FLAG_MULTIPLE_TASK  = 0x08000000;

    // ------------------------------------------------------------------
    // launchIntentFlags
    // ------------------------------------------------------------------

    @Test
    public void launchIntentFlags_includesFlagActivityNewTask() {
        int flags = AppLaunchPolicy.launchIntentFlags();
        assertEquals("FLAG_ACTIVITY_NEW_TASK must be set",
                EXPECTED_FLAG_NEW_TASK, flags & EXPECTED_FLAG_NEW_TASK);
    }

    @Test
    public void launchIntentFlags_includesFlagActivityMultipleTask() {
        int flags = AppLaunchPolicy.launchIntentFlags();
        assertEquals("FLAG_ACTIVITY_MULTIPLE_TASK must be set",
                EXPECTED_FLAG_MULTIPLE_TASK, flags & EXPECTED_FLAG_MULTIPLE_TASK);
    }

    @Test
    public void launchIntentFlags_equalsExpectedCombination() {
        assertEquals(EXPECTED_FLAG_NEW_TASK | EXPECTED_FLAG_MULTIPLE_TASK,
                AppLaunchPolicy.launchIntentFlags());
    }

    // ------------------------------------------------------------------
    // canLaunch — false paths
    // ------------------------------------------------------------------

    @Test
    public void canLaunch_nullPackageName_returnsFalse() {
        assertFalse(AppLaunchPolicy.canLaunch(null, 1));
    }

    @Test
    public void canLaunch_blankPackageName_returnsFalse() {
        assertFalse(AppLaunchPolicy.canLaunch("   ", 1));
    }

    @Test
    public void canLaunch_emptyPackageName_returnsFalse() {
        assertFalse(AppLaunchPolicy.canLaunch("", 1));
    }

    @Test
    public void canLaunch_displayIdZero_returnsFalse() {
        assertFalse(AppLaunchPolicy.canLaunch("com.example.app", 0));
    }

    @Test
    public void canLaunch_displayIdNegative_returnsFalse() {
        assertFalse(AppLaunchPolicy.canLaunch("com.example.app", -1));
    }

    // ------------------------------------------------------------------
    // canLaunch — true paths
    // ------------------------------------------------------------------

    @Test
    public void canLaunch_validPackageAndDisplayId1_returnsTrue() {
        assertTrue(AppLaunchPolicy.canLaunch("com.example.app", 1));
    }

    @Test
    public void canLaunch_validPackageAndHighDisplayId_returnsTrue() {
        assertTrue(AppLaunchPolicy.canLaunch("com.google.android.youtube", 42));
    }

    // ------------------------------------------------------------------
    // LaunchRequest.of — valid construction
    // ------------------------------------------------------------------

    @Test
    public void launchRequest_of_storesFields() {
        AppLaunchPolicy.LaunchRequest req =
                AppLaunchPolicy.LaunchRequest.of("com.example.app", 3);
        assertEquals("com.example.app", req.packageName);
        assertEquals(3, req.displayId);
    }

    @Test
    public void launchRequest_of_isNotNull() {
        assertNotNull(AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 1));
    }

    // ------------------------------------------------------------------
    // LaunchRequest.of — validation: packageName
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void launchRequest_of_nullPackageName_throws() {
        AppLaunchPolicy.LaunchRequest.of(null, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void launchRequest_of_blankPackageName_throws() {
        AppLaunchPolicy.LaunchRequest.of("  ", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void launchRequest_of_emptyPackageName_throws() {
        AppLaunchPolicy.LaunchRequest.of("", 1);
    }

    // ------------------------------------------------------------------
    // LaunchRequest.of — validation: displayId
    // ------------------------------------------------------------------

    @Test(expected = IllegalStateException.class)
    public void launchRequest_of_displayIdZero_throws() {
        AppLaunchPolicy.LaunchRequest.of("com.example.app", 0);
    }

    @Test(expected = IllegalStateException.class)
    public void launchRequest_of_displayIdNegative_throws() {
        AppLaunchPolicy.LaunchRequest.of("com.example.app", -5);
    }

    // ------------------------------------------------------------------
    // LaunchRequest equals — reflexive
    // ------------------------------------------------------------------

    @Test
    public void launchRequest_equals_reflexive() {
        AppLaunchPolicy.LaunchRequest req =
                AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 2);
        assertEquals(req, req);
    }

    // ------------------------------------------------------------------
    // LaunchRequest equals — symmetric
    // ------------------------------------------------------------------

    @Test
    public void launchRequest_equals_symmetric() {
        AppLaunchPolicy.LaunchRequest a = AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 2);
        AppLaunchPolicy.LaunchRequest b = AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 2);
        assertEquals(a, b);
        assertEquals(b, a);
    }

    // ------------------------------------------------------------------
    // LaunchRequest equals — null and wrong type
    // ------------------------------------------------------------------

    @Test
    public void launchRequest_equals_null_returnsFalse() {
        AppLaunchPolicy.LaunchRequest req =
                AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 2);
        assertFalse(req.equals(null));
    }

    @Test
    public void launchRequest_equals_wrongType_returnsFalse() {
        AppLaunchPolicy.LaunchRequest req =
                AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 2);
        assertFalse(req.equals("com.foo.bar"));
    }

    // ------------------------------------------------------------------
    // LaunchRequest equals — distinguishing fields
    // ------------------------------------------------------------------

    @Test
    public void launchRequest_equals_differentPackageName_returnsFalse() {
        AppLaunchPolicy.LaunchRequest a = AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 2);
        AppLaunchPolicy.LaunchRequest b = AppLaunchPolicy.LaunchRequest.of("com.baz.bar", 2);
        assertNotEquals(a, b);
    }

    @Test
    public void launchRequest_equals_differentDisplayId_returnsFalse() {
        AppLaunchPolicy.LaunchRequest a = AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 2);
        AppLaunchPolicy.LaunchRequest b = AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 3);
        assertNotEquals(a, b);
    }

    // ------------------------------------------------------------------
    // LaunchRequest hashCode — consistent with equals
    // ------------------------------------------------------------------

    @Test
    public void launchRequest_hashCode_equalObjects_sameHash() {
        AppLaunchPolicy.LaunchRequest a = AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 2);
        AppLaunchPolicy.LaunchRequest b = AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 2);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void launchRequest_hashCode_differentPackageName_likelyDifferentHash() {
        AppLaunchPolicy.LaunchRequest a = AppLaunchPolicy.LaunchRequest.of("com.foo.bar", 2);
        AppLaunchPolicy.LaunchRequest b = AppLaunchPolicy.LaunchRequest.of("com.baz.qux", 2);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    // ------------------------------------------------------------------
    // LaunchRequest toString
    // ------------------------------------------------------------------

    @Test
    public void launchRequest_toString_containsPackageNameAndDisplayId() {
        AppLaunchPolicy.LaunchRequest req =
                AppLaunchPolicy.LaunchRequest.of("com.example.app", 7);
        String s = req.toString();
        assertNotNull(s);
        assertTrue(s.contains("com.example.app"));
        assertTrue(s.contains("7"));
    }

    // ------------------------------------------------------------------
    // Constants (package-private) — verify numeric values match the spec
    // ------------------------------------------------------------------

    @Test
    public void flagActivityNewTask_matchesIntentConstant() {
        assertEquals(EXPECTED_FLAG_NEW_TASK, AppLaunchPolicy.FLAG_ACTIVITY_NEW_TASK);
    }

    @Test
    public void flagActivityMultipleTask_matchesIntentConstant() {
        assertEquals(EXPECTED_FLAG_MULTIPLE_TASK, AppLaunchPolicy.FLAG_ACTIVITY_MULTIPLE_TASK);
    }
}

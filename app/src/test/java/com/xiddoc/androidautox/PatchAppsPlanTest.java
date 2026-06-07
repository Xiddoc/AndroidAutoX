package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Plain-JUnit tests for the pure {@link PatchAppsPlan} diff planner: the enable/disable set
 * differences, their disjointness (the "opposite actions" guarantee), null handling, and that
 * the result is a defensive copy.
 */
public class PatchAppsPlanTest {

    private static Set<String> set(String... items) {
        return new LinkedHashSet<>(Arrays.asList(items));
    }

    @Test
    public void enableOnly_whenNothingApplied() {
        PatchAppsPlan.Diff d = PatchAppsPlan.computeDiff(set("a", "b"), Collections.<String>emptySet());
        assertEquals(set("a", "b"), d.toEnable);
        assertTrue(d.toDisable.isEmpty());
    }

    @Test
    public void disableOnly_whenNothingDesired() {
        PatchAppsPlan.Diff d = PatchAppsPlan.computeDiff(Collections.<String>emptySet(), set("a", "b"));
        assertTrue(d.toEnable.isEmpty());
        assertEquals(set("a", "b"), d.toDisable);
    }

    @Test
    public void noChange_whenEqual() {
        PatchAppsPlan.Diff d = PatchAppsPlan.computeDiff(set("a", "b"), set("a", "b"));
        assertTrue(d.toEnable.isEmpty());
        assertTrue(d.toDisable.isEmpty());
    }

    @Test
    public void mixed_oppositeActions_areDisjoint() {
        // Enable "c" while disabling "a", keeping "b" untouched -- the core safety case.
        PatchAppsPlan.Diff d = PatchAppsPlan.computeDiff(set("b", "c"), set("a", "b"));
        assertEquals(set("c"), d.toEnable);
        assertEquals(set("a"), d.toDisable);
        // Disjoint: no package is in both action sets.
        Set<String> intersection = new LinkedHashSet<>(d.toEnable);
        intersection.retainAll(d.toDisable);
        assertTrue(intersection.isEmpty());
    }

    @Test
    public void bothEmpty_isNoop() {
        PatchAppsPlan.Diff d = PatchAppsPlan.computeDiff(Collections.<String>emptySet(),
                Collections.<String>emptySet());
        assertTrue(d.toEnable.isEmpty());
        assertTrue(d.toDisable.isEmpty());
    }

    @Test
    public void nullInputs_treatedAsEmpty() {
        PatchAppsPlan.Diff d = PatchAppsPlan.computeDiff(null, null);
        assertTrue(d.toEnable.isEmpty());
        assertTrue(d.toDisable.isEmpty());

        PatchAppsPlan.Diff e = PatchAppsPlan.computeDiff(set("a"), null);
        assertEquals(set("a"), e.toEnable);
        assertTrue(e.toDisable.isEmpty());

        PatchAppsPlan.Diff f = PatchAppsPlan.computeDiff(null, set("a"));
        assertTrue(f.toEnable.isEmpty());
        assertEquals(set("a"), f.toDisable);
    }

    @Test
    public void result_isDefensiveCopy() {
        Set<String> desired = set("a", "b");
        Set<String> applied = set("a");
        PatchAppsPlan.Diff d = PatchAppsPlan.computeDiff(desired, applied);
        // Mutating the inputs after the fact must not change the computed diff.
        desired.add("z");
        applied.add("y");
        assertEquals(set("b"), d.toEnable);
        assertTrue(d.toDisable.isEmpty());
    }

    @Test
    public void preservesDesiredIterationOrder() {
        PatchAppsPlan.Diff d = PatchAppsPlan.computeDiff(set("z", "y", "x"), Collections.<String>emptySet());
        assertEquals(Arrays.asList("z", "y", "x"), new java.util.ArrayList<>(d.toEnable));
    }

    // --- decideFab --------------------------------------------------------

    @Test
    public void decideFab_hidden_whenNothingSelectedOrApplied() {
        assertEquals(PatchAppsPlan.FabAction.HIDDEN,
                PatchAppsPlan.decideFab(Collections.<String>emptySet(), Collections.<String>emptySet()));
        assertEquals(PatchAppsPlan.FabAction.HIDDEN, PatchAppsPlan.decideFab(null, null));
    }

    @Test
    public void decideFab_apply_whenSelectionDiffersFromApplied() {
        // pending enable
        assertEquals(PatchAppsPlan.FabAction.APPLY,
                PatchAppsPlan.decideFab(set("a"), Collections.<String>emptySet()));
        // pending disable
        assertEquals(PatchAppsPlan.FabAction.APPLY,
                PatchAppsPlan.decideFab(Collections.<String>emptySet(), set("a")));
        // mixed
        assertEquals(PatchAppsPlan.FabAction.APPLY, PatchAppsPlan.decideFab(set("b", "c"), set("a", "b")));
    }

    @Test
    public void decideFab_revertAll_whenSelectionMatchesAppliedNonEmpty() {
        assertEquals(PatchAppsPlan.FabAction.REVERT_ALL,
                PatchAppsPlan.decideFab(set("a", "b"), set("a", "b")));
    }
}

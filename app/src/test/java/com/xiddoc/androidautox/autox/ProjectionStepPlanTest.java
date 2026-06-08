package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Plain-JUnit tests pinning the AutoX {@link ProjectionStepPlan} apply / revert ordering.
 */
public class ProjectionStepPlanTest {

    @Test
    public void applyOrderIsTheDocumentedSequence() {
        assertEquals(Arrays.asList(
                ProjectionStep.LSPOSED_TRUSTED_DISPLAY,
                ProjectionStep.CREATE_DISPLAY,
                ProjectionStep.FREEFORM,
                ProjectionStep.IME_DECORS,
                ProjectionStep.LSPOSED_DISPLAY_COMMANDS,
                ProjectionStep.LAUNCH_APP,
                ProjectionStep.AUDIO_ROUTING),
                ProjectionStepPlan.applyOrder());
    }

    @Test
    public void revertOrderIsExactlyTheReverseOfApply() {
        List<ProjectionStep> expected = new ArrayList<>(ProjectionStepPlan.applyOrder());
        Collections.reverse(expected);
        assertEquals(expected, ProjectionStepPlan.revertOrder());
    }

    @Test
    public void applyAndRevertCoverEveryStepExactlyOnce() {
        assertEquals(EnumSet.allOf(ProjectionStep.class),
                EnumSet.copyOf(ProjectionStepPlan.applyOrder()));
        assertEquals(ProjectionStep.values().length, ProjectionStepPlan.applyOrder().size());
        assertEquals(ProjectionStep.values().length, ProjectionStepPlan.revertOrder().size());
    }

    @Test
    public void applyOrderIsUnmodifiable() {
        try {
            ProjectionStepPlan.applyOrder().add(ProjectionStep.FREEFORM);
            org.junit.Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void revertOrderIsUnmodifiable() {
        try {
            ProjectionStepPlan.revertOrder().add(ProjectionStep.FREEFORM);
            org.junit.Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void revertOrderReturnsAFreshListEachCall() {
        org.junit.Assert.assertNotSame(
                ProjectionStepPlan.revertOrder(), ProjectionStepPlan.revertOrder());
    }

    @Test
    public void enumValuesAndValueOfAreExercised() {
        // Touch values()/valueOf() so the synthetic enum methods are covered.
        for (ProjectionStep s : ProjectionStep.values()) {
            assertEquals(s, ProjectionStep.valueOf(s.name()));
        }
    }

    @Test
    public void constructorIsPrivate() throws Exception {
        Constructor<ProjectionStepPlan> c = ProjectionStepPlan.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(c.getModifiers()));
        c.setAccessible(true);
        c.newInstance(); // exercise the private ctor for coverage
    }
}

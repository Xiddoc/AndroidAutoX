package com.xiddoc.androidautox.autox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure descriptor of the AutoX projection bring-up / tear-down ordering.
 *
 * <p>The apply order and the revert order are projection-correctness invariants (e.g. the
 * LSPosed trusted-display hook must be written <em>before</em> the display is created; revert
 * must undo settings before the display is released). They previously lived only as prose in
 * {@code AutoXScreen}'s Javadoc and as the implicit call order in excluded glue, so they were
 * untestable. This class lifts them into a framework-free value the glue iterates / asserts
 * against, and that unit tests pin.
 *
 * <p>Contract: {@link #revertOrder()} is exactly the reverse of {@link #applyOrder()}, and
 * together they cover every {@link ProjectionStep} exactly once.
 */
public final class ProjectionStepPlan {

    /** The canonical apply order, oldest-first. */
    private static final List<ProjectionStep> APPLY_ORDER = Collections.unmodifiableList(
            java.util.Arrays.asList(
                    ProjectionStep.LSPOSED_TRUSTED_DISPLAY,
                    ProjectionStep.CREATE_DISPLAY,
                    ProjectionStep.FREEFORM,
                    ProjectionStep.IME_DECORS,
                    ProjectionStep.LSPOSED_DISPLAY_COMMANDS,
                    ProjectionStep.LAUNCH_APP,
                    ProjectionStep.AUDIO_ROUTING));

    private ProjectionStepPlan() {
        // Static utility class; prevent instantiation.
    }

    /**
     * @return the steps in the order they are applied during bring-up. Unmodifiable.
     */
    public static List<ProjectionStep> applyOrder() {
        return APPLY_ORDER;
    }

    /**
     * @return the steps in the order they are reverted during tear-down — exactly the reverse
     *         of {@link #applyOrder()}. A fresh unmodifiable list each call.
     */
    public static List<ProjectionStep> revertOrder() {
        List<ProjectionStep> reversed = new ArrayList<>(APPLY_ORDER);
        Collections.reverse(reversed);
        return Collections.unmodifiableList(reversed);
    }
}

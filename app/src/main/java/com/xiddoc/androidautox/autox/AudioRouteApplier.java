package com.xiddoc.androidautox.autox;

import com.xiddoc.androidautox.autox.provider.AudioRouter;

/**
 * Thin bridge between an {@link AudioRoutePolicy.RouteDecision} and an {@link AudioRouter}.
 *
 * <p>WS6 — translates the pure-policy decision objects (SetAffinity / ClearAffinity /
 * NoRoute) into the two corresponding {@link AudioRouter} calls and reports success.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link #apply} drives the {@link AudioRoutePolicy.RouteStep.SetAffinity} step:
 *       calls {@link AudioRouter#setUidAffinity} and returns its result. For a
 *       {@link AudioRoutePolicy.RouteStep.NoRoute} or
 *       {@link AudioRoutePolicy.RouteStep.ClearAffinity} apply-step it returns
 *       {@code false} immediately (a ClearAffinity apply-step is a caller error; a
 *       NoRoute step means routing is impossible).</li>
 *   <li>{@link #revert} drives the {@link AudioRoutePolicy.RouteStep.ClearAffinity}
 *       revert step: calls {@link AudioRouter#clearUidAffinity} and returns its result.
 *       For a {@link AudioRoutePolicy.RouteStep.NoRoute} revert-step it returns
 *       {@code false} immediately (nothing to revert).</li>
 * </ul>
 *
 * <p>Neither method ever throws — consistent with the best-effort/failing-closed contract
 * of {@link AudioRouter}. Both accept null arguments and return {@code false} defensively.
 *
 * <p>No Android imports — fully testable with a plain fake {@link AudioRouter} on the JVM.
 */
public final class AudioRouteApplier {

    private AudioRouteApplier() {
    }

    /**
     * Applies the {@link AudioRoutePolicy.RouteDecision#applyStep} via {@code router}.
     *
     * @param decision the routing decision whose apply-step is to be executed; may be null
     *                 (returns false)
     * @param router   the {@link AudioRouter} to invoke; may be null (returns false)
     * @return {@code true} iff a {@link AudioRoutePolicy.RouteStep.SetAffinity} step was
     *         executed and {@link AudioRouter#setUidAffinity} returned {@code true}
     */
    public static boolean apply(AudioRoutePolicy.RouteDecision decision, AudioRouter router) {
        if (decision == null || router == null) {
            return false;
        }

        AudioRoutePolicy.RouteStep step = decision.applyStep;

        if (step instanceof AudioRoutePolicy.RouteStep.SetAffinity) {
            AudioRoutePolicy.RouteStep.SetAffinity sa =
                    (AudioRoutePolicy.RouteStep.SetAffinity) step;
            return router.setUidAffinity(sa.uid, sa.deviceAddress);
        }

        // NoRoute or ClearAffinity in apply position: nothing to do (or caller error).
        return false;
    }

    /**
     * Reverts the {@link AudioRoutePolicy.RouteDecision#revertStep} via {@code router}.
     *
     * @param decision the routing decision whose revert-step is to be executed; may be null
     *                 (returns false)
     * @param router   the {@link AudioRouter} to invoke; may be null (returns false)
     * @return {@code true} iff a {@link AudioRoutePolicy.RouteStep.ClearAffinity} step was
     *         executed and {@link AudioRouter#clearUidAffinity} returned {@code true}
     */
    public static boolean revert(AudioRoutePolicy.RouteDecision decision, AudioRouter router) {
        if (decision == null || router == null) {
            return false;
        }

        AudioRoutePolicy.RouteStep step = decision.revertStep;

        if (step instanceof AudioRoutePolicy.RouteStep.ClearAffinity) {
            AudioRoutePolicy.RouteStep.ClearAffinity ca =
                    (AudioRoutePolicy.RouteStep.ClearAffinity) step;
            return router.clearUidAffinity(ca.uid);
        }

        // NoRoute: nothing was applied, so nothing to revert.
        return false;
    }
}

package com.xiddoc.androidautox.Utils;

/**
 * Pure-static geometry helpers for the FAB reveal animation.
 *
 * <p>This class has <strong>no Android framework dependencies</strong> so that
 * its invariants can be verified with plain JUnit on the JVM, without a device
 * or Robolectric.  All parameters are plain Java primitives or simple value
 * objects.
 *
 * <p><strong>Side effects:</strong> none.  This class is intentionally
 * side-effect-free and currently has no runtime callers — it exists purely so
 * that geometry invariants can be verified in unit tests without touching the
 * Android framework.  If the geometry logic ever needs to be called at runtime,
 * consider moving it to a {@code ui.animation} package; for now it lives in
 * {@code Utils} for convenience alongside the other utility helpers.
 *
 * <h3>The problem this models</h3>
 * <p>The {@code rebootContainer} FrameLayout holds the FAB button plus two
 * blurred glow layers (children with negative margins = {@code match_parent}
 * oversize).  Because {@code clipChildren=false} is set, child pixels are
 * painted outside the container's own layout bounds.
 *
 * <p>When a scale animation runs on the container, every pixel of every child
 * (including the blurred glow bleed that extends beyond the container edge) is
 * scaled and translated according to:
 * <pre>
 *   renderLeft  = pivotX - scale × pivotX   = pivotX × (1 - scale)
 *   renderRight = renderLeft + scale × containerWidth
 * </pre>
 * where {@code pivotX} is in the same coordinate space as the container's
 * left edge = 0.
 *
 * <p>The child's own left edge (relative to the container) is
 * {@code -childBleedPx} (because of the negative margin), so its rendered
 * left edge on screen is:
 * <pre>
 *   childLeft(scale) = renderLeft − scale × childBleedPx
 *                    = pivotX × (1 − scale) − scale × childBleedPx
 * </pre>
 * and its rendered right edge is:
 * <pre>
 *   childRight(scale) = renderLeft + scale × (containerWidth + childBleedPx)
 * </pre>
 *
 * <p>The <em>on-screen extent</em> swept during the animation from
 * {@code fromScale} to {@code toScale} is the difference between the
 * rightmost right-edge and the leftmost left-edge across all intermediate
 * scale values.  This is what this helper computes.
 *
 * <h3>The bug</h3>
 * <p>With the <strong>buggy</strong> animation (fromXScale=0.1, toXScale=1.0,
 * pivotX=100%=containerWidth):
 * <ul>
 *   <li>At scale 0.1 the container's left edge is at
 *       {@code containerWidth × (1 − 0.1) = 0.9 × containerWidth} — almost
 *       touching the right edge.</li>
 *   <li>At scale 1.0 the container's left edge is at 0 (its natural position).</li>
 *   <li>The left edge sweeps from {@code 0.9 × containerWidth} down to 0 as
 *       the animation progresses — the glow smears across nearly the full
 *       container width (which, for a phone-width container, is most of the
 *       screen).</li>
 * </ul>
 *
 * <h3>The fix</h3>
 * <p>With the <strong>fixed</strong> animation (fromXScale=0.85, toXScale=1.0,
 * pivotX=50%=containerWidth/2):
 * <ul>
 *   <li>At scale 0.85 the left edge is at
 *       {@code containerWidth/2 × (1 − 0.85) = 0.075 × containerWidth}.</li>
 *   <li>At scale 1.0 the left edge is at 0.</li>
 *   <li>The left edge only sweeps {@code 7.5 %} of the container width —
 *       entirely invisible as a streak, appearing only as a gentle pop-in.</li>
 * </ul>
 *
 * <h3>API</h3>
 * <ul>
 *   <li>{@link #leftEdgeSweepPx} — the key metric: how far the glow's left
 *       edge travels during the reveal (larger = more visible smear).</li>
 *   <li>{@link #maxOnScreenWidth} / {@link #maxOnScreenHeight} — worst-case
 *       on-screen child size at any point during the animation.</li>
 *   <li>{@link #isLeftEdgeSweepContained} — assertion helper used by tests.</li>
 *   <li>{@link #FIXED_REVEAL}, {@link #BUGGY_REVEAL} — canonical param sets.</li>
 * </ul>
 */
public final class FabRevealGeometry {

    // ──────────────────────────────────────────────────────────────────────────
    // Canonical animation parameters — keep in sync with reboot_button_anim.xml
    //
    // NOTE: these compile-time constants serve as documentation and are verified
    // against each other by FabRevealGeometryTest.fixedReveal_constantsMatchXmlValues
    // and FabRevealGeometryTest.buggyReveal_constantsMatchOriginalXml.  Those tests
    // are documentation-only (they test the constants against themselves).
    //
    // The *actual* XML is validated at runtime (under Robolectric) by
    // RebootButtonAnimationTest.animResource_glowBoundsViaGeometryHelper_stayInsideScreen,
    // which parses R.anim.reboot_button_anim and derives RevealParams from the
    // matrix — so it catches any XML change that does NOT update these constants.
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Scale parameters for the <em>fixed</em> FAB reveal animation.
     *
     * <p>Must stay in sync with
     * {@code app/src/main/res/anim/reboot_button_anim.xml}:
     * <ul>
     *   <li>{@code fromXScale=fromYScale="0.85"}</li>
     *   <li>{@code toXScale=toYScale="1.0"}</li>
     *   <li>{@code pivotX=pivotY="50%"} → pivotFrac = 0.5 (centre)</li>
     * </ul>
     *
     * <p>With a centre pivot the left-edge sweep is symmetric about the view
     * centre: {@code sweep = (toScale − fromScale) × (containerW/2 + bleed)},
     * which is ~7.5 % of containerWidth for the chosen scale range.  Rightward
     * containment is governed by {@link #maxOnScreenWidth}, not this formula.
     */
    public static final RevealParams FIXED_REVEAL = new RevealParams(
            /* fromScale */ 0.85f,
            /* toScale   */ 1.0f,
            /* pivotFrac */ 0.5f   // centre of the view
    );

    /**
     * Scale parameters that reproduce the <em>original buggy</em> animation,
     * kept for regression testing.  The old XML had:
     * <pre>
     *   fromXScale="0.1"  toXScale="1.0"
     *   pivotX="100%"                      ← right-edge pivot
     *   fromYScale="1.0"  toYScale="1.0"   ← Y axis was identity (fixed at full size)
     * </pre>
     *
     * <p><strong>X-axis only:</strong> only the X-axis parameters are modelled
     * here because the original Y-axis animation was the identity (fromYScale=1.0
     * → toYScale=1.0, zero delta, zero sweep).  It was the X-axis sweep with a
     * right-edge pivot that produced the screen-spanning streak, so this record
     * models the X-axis parameters only and should not be used for Y-axis checks.
     */
    public static final RevealParams BUGGY_REVEAL = new RevealParams(
            /* fromScale */ 0.1f,
            /* toScale   */ 1.0f,
            /* pivotFrac */ 1.0f   // right edge of the view (100%)
    );

    // Private — utility class, not instantiatable.
    private FabRevealGeometry() {}

    // ──────────────────────────────────────────────────────────────────────────
    // Core geometry
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Computes how far the <em>left edge</em> of the glow child sweeps during
     * the reveal animation (in pixels, relative to the container's left edge).
     *
     * <h4>Symmetry note (centre pivot)</h4>
     * <p>When {@code pivotFrac = 0.5} (centre) the left-edge sweep equals the
     * right-edge inward sweep — the motion is symmetric about the view centre.
     * Only the <em>left-edge</em> sweep is computed here because that is what
     * creates the visible horizontal "streak."  Rightward containment (ensuring
     * the child's right edge stays on-screen) is governed by
     * {@link #maxOnScreenWidth}, not this method.
     *
     * <h4>Derivation</h4>
     * <p>Let {@code P = pivotFrac × containerWidth} (pivot x in container coords).
     * At scale {@code s}, the container's left edge is rendered at
     * {@code P × (1 − s)} in container coords.  The child overhangs the
     * container by {@code childBleedPerSidePx} on the <em>left side only</em>
     * (the right side contributes to {@link #maxOnScreenWidth} instead), so
     * the child's left edge in container coords is {@code −childBleedPerSidePx},
     * which after scaling becomes:
     * <pre>
     *   childLeft(s) = P × (1 − s) − s × childBleedPerSidePx
     * </pre>
     * The sweep is the range of {@code childLeft(s)} across all {@code s} in
     * {@code [fromScale, toScale]}:
     * <pre>
     *   sweep = childLeft(fromScale) − childLeft(toScale)
     *         = [P × (1 − fromScale) − fromScale × bleed]
     *           − [P × (1 − toScale) − toScale × bleed]
     *         = P × (toScale − fromScale) + (toScale − fromScale) × bleed
     *         = (toScale − fromScale) × (P + bleed)
     * </pre>
     * A large sweep means the left edge travels far — the visual "streak."
     *
     * @param containerWidthPx       Container's natural laid-out width (px).
     * @param childBleedPerSidePx    Child overhang beyond the container edge on
     *                               <em>each</em> side (negative-margin px +
     *                               blur-radius px).  Only the left-side bleed
     *                               enters the left-edge formula; the total
     *                               two-sided bleed (2× this value) is used by
     *                               {@link #maxOnScreenWidth}.
     * @param params                 Animation parameters for one axis.
     * @return Left-edge sweep distance (px); always ≥ 0.
     */
    public static float leftEdgeSweepPx(
            float containerWidthPx,
            float childBleedPerSidePx,
            RevealParams params) {

        float pivotX       = params.pivotFrac * containerWidthPx;
        float scaleDelta   = params.toScale - params.fromScale;
        // Formula derived above: sweep = (toScale − fromScale) × (pivotX + childBleedPerSide)
        return Math.abs(scaleDelta) * (pivotX + childBleedPerSidePx);
    }

    /**
     * Computes the maximum on-screen width (px) of the glow child at any
     * moment during the animation.  This is the child's natural width times
     * the maximum scale reached:
     * <pre>
     *   maxWidth = max(fromScale, toScale) × (containerWidth + 2 × childBleed)
     * </pre>
     *
     * @param containerWidthPx  Container's natural width (px).
     * @param childBleedPx      Child overhang beyond the container edge (each side).
     * @param params            Animation parameters.
     * @return Maximum on-screen width (px) during the animation.
     */
    public static float maxOnScreenWidth(
            float containerWidthPx,
            float childBleedPx,
            RevealParams params) {

        float childNaturalWidth = containerWidthPx + 2f * childBleedPx;
        float worstScale = Math.max(Math.abs(params.fromScale), Math.abs(params.toScale));
        return worstScale * childNaturalWidth;
    }

    /**
     * Analogous to {@link #maxOnScreenWidth} for the vertical axis.
     *
     * @param containerHeightPx  Container's natural height (px).
     * @param childBleedPx       Child overhang beyond the container edge (each side).
     * @param params             Animation parameters (Y-axis).
     * @return Maximum on-screen height (px) during the animation.
     */
    public static float maxOnScreenHeight(
            float containerHeightPx,
            float childBleedPx,
            RevealParams params) {

        float childNaturalHeight = containerHeightPx + 2f * childBleedPx;
        float worstScale = Math.max(Math.abs(params.fromScale), Math.abs(params.toScale));
        return worstScale * childNaturalHeight;
    }

    /**
     * Returns {@code true} if the left-edge sweep stays within
     * {@code maxSweepFraction × containerWidth}.
     *
     * <p>A fraction of {@code 0.15} (15 % of container width) means the glow's
     * left edge travels at most 15 % of the container width during the reveal —
     * invisible as a streak.  The fixed animation achieves ~7.5 %; the buggy
     * animation achieves 90 % (nearly the full container width, which on a
     * full-width layout is the screen width).
     *
     * @param containerWidthPx     Container's natural width (px).
     * @param childBleedPerSidePx  Child overhang per side (px).  See
     *                             {@link #leftEdgeSweepPx} for parameter semantics.
     * @param params               Animation parameters.
     * @param maxSweepFraction     Maximum allowed sweep as a fraction of
     *                             {@code containerWidthPx} (e.g. 0.15).
     * @return {@code true} if the sweep is within the allowed fraction
     *         (inclusive: sweep == threshold returns {@code true}).
     */
    public static boolean isLeftEdgeSweepContained(
            float containerWidthPx,
            float childBleedPerSidePx,
            RevealParams params,
            float maxSweepFraction) {

        float sweep = leftEdgeSweepPx(containerWidthPx, childBleedPerSidePx, params);
        return sweep <= containerWidthPx * maxSweepFraction;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Value object
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Immutable record of the scale-animation parameters for one axis.
     *
     * <p>For a uniform (X == Y) animation use the same {@code RevealParams} for
     * both axes.  For an asymmetric animation (like the buggy original, which
     * had fromXScale=0.1 but fromYScale=1.0) model each axis separately.
     */
    public static final class RevealParams {
        /** Starting scale factor (e.g. 0.85 for the fixed animation). */
        public final float fromScale;
        /** Ending scale factor (e.g. 1.0 for the fixed animation). */
        public final float toScale;
        /**
         * Pivot position as a fraction of the view's natural dimension:
         * {@code 0.0} = left/top edge; {@code 0.5} = centre;
         * {@code 1.0} = right/bottom edge.
         */
        public final float pivotFrac;

        /**
         * @param fromScale  Starting scale factor.
         * @param toScale    Ending scale factor.
         * @param pivotFrac  Pivot as a fraction of the view dimension (0–1).
         */
        public RevealParams(float fromScale, float toScale, float pivotFrac) {
            this.fromScale = fromScale;
            this.toScale   = toScale;
            this.pivotFrac = pivotFrac;
        }

        @Override
        public String toString() {
            return "RevealParams{fromScale=" + fromScale
                    + ", toScale=" + toScale
                    + ", pivotFrac=" + pivotFrac + "}";
        }
    }
}

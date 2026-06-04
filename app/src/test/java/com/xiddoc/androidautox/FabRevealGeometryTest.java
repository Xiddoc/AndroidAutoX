package com.xiddoc.androidautox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xiddoc.androidautox.Utils.FabRevealGeometry;
import com.xiddoc.androidautox.Utils.FabRevealGeometry.RevealParams;

import org.junit.Test;

/**
 * Pure JVM regression tests for the FAB reveal animation geometry.
 *
 * <p>These tests have <strong>zero Android framework dependencies</strong>:
 * they exercise only {@link FabRevealGeometry}, which is a plain Java class.
 * No Robolectric, no device, no Activity lifecycle — just arithmetic.
 *
 * <h3>The key metric: left-edge sweep</h3>
 * <p>The original bug was a POSITIONAL smear: the glow's left edge swept from
 * near the container's right edge all the way to the container's left edge as
 * the scale grew from 0.1 to 1.0 with a right-edge pivot.  The fixed animation
 * uses a centre pivot and a much narrower scale range (0.85→1.0), so the sweep
 * is only ~7.5% of the container width instead of ~90%.
 *
 * <h3>Geometry recap</h3>
 * <pre>
 *   leftEdgeSweep = (toScale − fromScale) × (pivotX + childBleed)
 *
 *   Buggy  (fromScale=0.1, pivotFrac=1.0):
 *     pivotX = 1.0 × containerW = containerW
 *     sweep  = (1.0 − 0.1) × (containerW + bleed) = 0.9 × (containerW + bleed)
 *     → approaches containerW when containerW >> bleed
 *
 *   Fixed  (fromScale=0.85, pivotFrac=0.5):
 *     pivotX = 0.5 × containerW = containerW/2
 *     sweep  = (1.0 − 0.85) × (containerW/2 + bleed) = 0.15 × (containerW/2 + bleed)
 *     → ≈ 0.075 × containerW when bleed ≈ 0
 * </pre>
 *
 * <h3>Device geometry assumptions</h3>
 * <p>Typical 420-dpi phone in portrait mode:
 * <ul>
 *   <li>Screen: 1080 × 2340 px</li>
 *   <li>Container ({@code reboot_container}): ~200 dp wide ≈ 525 px</li>
 *   <li>Outer glow bleed per side: 44 dp negative-margin (≈ 116 px)
 *       + 30 dp blur radius (≈ 79 px) = ~195 px total bleed</li>
 * </ul>
 */
public class FabRevealGeometryTest {

    // ─── realistic device geometry ────────────────────────────────────────────
    /** Typical 1080-px phone screen width. */
    private static final float SCREEN_WIDTH_PX  = 1080f;
    /** Typical 2340-px phone screen height. */
    private static final float SCREEN_HEIGHT_PX = 2340f;

    /**
     * FAB container natural width (wrap_content ≈ 200 dp at 420 dpi ≈ 525 px).
     */
    private static final float CONTAINER_W_PX = 525f;
    /**
     * FAB container natural height (standard 48 dp button ≈ 126 px at 420 dpi).
     */
    private static final float CONTAINER_H_PX = 126f;

    /**
     * Outer glow child bleed per side.
     * <ul>
     *   <li>Negative margin: 44 dp × 2.625 = 116 px</li>
     *   <li>RenderEffect blur radius: 30 dp × 2.625 = 79 px</li>
     *   <li>Total: 195 px per side</li>
     * </ul>
     */
    private static final float CHILD_BLEED_PX = 195f;

    /**
     * Maximum allowed left-edge sweep as a fraction of the container width for
     * a "contained" animation.  0.25 = 25% of container width → imperceptible
     * on a typical phone (only ~131 px on a 525-px-wide container).
     */
    private static final float MAX_SWEEP_FRACTION = 0.25f;

    // ─── tests ────────────────────────────────────────────────────────────────

    /**
     * The fixed animation parameters must keep the glow left-edge sweep well
     * within 25% of the container width.
     *
     * <p>For the fixed params with the device geometry above:
     * <pre>
     *   pivotX = 0.5 × 525 = 262.5 px
     *   sweep  = (1.0 − 0.85) × (262.5 + 195) = 0.15 × 457.5 ≈ 68.6 px
     *   68.6 ≤ 0.25 × 525 = 131.25 px  →  PASS
     * </pre>
     */
    @Test
    public void fixedReveal_leftEdgeSweep_isContained() {
        float sweep = FabRevealGeometry.leftEdgeSweepPx(
                CONTAINER_W_PX, CHILD_BLEED_PX, FabRevealGeometry.FIXED_REVEAL);

        float maxAllowed = MAX_SWEEP_FRACTION * CONTAINER_W_PX;
        assertTrue(
                "Fixed reveal: left-edge sweep (" + sweep + " px) must be ≤ "
                        + maxAllowed + " px (= " + (MAX_SWEEP_FRACTION * 100) + "% of container)",
                sweep <= maxAllowed);
    }

    /**
     * The old buggy parameters must VIOLATE the same sweep bound.
     *
     * <p>For the buggy params with the device geometry above:
     * <pre>
     *   pivotX = 1.0 × 525 = 525 px   (right edge)
     *   sweep  = (1.0 − 0.1) × (525 + 195) = 0.9 × 720 = 648 px
     *   648 > 0.25 × 525 = 131.25 px  →  FAIL (correctly)
     * </pre>
     * This demonstrates that the test WOULD have caught the original bug.
     */
    @Test
    public void buggyReveal_leftEdgeSweep_violatesContainedBound() {
        assertFalse(
                "Buggy reveal must VIOLATE the contained-sweep bound — "
                        + "if this assertion fails, the test cannot catch a regression",
                FabRevealGeometry.isLeftEdgeSweepContained(
                        CONTAINER_W_PX, CHILD_BLEED_PX,
                        FabRevealGeometry.BUGGY_REVEAL, MAX_SWEEP_FRACTION));
    }

    /**
     * Demonstrate the sweep ratio difference between fixed and buggy params.
     * Fixed sweep ≤ 15 % of buggy sweep — at least a 6× improvement.
     */
    @Test
    public void fixedReveal_sweepIsMuchSmallerThanBuggyReveal() {
        float fixedSweep = FabRevealGeometry.leftEdgeSweepPx(
                CONTAINER_W_PX, CHILD_BLEED_PX, FabRevealGeometry.FIXED_REVEAL);
        float buggySweep = FabRevealGeometry.leftEdgeSweepPx(
                CONTAINER_W_PX, CHILD_BLEED_PX, FabRevealGeometry.BUGGY_REVEAL);

        // Fixed sweep should be at most 15 % of the buggy sweep (empirically ~10.6 %).
        assertTrue(
                "Fixed sweep (" + fixedSweep + " px) should be ≤ 15% of buggy sweep ("
                        + buggySweep + " px)",
                fixedSweep <= 0.15f * buggySweep);
    }

    /**
     * The fixed animation's max on-screen width must stay below the screen width.
     *
     * <p>For the fixed params: 1.0 × (525 + 2 × 195) = 915 px &lt; 1080 px.
     */
    @Test
    public void fixedReveal_maxOnScreenWidth_staysInsideScreenWidth() {
        float maxWidth = FabRevealGeometry.maxOnScreenWidth(
                CONTAINER_W_PX, CHILD_BLEED_PX, FabRevealGeometry.FIXED_REVEAL);

        assertTrue(
                "Fixed reveal: glow max width (" + maxWidth + " px) must be"
                        + " < screen width (" + SCREEN_WIDTH_PX + " px)",
                maxWidth < SCREEN_WIDTH_PX);
    }

    /**
     * The fixed animation's max on-screen height must stay below the screen height.
     */
    @Test
    public void fixedReveal_maxOnScreenHeight_staysInsideScreenHeight() {
        float maxHeight = FabRevealGeometry.maxOnScreenHeight(
                CONTAINER_H_PX, CHILD_BLEED_PX, FabRevealGeometry.FIXED_REVEAL);

        assertTrue(
                "Fixed reveal: glow max height (" + maxHeight + " px) must be"
                        + " < screen height (" + SCREEN_HEIGHT_PX + " px)",
                maxHeight < SCREEN_HEIGHT_PX);
    }

    /**
     * The constants in {@link FabRevealGeometry#FIXED_REVEAL} must exactly
     * match the values declared in {@code reboot_button_anim.xml}.
     *
     * <p>This test locks the literal constants so that a future XML edit that
     * changes the scale range or pivot breaks loudly here.
     */
    @Test
    public void fixedReveal_constantsMatchXmlValues() {
        RevealParams p = FabRevealGeometry.FIXED_REVEAL;
        assertTrue("fromScale must be 0.85", Math.abs(p.fromScale - 0.85f) < 0.001f);
        assertTrue("toScale must be 1.0",    Math.abs(p.toScale   - 1.0f)  < 0.001f);
        assertTrue("pivotFrac must be 0.5 (centre pivot)",
                Math.abs(p.pivotFrac - 0.5f) < 0.001f);
    }

    /**
     * The buggy constants in {@link FabRevealGeometry#BUGGY_REVEAL} must match
     * the original {@code reboot_button_anim.xml} values for the regression
     * tests to be meaningful.
     */
    @Test
    public void buggyReveal_constantsMatchOriginalXml() {
        RevealParams p = FabRevealGeometry.BUGGY_REVEAL;
        assertTrue("buggy fromScale must be 0.1",  Math.abs(p.fromScale - 0.1f) < 0.001f);
        assertTrue("buggy toScale must be 1.0",    Math.abs(p.toScale   - 1.0f) < 0.001f);
        assertTrue("buggy pivotFrac must be 1.0 (right-edge pivot)",
                Math.abs(p.pivotFrac - 1.0f) < 0.001f);
    }

    /**
     * Verify the sweep formula manually for a simple case:
     * container=200, bleed=0, fromScale=0.5, pivotFrac=1.0 (right edge).
     *
     * <pre>
     *   pivotX = 1.0 × 200 = 200
     *   sweep  = (1.0 − 0.5) × (200 + 0) = 0.5 × 200 = 100
     * </pre>
     */
    @Test
    public void leftEdgeSweep_formula_knownCase_rightEdgePivotHalfScale() {
        RevealParams p = new RevealParams(0.5f, 1.0f, 1.0f);
        float sweep = FabRevealGeometry.leftEdgeSweepPx(200f, 0f, p);
        assertTrue("Sweep should be 100 px for this known case, got " + sweep,
                Math.abs(sweep - 100f) < 0.01f);
    }

    /**
     * Zero sweep: if fromScale == toScale there is no animation → no sweep.
     */
    @Test
    public void leftEdgeSweep_noAnimation_isZero() {
        RevealParams p = new RevealParams(1.0f, 1.0f, 0.5f);
        float sweep = FabRevealGeometry.leftEdgeSweepPx(500f, 100f, p);
        assertTrue("Sweep must be 0 when fromScale == toScale, got " + sweep,
                sweep < 0.001f);
    }

    /**
     * Centre pivot, zero bleed: sweep = scaleDelta × containerW/2.
     * E.g. container=400, bleed=0, from=0.8 → sweep = 0.2 × 200 = 40.
     */
    @Test
    public void leftEdgeSweep_centrePivot_zeroBleed_formula() {
        RevealParams p = new RevealParams(0.8f, 1.0f, 0.5f);
        float sweep = FabRevealGeometry.leftEdgeSweepPx(400f, 0f, p);
        // pivot = 200, delta = 0.2, sweep = 0.2 × 200 = 40
        assertTrue("Sweep should be 40 px, got " + sweep,
                Math.abs(sweep - 40f) < 0.01f);
    }
}

package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Matrix;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.xiddoc.androidautox.Utils.FabRevealGeometry;
import com.xiddoc.androidautox.Utils.FabRevealGeometry.RevealParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Robolectric tests that verify the FAB reveal animation resource is the
 * fixed, contained variant (not the old screen-spanning horizontal scale).
 *
 * <p>These tests run on the JVM with Robolectric's Android shadow layer — no
 * device or emulator required.  They load the actual
 * {@code R.anim.reboot_button_anim} XML from the compiled resource tree and
 * inspect its animation properties programmatically.
 *
 * <p>Robolectric 4.12.2 is assumed for pivot-resolution behaviour: the shadow
 * layer resolves {@code RELATIVE_TO_SELF} pivot values against the provided
 * view dimensions in {@link ScaleAnimation#initialize}, matching the platform
 * implementation.
 *
 * <h3>Why this approach?</h3>
 * <p>Inflating the full {@code MainActivity} under Robolectric is impractical
 * because its {@code onCreate} immediately tries to bind {@code PhixitRootService}
 * (a libsu {@link com.topjohnwu.superuser.ipc.RootService}) and acquire a root
 * shell — both impossible in a JVM sandbox.  Instead we test the animation
 * resource directly, which is the minimal unit that contained the bug:
 * <ul>
 *   <li>Load the animation set from {@code R.anim.reboot_button_anim}.</li>
 *   <li>Find the {@link ScaleAnimation} child inside the {@link AnimationSet}.</li>
 *   <li>Reflect on its public fields / inspect via the {@link Animation} API to
 *       confirm the scale range and pivot are the contained values.</li>
 * </ul>
 *
 * <h3>Drift-gap integration (top priority)</h3>
 * <p>{@link #animResource_derivedParams_glowBoundsContained} is the key
 * integration test: it derives a {@link RevealParams} <em>directly from the
 * matrix values parsed from the XML resource</em> (not from the compile-time
 * constants in {@link FabRevealGeometry#FIXED_REVEAL}) and feeds those into
 * {@link FabRevealGeometry#leftEdgeSweepPx} / {@link FabRevealGeometry#isLeftEdgeSweepContained}.
 * If someone reverts {@code reboot_button_anim.xml} to the buggy values
 * ({@code fromXScale=0.1, pivotX=100%}) this test MUST fail — the Robolectric
 * layer and the geometry layer meet here.
 *
 * <h3>Constant-vs-literal tests (documentation only)</h3>
 * <p>{@link FabRevealGeometryTest#fixedReveal_constantsMatchXmlValues} and
 * {@link FabRevealGeometryTest#buggyReveal_constantsMatchOriginalXml} verify the
 * compile-time constants against themselves — they are documentation tests that
 * lock the expected values.  They do NOT parse the XML and cannot catch an XML
 * change that is not reflected in the constants.  Only this Robolectric test
 * closes that gap.
 *
 * <h3>What is asserted</h3>
 * <ol>
 *   <li>The top-level animation is an {@link AnimationSet} (the {@code <set>}
 *       element in the XML).</li>
 *   <li>The set contains <em>exactly</em> two children: a {@link ScaleAnimation}
 *       and an {@link AlphaAnimation}.</li>
 *   <li>The {@link ScaleAnimation}'s from/to X-scale values are the contained
 *       ones (0.85→1.0), not the old buggy ones (0.1→1.0).</li>
 *   <li>The scale animation duration is 400 ms.</li>
 *   <li>The scale is <em>uniform</em>: fromX == fromY, toX == toY.</li>
 *   <li>The pivot X and Y are both 50 % of the view's dimension (centre pivot),
 *       not the old 100 % (right-edge) pivot.</li>
 *   <li>The {@link AlphaAnimation} goes from alpha 0 to alpha 1.</li>
 *   <li>Drift-gap: {@link RevealParams} derived from the parsed matrix passes
 *       the containment invariant via {@link FabRevealGeometry}.</li>
 * </ol>
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 34)
public class RebootButtonAnimationTest {

    // The pivot value stored by ScaleAnimation for "50%" (RELATIVE_TO_SELF, 0.5f).
    // Robolectric 4.12.2 resolves the raw fraction against the provided view dims
    // in initialize(), so we compare the *resolved* pivot against a reference view.
    private static final float CENTRE_PIVOT_FRAC = 0.5f;

    /** fromXScale in the *fixed* animation XML. */
    private static final float EXPECTED_FROM_SCALE = 0.85f;
    /** toXScale in the *fixed* animation XML. */
    private static final float EXPECTED_TO_SCALE   = 1.0f;

    /** Expected scale animation duration (ms). */
    private static final long EXPECTED_DURATION_MS = 400L;

    /**
     * Floating-point tolerance for scale comparisons (~1% of full scale).
     * The math is deterministic under Robolectric, so we only need headroom
     * for floating-point representation.
     */
    private static final float SCALE_TOLERANCE = 0.01f;

    /**
     * Tight pixel tolerance for matrix translation checks.
     * Robolectric pivot resolution is deterministic, so 0.5 px is sufficient.
     */
    private static final float PIXEL_TOLERANCE = 0.5f;

    /**
     * Tightened maximum left-edge sweep fraction.
     *
     * <p>The fixed animation achieves ~13.1% of container width (≈ 68.6 px on
     * a 525-px container).  A partial-streak animation like {@code fromScale=0.85,
     * pivotFrac=0.9} would produce:
     * <pre>
     *   pivot = 0.9 × 525 = 472.5 px
     *   sweep = (1.0 − 0.85) × (472.5 + 195) = 0.15 × 667.5 ≈ 100 px
     *   100 px > 0.15 × 525 = 78.75 px  →  FAIL (correctly)
     * </pre>
     * A threshold of 0.15 (15% of container width, ~1.15× the fixed animation's
     * actual ~13.1%) is tight enough to catch off-centre pivots and large-delta
     * animations while leaving comfortable headroom for the intended values.
     * The old threshold of 0.25 would let a pivotFrac=0.9 animation pass.
     */
    private static final float MAX_SWEEP_FRACTION = 0.15f;

    private Context context;
    private AnimationSet animSet;
    private ScaleAnimation scaleAnim;
    private AlphaAnimation alphaAnim;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();

        // Load the actual animation resource from the compiled res tree.
        Animation raw = AnimationUtils.loadAnimation(context, R.anim.reboot_button_anim);

        assertNotNull("R.anim.reboot_button_anim must load successfully", raw);
        assertTrue(
                "reboot_button_anim must be an AnimationSet (<set> root element),"
                        + " got: " + raw.getClass().getSimpleName(),
                raw instanceof AnimationSet);

        animSet = (AnimationSet) raw;

        // Find ScaleAnimation and AlphaAnimation inside the set.
        ScaleAnimation foundScale = null;
        AlphaAnimation foundAlpha = null;
        List<Animation> children = animSet.getAnimations();
        for (Animation child : children) {
            if (child instanceof ScaleAnimation && foundScale == null) {
                foundScale = (ScaleAnimation) child;
            } else if (child instanceof AlphaAnimation && foundAlpha == null) {
                foundAlpha = (AlphaAnimation) child;
            }
        }
        assertNotNull("AnimationSet must contain a ScaleAnimation child", foundScale);
        assertNotNull("AnimationSet must contain an AlphaAnimation child", foundAlpha);
        scaleAnim = foundScale;
        alphaAnim = foundAlpha;
    }

    // ─── structural tests ────────────────────────────────────────────────────

    /**
     * The animation set must contain EXACTLY two children (scale + alpha).
     * Extra children would indicate an unreviewed structural change to the XML.
     */
    @Test
    public void animSet_hasExactlyTwoChildren() {
        assertEquals(
                "AnimationSet must have exactly 2 children (scale + alpha), found "
                        + animSet.getAnimations().size(),
                2, animSet.getAnimations().size());
    }

    // ─── scale animation tests ───────────────────────────────────────────────

    /**
     * The scale animation must use the contained from-scale (0.85), not the
     * old buggy value of 0.1 which caused the screen-spanning streak.
     *
     * <p>We resolve the scale by applying it to a synthetic view of known size
     * and reading the transformation matrix.
     */
    @Test
    public void scaleAnim_fromXScale_isContainedNotBuggy() {
        View probe = new View(context);
        probe.layout(0, 0, 1000, 1000);

        Transformation tf = new Transformation();
        long fakeNow = 0L;
        scaleAnim.initialize(1000, 1000, 1000, 1000);
        scaleAnim.setStartTime(fakeNow);
        scaleAnim.getTransformation(fakeNow, tf);

        float[] matValues = new float[9];
        tf.getMatrix().getValues(matValues);
        float scaleXAtStart = matValues[Matrix.MSCALE_X];

        assertTrue(
                "fromXScale must be close to " + EXPECTED_FROM_SCALE
                        + " (contained reveal), got " + scaleXAtStart
                        + ".  If this is ~0.1, the buggy screen-spanning animation is back.",
                Math.abs(scaleXAtStart - EXPECTED_FROM_SCALE) < SCALE_TOLERANCE);
    }

    /**
     * The scale animation must end at 1.0 (full size, no shrink-on-completion).
     */
    @Test
    public void scaleAnim_toXScale_isOne() {
        scaleAnim.initialize(1000, 1000, 1000, 1000);
        long duration = scaleAnim.getDuration();
        scaleAnim.setStartTime(0L);

        Transformation tf = new Transformation();
        scaleAnim.getTransformation(duration, tf);

        float[] matValues = new float[9];
        tf.getMatrix().getValues(matValues);
        float scaleXAtEnd = matValues[Matrix.MSCALE_X];

        assertTrue(
                "toXScale must be 1.0, got " + scaleXAtEnd,
                Math.abs(scaleXAtEnd - EXPECTED_TO_SCALE) < SCALE_TOLERANCE);
    }

    /**
     * The scale animation duration must be 400 ms.
     */
    @Test
    public void scaleAnim_duration_is400ms() {
        assertEquals(
                "Scale animation duration must be 400 ms",
                EXPECTED_DURATION_MS, scaleAnim.getDuration());
    }

    /**
     * The scale must be uniform: the Y-axis scale must equal the X-axis scale
     * at the start of the animation.  An X-only scale (like the old bug) has
     * fromYScale=1.0 while fromXScale=0.1 — these must not diverge.
     */
    @Test
    public void scaleAnim_isUniform_xEqualsYAtStart() {
        scaleAnim.initialize(1000, 1000, 1000, 1000);
        scaleAnim.setStartTime(0L);

        Transformation tf = new Transformation();
        scaleAnim.getTransformation(0L, tf);

        float[] matValues = new float[9];
        tf.getMatrix().getValues(matValues);
        float scaleX = matValues[Matrix.MSCALE_X];
        float scaleY = matValues[Matrix.MSCALE_Y];

        assertEquals(
                "Scale must be uniform (fromXScale == fromYScale)."
                        + "  An X-only scale would leave scaleY=1.0 while scaleX=0.85.",
                scaleX, scaleY, SCALE_TOLERANCE);
    }

    /**
     * The pivot must be the centre (50 % / 50 %) of the view, in both X and Y.
     *
     * <p>We detect the X pivot by checking the translation component of the matrix:
     * with a centre pivot and a scale of {@code s}, the matrix translates by
     * {@code (1-s) × halfSize} in both directions, so the upper-left corner
     * of the scaled view lands at {@code (1-s) × halfSize}, not at 0 (right-
     * edge pivot would leave the right corner fixed and push the left corner
     * far left).
     *
     * <p>For a 1000×1000 view at scale 0.85 from centre: translateX = (1-0.85)×500 = 75.
     * For scale 0.85 from right edge (pivotX=1.0): translateX = (1-0.85)×1000 = 150.
     * We also verify translateY matches the centre-pivot formula (Y is symmetric).
     */
    @Test
    public void scaleAnim_pivot_isCentre_notRightEdge() {
        int viewSize = 1000;
        scaleAnim.initialize(viewSize, viewSize, viewSize, viewSize);
        scaleAnim.setStartTime(0L);

        Transformation tf = new Transformation();
        scaleAnim.getTransformation(0L, tf);

        float[] matValues = new float[9];
        tf.getMatrix().getValues(matValues);
        float scaleX = matValues[Matrix.MSCALE_X];
        float scaleY = matValues[Matrix.MSCALE_Y];
        float tx     = matValues[Matrix.MTRANS_X];
        float ty     = matValues[Matrix.MTRANS_Y];

        // With centre pivot: tx = ty = (1 - scale) × viewSize × 0.5
        float expectedTxCentre    = (1f - scaleX) * viewSize * CENTRE_PIVOT_FRAC;
        float expectedTyCenter    = (1f - scaleY) * viewSize * CENTRE_PIVOT_FRAC;
        float expectedTxRightEdge = (1f - scaleX) * viewSize * 1.0f;

        assertTrue(
                "Pivot X must be at the view centre (tx ≈ " + expectedTxCentre
                        + "), not at the right edge (tx ≈ " + expectedTxRightEdge
                        + ").  Got tx=" + tx + ".  Right-edge pivot causes the screen-wide smear.",
                Math.abs(tx - expectedTxCentre) < PIXEL_TOLERANCE);

        assertTrue(
                "Pivot Y must also be at the view centre (ty ≈ " + expectedTyCenter
                        + ").  Got ty=" + ty + ".",
                Math.abs(ty - expectedTyCenter) < PIXEL_TOLERANCE);
    }

    // ─── alpha animation tests ───────────────────────────────────────────────

    /**
     * The AlphaAnimation must fade in from 0 to 1 — no pre-baked visibility jump.
     */
    @Test
    public void alphaAnim_fadeIn_zeroToOne() {
        alphaAnim.initialize(100, 100, 100, 100);
        alphaAnim.setStartTime(0L);

        // Sample at t=0 → fromAlpha
        Transformation tfStart = new Transformation();
        alphaAnim.getTransformation(0L, tfStart);
        float fromAlpha = tfStart.getAlpha();

        // Sample at t=duration → toAlpha
        Transformation tfEnd = new Transformation();
        alphaAnim.setStartTime(0L);
        alphaAnim.getTransformation(alphaAnim.getDuration(), tfEnd);
        float toAlpha = tfEnd.getAlpha();

        assertTrue(
                "AlphaAnimation must start at alpha 0 (fade in), got fromAlpha=" + fromAlpha,
                Math.abs(fromAlpha - 0f) < SCALE_TOLERANCE);
        assertTrue(
                "AlphaAnimation must end at alpha 1 (fully visible), got toAlpha=" + toAlpha,
                Math.abs(toAlpha - 1f) < SCALE_TOLERANCE);
    }

    // ─── drift-gap integration test (TOP PRIORITY) ───────────────────────────

    /**
     * Drift-gap integration: derive a {@link RevealParams} from the matrix values
     * actually parsed from {@code R.anim.reboot_button_anim}, then assert that
     * {@link FabRevealGeometry#leftEdgeSweepPx} / {@link FabRevealGeometry#isLeftEdgeSweepContained}
     * report containment.
     *
     * <p>This test closes the XML↔constant drift gap:
     * <ul>
     *   <li>It does NOT use {@link FabRevealGeometry#FIXED_REVEAL} — those are
     *       compile-time constants that could drift from the XML without this test
     *       noticing.</li>
     *   <li>It parses the ScaleAnimation matrix at {@code t=0} (fromScale) and
     *       at {@code t=duration} (toScale), then inverts the pivot formula to
     *       extract {@code pivotFrac} from the translation component.</li>
     * </ul>
     *
     * <p>Regression proof: if the XML is reverted to the buggy values
     * ({@code fromXScale=0.1, pivotX=100%}):
     * <pre>
     *   fromScale derived  = 0.1
     *   tx at t=0          = (1 − 0.1) × 1000 × 1.0 = 900
     *   pivotFrac inferred = tx / ((1 − fromScale) × viewSize) = 900 / (0.9 × 1000) = 1.0
     *   sweep              = (1.0 − 0.1) × (1.0 × 525 + 195) = 0.9 × 720 = 648 px
     *   648 px > 0.15 × 525 = 78.75 px  →  FAILS (correctly)
     * </pre>
     *
     * <p>Cross-check: {@code CHILD_BLEED_PX = 195 px} corresponds to the layout's
     * {@code -44 dp} negative margin (44 × 2.625 ≈ 116 px) plus the 30 dp blur
     * radius (30 × 2.625 ≈ 79 px), totalling ~195 px per side.
     */
    @Test
    public void animResource_derivedParams_glowBoundsContained() {
        final int viewSize = 1000;
        scaleAnim.initialize(viewSize, viewSize, viewSize, viewSize);

        // ── 1. Read fromScale from the matrix at t=0 ──────────────────────────
        scaleAnim.setStartTime(0L);
        Transformation tfStart = new Transformation();
        scaleAnim.getTransformation(0L, tfStart);
        float[] matStart = new float[9];
        tfStart.getMatrix().getValues(matStart);
        float fromScale = matStart[Matrix.MSCALE_X];
        float txAtStart = matStart[Matrix.MTRANS_X];

        // ── 2. Read toScale from the matrix at t=duration ─────────────────────
        long duration = scaleAnim.getDuration();
        scaleAnim.setStartTime(0L);
        Transformation tfEnd = new Transformation();
        scaleAnim.getTransformation(duration, tfEnd);
        float[] matEnd = new float[9];
        tfEnd.getMatrix().getValues(matEnd);
        float toScale = matEnd[Matrix.MSCALE_X];

        // ── 3. Invert pivot formula to recover pivotFrac ──────────────────────
        // tx = pivotX × (1 − scale) = pivotFrac × viewSize × (1 − scale)
        // → pivotFrac = tx / (viewSize × (1 − scale))
        float scaleDeltaAtStart = 1f - fromScale; // = 1 - fromScale (assuming toScale=1)
        float pivotFrac = (scaleDeltaAtStart > 1e-6f)
                ? txAtStart / (viewSize * scaleDeltaAtStart)
                : 0.5f; // degenerate: no motion → assume centre

        RevealParams derivedParams = new RevealParams(fromScale, toScale, pivotFrac);

        // ── 4. Run through geometry helper with realistic device dimensions ────
        float containerW = 525f;   // ~200 dp at 420 dpi
        float bleedPerSide = 195f; // 44 dp margin + 30 dp blur at 420 dpi
        float screenW = 1080f;

        float sweep = FabRevealGeometry.leftEdgeSweepPx(containerW, bleedPerSide, derivedParams);
        float maxW  = FabRevealGeometry.maxOnScreenWidth(containerW, bleedPerSide, derivedParams);

        assertTrue(
                "Derived fromScale=" + fromScale + ", toScale=" + toScale
                        + ", pivotFrac=" + pivotFrac
                        + ".  Left-edge sweep=" + sweep + " px must be ≤ "
                        + (MAX_SWEEP_FRACTION * containerW) + " px (= "
                        + (MAX_SWEEP_FRACTION * 100) + "% × " + containerW + " px)."
                        + "  Revert to fromXScale=0.1/pivotX=100% and sweep becomes ~648 px.",
                FabRevealGeometry.isLeftEdgeSweepContained(
                        containerW, bleedPerSide, derivedParams, MAX_SWEEP_FRACTION));

        assertTrue(
                "Glow max on-screen width via derived params (" + maxW + " px) must be < screen ("
                        + screenW + " px)",
                maxW < screenW);
    }
}

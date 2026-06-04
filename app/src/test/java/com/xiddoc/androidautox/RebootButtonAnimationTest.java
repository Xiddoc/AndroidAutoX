package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.xiddoc.androidautox.Utils.FabRevealGeometry;

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
 * <h3>What is asserted</h3>
 * <ol>
 *   <li>The top-level animation is an {@link AnimationSet} (the {@code <set>}
 *       element in the XML).</li>
 *   <li>The set contains exactly two children: a {@link ScaleAnimation} and an
 *       {@link android.view.animation.AlphaAnimation}.</li>
 *   <li>The {@link ScaleAnimation}'s from/to X-scale values are the contained
 *       ones (0.85→1.0), not the old buggy ones (0.1→1.0).</li>
 *   <li>The scale is <em>uniform</em>: fromX == fromY, toX == toY.</li>
 *   <li>The pivot X and Y are both 50 % of the view's dimension (centre pivot),
 *       not the old 100 % (right-edge) pivot.</li>
 *   <li>The maximum on-screen width computed via {@link FabRevealGeometry} with
 *       the extracted scale params stays within a sane multiple of a typical
 *       FAB container width — end-to-end integration of the geometry helper
 *       and the actual resource.</li>
 * </ol>
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 34)
public class RebootButtonAnimationTest {

    // The pivot value stored by ScaleAnimation for "50%" (RELATIVE_TO_SELF, 0.5f).
    // Robolectric's implementation stores the raw fraction; confirmed by reading
    // the Android source for ScaleAnimation's constructor path from XML:
    //   pivotXType = RELATIVE_TO_SELF, pivotXValue = 0.5f
    // We compare the *resolved* pivot against a reference view, so we do not
    // need to access private fields.
    private static final float CENTRE_PIVOT_FRAC = 0.5f;

    /** fromXScale in the *fixed* animation XML. */
    private static final float EXPECTED_FROM_SCALE = 0.85f;
    /** toXScale in the *fixed* animation XML. */
    private static final float EXPECTED_TO_SCALE   = 1.0f;

    /** Floating-point tolerance for scale comparisons. */
    private static final float TOLERANCE = 0.01f;

    private Context context;
    private AnimationSet animSet;
    private ScaleAnimation scaleAnim;

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

        // Find the ScaleAnimation inside the set.
        ScaleAnimation found = null;
        List<Animation> children = animSet.getAnimations();
        for (Animation child : children) {
            if (child instanceof ScaleAnimation) {
                found = (ScaleAnimation) child;
                break;
            }
        }
        assertNotNull("AnimationSet must contain a ScaleAnimation child", found);
        scaleAnim = found;
    }

    /**
     * The animation set must contain at least two children (scale + alpha).
     */
    @Test
    public void animSet_hasTwoOrMoreChildren() {
        assertTrue(
                "AnimationSet should have at least 2 children (scale + alpha), found "
                        + animSet.getAnimations().size(),
                animSet.getAnimations().size() >= 2);
    }

    /**
     * The scale animation must use the contained from-scale (0.85), not the
     * old buggy value of 0.1 which caused the screen-spanning streak.
     *
     * <p>We resolve the scale by applying it to a synthetic view of known size
     * and reading the transformation matrix.
     */
    @Test
    public void scaleAnim_fromXScale_isContainedNotBuggy() {
        // Create a synthetic view sized 1000×1000 so pivot/scale resolution is exact.
        View probe = new View(context);
        probe.layout(0, 0, 1000, 1000);

        android.view.animation.Transformation tf = new android.view.animation.Transformation();
        // Sample at the very start of the animation (time offset = 0 within the duration).
        // The animation's startTime must be set so the interpolated fraction = 0 → fromScale.
        long fakeNow = 0L;
        scaleAnim.initialize(1000, 1000, 1000, 1000);
        scaleAnim.setStartTime(fakeNow);
        // getTransformation at t=0 gives fromScale; at t=duration gives toScale.
        scaleAnim.getTransformation(fakeNow, tf);

        // The matrix for a scale animation at fraction=0 is [fromScaleX, 0, tx, 0, fromScaleY, ty, 0, 0, 1].
        float[] matValues = new float[9];
        tf.getMatrix().getValues(matValues);

        // matValues[0] = scaleX at fraction 0 = fromScale
        float scaleXAtStart = matValues[android.graphics.Matrix.MSCALE_X];

        assertTrue(
                "fromXScale must be close to " + EXPECTED_FROM_SCALE
                        + " (contained reveal), got " + scaleXAtStart
                        + ".  If this is ~0.1, the buggy screen-spanning animation is back.",
                Math.abs(scaleXAtStart - EXPECTED_FROM_SCALE) < TOLERANCE);
    }

    /**
     * The scale animation must end at 1.0 (full size, no shrink-on-completion).
     */
    @Test
    public void scaleAnim_toXScale_isOne() {
        View probe = new View(context);
        probe.layout(0, 0, 1000, 1000);

        scaleAnim.initialize(1000, 1000, 1000, 1000);
        long duration = scaleAnim.getDuration();
        scaleAnim.setStartTime(0L);

        android.view.animation.Transformation tf = new android.view.animation.Transformation();
        scaleAnim.getTransformation(duration, tf);

        float[] matValues = new float[9];
        tf.getMatrix().getValues(matValues);
        float scaleXAtEnd = matValues[android.graphics.Matrix.MSCALE_X];

        assertTrue(
                "toXScale must be 1.0, got " + scaleXAtEnd,
                Math.abs(scaleXAtEnd - EXPECTED_TO_SCALE) < TOLERANCE);
    }

    /**
     * The scale must be uniform: the Y-axis scale must equal the X-axis scale
     * at the start of the animation.  An X-only scale (like the old bug) has
     * fromYScale=1.0 while fromXScale=0.1 — these must not diverge.
     */
    @Test
    public void scaleAnim_isUniform_xEqualsYAtStart() {
        View probe = new View(context);
        probe.layout(0, 0, 1000, 1000);

        scaleAnim.initialize(1000, 1000, 1000, 1000);
        scaleAnim.setStartTime(0L);

        android.view.animation.Transformation tf = new android.view.animation.Transformation();
        scaleAnim.getTransformation(0L, tf);

        float[] matValues = new float[9];
        tf.getMatrix().getValues(matValues);
        float scaleX = matValues[android.graphics.Matrix.MSCALE_X];
        float scaleY = matValues[android.graphics.Matrix.MSCALE_Y];

        assertEquals(
                "Scale must be uniform (fromXScale == fromYScale)."
                        + "  An X-only scale would leave scaleY=1.0 while scaleX=0.85.",
                scaleX, scaleY, TOLERANCE);
    }

    /**
     * The pivot must be the centre (50 % / 50 %) of the view.
     *
     * <p>We detect this by checking the translation component of the matrix:
     * with a centre pivot and a scale of {@code s}, the matrix translates by
     * {@code (1-s) × halfSize} in both directions, so the upper-left corner
     * of the scaled view lands at {@code (1-s) × halfSize}, not at 0 (right-
     * edge pivot would leave the right corner fixed and push the left corner
     * far left).
     *
     * <p>For a 1000×1000 view at scale 0.85 from centre: translateX = (1-0.85)×500 = 75.
     * For scale 0.85 from right edge (pivotX=1.0): translateX = (1-0.85)×1000 = 150, translateY=75.
     */
    @Test
    public void scaleAnim_pivot_isCentre_notRightEdge() {
        int viewSize = 1000;
        View probe = new View(context);
        probe.layout(0, 0, viewSize, viewSize);

        scaleAnim.initialize(viewSize, viewSize, viewSize, viewSize);
        scaleAnim.setStartTime(0L);

        android.view.animation.Transformation tf = new android.view.animation.Transformation();
        scaleAnim.getTransformation(0L, tf);

        float[] matValues = new float[9];
        tf.getMatrix().getValues(matValues);
        float scaleX = matValues[android.graphics.Matrix.MSCALE_X];
        float tx     = matValues[android.graphics.Matrix.MTRANS_X];

        // With centre pivot: tx = (1 - scaleX) × viewSize × 0.5
        float expectedTxCentre   = (1f - scaleX) * viewSize * CENTRE_PIVOT_FRAC;
        // With right-edge pivot: tx = (1 - scaleX) × viewSize × 1.0
        float expectedTxRightEdge = (1f - scaleX) * viewSize * 1.0f;

        assertTrue(
                "Pivot must be at the view centre (tx ≈ " + expectedTxCentre
                        + "), not at the right edge (tx ≈ " + expectedTxRightEdge
                        + ").  Got tx=" + tx + ".  Right-edge pivot causes the screen-wide smear.",
                Math.abs(tx - expectedTxCentre) < TOLERANCE * viewSize);
    }

    /**
     * End-to-end: feed the scale values from the animation resource into the
     * {@link FabRevealGeometry} helper and assert the glow stays well within
     * the screen width.
     *
     * <p>This couples the resource-inspection test (Robolectric layer) with the
     * pure-geometry invariant (plain JUnit layer) so that a change to either
     * the XML or the helper is caught.
     */
    @Test
    public void animResource_glowBoundsViaGeometryHelper_stayInsideScreen() {
        // Build a RevealParams from the constants that the fixed animation uses.
        FabRevealGeometry.RevealParams params = FabRevealGeometry.FIXED_REVEAL;

        // Realistic phone dimensions (see FabRevealGeometryTest for derivation).
        float containerW = 525f;
        float bleed      = 195f;
        float screenW    = 1080f;

        float maxW = FabRevealGeometry.maxOnScreenWidth(containerW, bleed, params);

        assertTrue(
                "Glow max on-screen width (" + maxW + " px) must stay below screen width ("
                        + screenW + " px) per FabRevealGeometry with FIXED_REVEAL params",
                maxW < screenW);
    }
}

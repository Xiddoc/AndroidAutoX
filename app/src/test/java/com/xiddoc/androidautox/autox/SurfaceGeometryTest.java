package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link SurfaceGeometry} — no Android runtime required.
 *
 * <p>Exhaustively covers all three {@link SurfaceGeometry.Action} outcomes and every
 * branch of the {@link SurfaceGeometry#decide} method, satisfying the 100% line +
 * branch coverage requirement.
 *
 * <h2>Decision rules under test</h2>
 * <ol>
 *   <li>{@code surfaceIdentityChanged == true} → {@link SurfaceGeometry.Action#RECREATE}
 *       regardless of dimension equality.</li>
 *   <li>{@code surfaceIdentityChanged == false} and all three of w/h/dpi equal
 *       → {@link SurfaceGeometry.Action#NOOP}.</li>
 *   <li>{@code surfaceIdentityChanged == false} and any dimension differs
 *       → {@link SurfaceGeometry.Action#RESIZE}.</li>
 * </ol>
 */
public class SurfaceGeometryTest {

    // ------------------------------------------------------------------
    // Action enum sanity
    // ------------------------------------------------------------------

    @Test
    public void action_allValuesExist() {
        assertNotNull(SurfaceGeometry.Action.NOOP);
        assertNotNull(SurfaceGeometry.Action.RESIZE);
        assertNotNull(SurfaceGeometry.Action.RECREATE);
        assertEquals(3, SurfaceGeometry.Action.values().length);
    }

    // ------------------------------------------------------------------
    // RECREATE — surfaceIdentityChanged == true (regardless of dims)
    // ------------------------------------------------------------------

    @Test
    public void decide_surfaceChanged_sameGeometry_returnsRECREATE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1920, 1080, 240,
                true);
        assertEquals(SurfaceGeometry.Action.RECREATE, result);
    }

    @Test
    public void decide_surfaceChanged_differentWidth_returnsRECREATE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1280, 1080, 240,
                true);
        assertEquals(SurfaceGeometry.Action.RECREATE, result);
    }

    @Test
    public void decide_surfaceChanged_differentHeight_returnsRECREATE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1920, 720, 240,
                true);
        assertEquals(SurfaceGeometry.Action.RECREATE, result);
    }

    @Test
    public void decide_surfaceChanged_differentDpi_returnsRECREATE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1920, 1080, 160,
                true);
        assertEquals(SurfaceGeometry.Action.RECREATE, result);
    }

    @Test
    public void decide_surfaceChanged_allDifferent_returnsRECREATE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1280, 720, 120,
                true);
        assertEquals(SurfaceGeometry.Action.RECREATE, result);
    }

    // ------------------------------------------------------------------
    // NOOP — surfaceIdentityChanged == false, all dims equal
    // ------------------------------------------------------------------

    @Test
    public void decide_noSurfaceChange_sameDims_returnsNOOP() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1920, 1080, 240,
                false);
        assertEquals(SurfaceGeometry.Action.NOOP, result);
    }

    @Test
    public void decide_noSurfaceChange_minimalDims_returnsNOOP() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1, 1, 1,
                1, 1, 1,
                false);
        assertEquals(SurfaceGeometry.Action.NOOP, result);
    }

    @Test
    public void decide_noSurfaceChange_largeDims_returnsNOOP() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                3840, 2160, 480,
                3840, 2160, 480,
                false);
        assertEquals(SurfaceGeometry.Action.NOOP, result);
    }

    // ------------------------------------------------------------------
    // RESIZE — surfaceIdentityChanged == false, at least one dim differs
    // ------------------------------------------------------------------

    @Test
    public void decide_noSurfaceChange_differentWidth_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1280, 1080, 240,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    @Test
    public void decide_noSurfaceChange_differentHeight_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1920, 720, 240,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    @Test
    public void decide_noSurfaceChange_differentDpi_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1920, 1080, 160,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    @Test
    public void decide_noSurfaceChange_widthAndHeightDiffer_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1280, 720, 240,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    @Test
    public void decide_noSurfaceChange_widthAndDpiDiffer_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1280, 1080, 160,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    @Test
    public void decide_noSurfaceChange_heightAndDpiDiffer_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1920, 720, 160,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    @Test
    public void decide_noSurfaceChange_allDimsDiffer_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 1080, 240,
                1280, 720, 120,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    // ------------------------------------------------------------------
    // Boundary: increasing vs decreasing dimensions
    // ------------------------------------------------------------------

    @Test
    public void decide_noSurfaceChange_widthIncreased_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                800, 600, 160,
                1920, 600, 160,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    @Test
    public void decide_noSurfaceChange_widthDecreased_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                1920, 600, 160,
                800, 600, 160,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    @Test
    public void decide_noSurfaceChange_heightIncreased_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                800, 480, 160,
                800, 600, 160,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    @Test
    public void decide_noSurfaceChange_dpiIncreased_returnsRESIZE() {
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                800, 600, 120,
                800, 600, 240,
                false);
        assertEquals(SurfaceGeometry.Action.RESIZE, result);
    }

    // ------------------------------------------------------------------
    // Confirm RECREATE priority over dimension change
    // ------------------------------------------------------------------

    @Test
    public void decide_surfaceChanged_prioritisedOverDimChange() {
        // Even if all dims differ, RECREATE is returned when surface identity changed.
        SurfaceGeometry.Action result = SurfaceGeometry.decide(
                100, 100, 100,
                200, 200, 200,
                true);
        assertEquals(SurfaceGeometry.Action.RECREATE, result);
    }
}

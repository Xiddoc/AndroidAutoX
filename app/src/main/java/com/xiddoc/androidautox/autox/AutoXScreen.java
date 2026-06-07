package com.xiddoc.androidautox.autox;

import android.content.Intent;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;

/**
 * Jetpack Car App {@link Screen} that drives the AutoX isolated virtual-display pipeline.
 *
 * <h2>Pipeline overview</h2>
 * <pre>
 *   Android Auto host
 *       │  SurfaceContainer (width, height, dpi, Surface)
 *       ▼
 *   AutoXScreen (SurfaceCallback)
 *       │  builds AutoXDisplaySpec from container geometry
 *       │  creates VirtualDisplayController → DisplayManager.createVirtualDisplay
 *       │  launched guest app via AppLauncher → ActivityOptions.setLaunchDisplayId
 *       │  routes touch events via CoordinateTranslator + GestureInjector
 *       ▼
 *   Guest app renders on VirtualDisplay
 *       └─ frames flow back through Surface to Android Auto head unit
 * </pre>
 *
 * <h2>Surface lifecycle — resize vs recreate</h2>
 * <ul>
 *   <li>{@link SurfaceCallback#onSurfaceAvailable(SurfaceContainer)}: the host provides a
 *       surface.  If a display already exists, {@link SurfaceGeometry#decide} is consulted:
 *       <ul>
 *         <li>{@link SurfaceGeometry.Action#NOOP} — geometry unchanged; no-op.</li>
 *         <li>{@link SurfaceGeometry.Action#RESIZE} — same {@link Surface} object, new size/dpi;
 *             calls {@link VirtualDisplayController#resize(AutoXDisplaySpec)} to update the
 *             existing display in-place (avoids an unnecessary app relaunch).</li>
 *         <li>{@link SurfaceGeometry.Action#RECREATE} — the host tore down and re-created the
 *             surface; the old display is released and a new one is created.</li>
 *       </ul>
 *   </li>
 *   <li>{@link SurfaceCallback#onSurfaceDestroyed(SurfaceContainer)}: releases the display
 *       (idempotent).</li>
 *   <li>{@link SurfaceCallback#onVisibleAreaChanged(Rect)}: logs the new visible area for
 *       future coordinate-clipping use.</li>
 *   <li>{@link SurfaceCallback#onStableAreaChanged(Rect)}: logs the new stable area for
 *       future content-inset use.</li>
 * </ul>
 *
 * <h2>Gesture routing</h2>
 * <p>Car-digitizer touch events received via {@link SurfaceCallback#onClick},
 * {@link SurfaceCallback#onScroll}, and {@link SurfaceCallback#onFling} are translated
 * from car-surface coordinates to virtual-display coordinates via
 * {@link CoordinateTranslator} and dispatched as {@link GestureSpec} objects through
 * the {@link GestureInjector} seam.
 *
 * <h2>Template</h2>
 * <p>{@link #onGetTemplate()} returns a minimal {@link NavigationTemplate} with an
 * {@link Action#PAN} primary action. The surface is rendered beneath the template's
 * transparent overlay by the Android Auto host.
 *
 * <p>This class is a framework-entry point and is excluded from the JaCoCo coverage gate.
 */
public final class AutoXScreen extends Screen implements SurfaceCallback {

    private static final String TAG = "AndroidAutoX";

    /** Swipe gesture duration used when translating scroll/fling events. */
    private static final long SWIPE_DURATION_MS = 200L;

    private final AppLauncher appLauncher;
    private final GestureInjector gestureInjector;

    /** Non-null while a surface is available. */
    private VirtualDisplayController displayController;

    /** Spec for the car surface; set when the surface becomes available. */
    private AutoXDisplaySpec carSpec;

    /**
     * The {@link Surface} object currently backing the virtual display.
     * Tracked so that {@code onSurfaceAvailable} can detect surface-identity changes
     * (host tore down and re-created the surface vs. just resizing it in place).
     */
    private Surface currentSurface;

    /**
     * Constructs an {@code AutoXScreen} with production-wired collaborators.
     *
     * @param carContext the {@link CarContext} provided by the framework
     */
    public AutoXScreen(@NonNull CarContext carContext) {
        this(carContext,
                new AppLauncher(carContext),
                new ReflectiveGestureInjector(
                        carContext.getSystemService(InputManager.class)));
    }

    /**
     * Package-private constructor for dependency injection (used in tests and for
     * wiring custom collaborators).
     *
     * @param carContext      the {@link CarContext} provided by the framework
     * @param appLauncher     launcher to place a guest app on the virtual display
     * @param gestureInjector injector to route car touch events to the virtual display
     */
    AutoXScreen(@NonNull CarContext carContext,
                @NonNull AppLauncher appLauncher,
                @NonNull GestureInjector gestureInjector) {
        super(carContext);
        this.appLauncher = appLauncher;
        this.gestureInjector = gestureInjector;
        // Register this screen as the SurfaceCallback so the host delivers surface events.
        carContext.getCarService(AppManager.class).setSurfaceCallback(this);
    }

    // ------------------------------------------------------------------
    // Screen
    // ------------------------------------------------------------------

    /**
     * Returns a minimal {@link NavigationTemplate} with a PAN action that instructs
     * the Android Auto host to render the surface (provided by the car app SDK) beneath
     * the template overlay.
     */
    @NonNull
    @Override
    public Template onGetTemplate() {
        return new NavigationTemplate.Builder()
                .setActionStrip(
                        new androidx.car.app.model.ActionStrip.Builder()
                                .addAction(Action.PAN)
                                .build())
                .build();
    }

    // ------------------------------------------------------------------
    // SurfaceCallback
    // ------------------------------------------------------------------

    /**
     * Called by the Android Auto host when a drawable surface is ready or when
     * the surface geometry changes.
     *
     * <p>Builds an {@link AutoXDisplaySpec} from the container geometry, then applies
     * the resize-vs-recreate policy via {@link SurfaceGeometry#decide}:
     * <ul>
     *   <li>{@link SurfaceGeometry.Action#NOOP}: geometry unchanged — nothing to do.</li>
     *   <li>{@link SurfaceGeometry.Action#RESIZE}: same surface, new size/dpi — resizes the
     *       existing virtual display in place via
     *       {@link VirtualDisplayController#resize(AutoXDisplaySpec)}.</li>
     *   <li>{@link SurfaceGeometry.Action#RECREATE}: new surface object — releases the old
     *       display and creates a fresh one, then re-launches the guest app.</li>
     * </ul>
     * When there is no existing display yet (first call), a display is always created.
     */
    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer surfaceContainer) {
        Log.d(TAG, "AutoXScreen.onSurfaceAvailable: "
                + surfaceContainer.getWidth() + "x" + surfaceContainer.getHeight()
                + " dpi=" + surfaceContainer.getDpi());

        // Guard against a zero-size container (can happen during initialization).
        if (surfaceContainer.getWidth() <= 0 || surfaceContainer.getHeight() <= 0
                || surfaceContainer.getDpi() <= 0 || surfaceContainer.getSurface() == null) {
            Log.w(TAG, "AutoXScreen.onSurfaceAvailable: invalid container — skipping");
            return;
        }

        AutoXDisplaySpec newSpec = new AutoXDisplaySpec(
                surfaceContainer.getWidth(),
                surfaceContainer.getHeight(),
                surfaceContainer.getDpi());
        Surface newSurface = surfaceContainer.getSurface();

        if (displayController == null) {
            // First call: no display exists yet — always create.
            createDisplay(newSpec, newSurface);
            return;
        }

        // A display already exists. Consult SurfaceGeometry to decide the action.
        AutoXDisplaySpec oldSpec = carSpec;
        boolean surfaceIdentityChanged = (currentSurface != newSurface);

        SurfaceGeometry.Action action = SurfaceGeometry.decide(
                oldSpec.getWidth(), oldSpec.getHeight(), oldSpec.getDensityDpi(),
                newSpec.getWidth(), newSpec.getHeight(), newSpec.getDensityDpi(),
                surfaceIdentityChanged);

        Log.d(TAG, "AutoXScreen.onSurfaceAvailable: SurfaceGeometry.decide → " + action);

        switch (action) {
            case NOOP:
                // Geometry and surface are identical — nothing to do.
                break;
            case RESIZE:
                // Same surface object, different size/dpi: resize the existing display.
                carSpec = newSpec;
                displayController.resize(newSpec);
                break;
            case RECREATE:
                // New surface object: release the old display and recreate.
                releaseDisplay();
                createDisplay(newSpec, newSurface);
                break;
        }
    }

    /**
     * Called by the Android Auto host when the visible area of the surface changes
     * (e.g. an overlay is drawn on part of the surface by the host template).
     *
     * <p>The visible area describes the portion of the surface that is not occluded by
     * host-drawn UI chrome. Logged here for diagnostics; future implementations can use
     * this to clip the coordinate-translation range or inset the guest app's content.
     *
     * @param visibleArea the current visible (non-occluded) rectangle on the surface
     */
    @Override
    public void onVisibleAreaChanged(@NonNull Rect visibleArea) {
        Log.d(TAG, "AutoXScreen.onVisibleAreaChanged: " + visibleArea);
        // Stored for future use (e.g. touch-coordinate clamping to visible area).
        // No action needed yet: the CoordinateTranslator clamps to the full display bounds.
    }

    /**
     * Called by the Android Auto host when the stable area of the surface changes.
     *
     * <p>The stable area is the region that remains consistently available regardless
     * of temporary UI chrome (e.g. keyboard, overlays). Logged here for diagnostics;
     * future implementations can use this to inset content layout.
     *
     * @param stableArea the current stable rectangle on the surface
     */
    @Override
    public void onStableAreaChanged(@NonNull Rect stableArea) {
        Log.d(TAG, "AutoXScreen.onStableAreaChanged: " + stableArea);
        // Stored for future use (e.g. content-inset guidance for the guest app).
    }

    /**
     * Called by the Android Auto host when the surface is no longer available (e.g. the
     * projection session ended or the user navigated away). Releases the virtual display
     * idempotently.
     */
    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer surfaceContainer) {
        Log.d(TAG, "AutoXScreen.onSurfaceDestroyed");
        releaseDisplay();
    }

    // ------------------------------------------------------------------
    // Touch routing
    // ------------------------------------------------------------------

    /**
     * Routes a tap from the car digitizer to the virtual display.
     *
     * <p>The car-surface coordinate is translated to virtual-display space via
     * {@link CoordinateTranslator}, then injected as a {@link GestureSpec#tap}.
     */
    @Override
    public void onClick(float x, float y) {
        if (displayController == null || carSpec == null) return;
        // Note: today the virtual display is created at the car's resolution, so
        // virtSpec == carSpec (identity) at runtime; the routing math is still correct
        // when they differ (e.g. forced-vertical layouts).
        AutoXDisplaySpec virtSpec = displayController.getSpec();
        GestureSpec spec = TouchRouter.routeTap(
                carSpec, virtSpec, displayController.getDisplayId(), x, y);
        gestureInjector.inject(spec);
    }

    /**
     * Routes a scroll from the car digitizer to the virtual display as a swipe gesture.
     *
     * @param distanceX horizontal scroll distance in car-surface pixels
     * @param distanceY vertical scroll distance in car-surface pixels
     */
    @Override
    public void onScroll(float distanceX, float distanceY) {
        if (displayController == null || carSpec == null) return;
        AutoXDisplaySpec virtSpec = displayController.getSpec();
        GestureSpec spec = TouchRouter.routeScroll(
                carSpec, virtSpec, displayController.getDisplayId(),
                distanceX, distanceY, SWIPE_DURATION_MS);
        gestureInjector.inject(spec);
    }

    /**
     * Routes a fling from the car digitizer to the virtual display as a swipe gesture.
     *
     * @param velocityX horizontal fling velocity in pixels per second
     * @param velocityY vertical fling velocity in pixels per second
     */
    @Override
    public void onFling(float velocityX, float velocityY) {
        if (displayController == null || carSpec == null) return;
        AutoXDisplaySpec virtSpec = displayController.getSpec();
        GestureSpec spec = TouchRouter.routeFling(
                carSpec, virtSpec, displayController.getDisplayId(),
                velocityX, velocityY, SWIPE_DURATION_MS);
        gestureInjector.inject(spec);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Creates a new virtual display backed by {@code surface} with the given {@code spec},
     * starts the foreground service, and launches the default guest app.
     *
     * <p>On failure to create the display, the state is left clean (no partial display
     * or service started).
     */
    private void createDisplay(AutoXDisplaySpec spec, Surface surface) {
        DisplayManager dm = getCarContext().getSystemService(DisplayManager.class);
        try {
            displayController = new VirtualDisplayController(dm, spec, surface);
        } catch (RuntimeException e) {
            Log.e(TAG, "AutoXScreen.createDisplay: failed to create virtual display", e);
            return;
        }
        carSpec = spec;
        currentSurface = surface;

        // Start the foreground service to keep the projection session alive (wake lock +
        // ongoing notification). Tied to the surface lifecycle: stopped in releaseDisplay().
        getCarContext().startForegroundService(
                new Intent(getCarContext(), AutoXForegroundService.class));

        // Launch the first default app (e.g. YouTube) onto the new virtual display.
        AutoXTargetApp defaultApp = AutoXAppRegistry.defaults().get(0);
        boolean launched = appLauncher.launch(
                defaultApp.packageName, displayController.getDisplayId());
        if (!launched) {
            Log.w(TAG, "AutoXScreen: default app '" + defaultApp.packageName
                    + "' could not be launched — app may not be installed on this device");
        }
    }

    /**
     * Releases the virtual display and stops the foreground service.
     * Idempotent: safe to call even when no display is active.
     */
    private void releaseDisplay() {
        if (displayController != null) {
            displayController.release();
            displayController = null;
            // Stop the foreground service: its lifecycle is tied to an active display.
            getCarContext().stopService(
                    new Intent(getCarContext(), AutoXForegroundService.class));
        }
        carSpec = null;
        currentSurface = null;
    }
}

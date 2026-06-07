package com.xiddoc.androidautox.autox;

import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.util.Log;

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
 * <h2>Surface lifecycle</h2>
 * <ul>
 *   <li>{@link SurfaceCallback#onSurfaceAvailable(SurfaceContainer)}: creates the
 *       {@link VirtualDisplayController} and optionally launches the first default app.</li>
 *   <li>{@link SurfaceCallback#onSurfaceDestroyed(SurfaceContainer)}: releases the
 *       {@link VirtualDisplayController}, freeing the display resource.</li>
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
     * Called by the Android Auto host when a drawable surface is ready.
     *
     * <p>Builds an {@link AutoXDisplaySpec} from the container geometry, creates a
     * {@link VirtualDisplayController} backed by the provided surface, then launches
     * the first default app from {@link AutoXAppRegistry} onto the virtual display.
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

        carSpec = new AutoXDisplaySpec(
                surfaceContainer.getWidth(),
                surfaceContainer.getHeight(),
                surfaceContainer.getDpi());

        DisplayManager dm = getCarContext().getSystemService(DisplayManager.class);
        try {
            displayController = new VirtualDisplayController(
                    dm, carSpec, surfaceContainer.getSurface());
        } catch (RuntimeException e) {
            Log.e(TAG, "AutoXScreen.onSurfaceAvailable: failed to create virtual display", e);
            return;
        }

        // Launch the first default app (e.g. YouTube) onto the new virtual display.
        AutoXTargetApp defaultApp = AutoXAppRegistry.defaults().get(0);
        boolean launched = appLauncher.launch(defaultApp.packageName, displayController.getDisplayId());
        if (!launched) {
            Log.w(TAG, "AutoXScreen: default app '" + defaultApp.packageName
                    + "' could not be launched — app may not be installed on this device");
        }
    }

    /**
     * Called by the Android Auto host when the surface is no longer available (e.g. the
     * projection session ended or the user navigated away). Releases the virtual display.
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
        AutoXDisplaySpec virtSpec = new AutoXDisplaySpec(
                displayController.getDisplayId() > 0 ? carSpec.getWidth() : carSpec.getWidth(),
                carSpec.getHeight(), carSpec.getDensityDpi());
        CoordinateTranslator translator = new CoordinateTranslator(carSpec, virtSpec);
        CoordinateTranslator.TranslatedPoint pt = translator.translate(x, y);
        GestureSpec spec = GestureSpec.tap(displayController.getDisplayId(), pt.getX(), pt.getY());
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
        // Treat the scroll as a swipe starting from the centre of the car surface.
        float startX = carSpec.getWidth() / 2.0f;
        float startY = carSpec.getHeight() / 2.0f;
        float endX = startX - distanceX;
        float endY = startY - distanceY;
        CoordinateTranslator translator = new CoordinateTranslator(carSpec, carSpec);
        CoordinateTranslator.TranslatedPoint start = translator.translate(startX, startY);
        CoordinateTranslator.TranslatedPoint end = translator.translate(endX, endY);
        GestureSpec spec = GestureSpec.swipe(
                displayController.getDisplayId(),
                start.getX(), start.getY(),
                end.getX(), end.getY(),
                SWIPE_DURATION_MS);
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
        float startX = carSpec.getWidth() / 2.0f;
        float startY = carSpec.getHeight() / 2.0f;
        // Project the fling velocity over the swipe duration to an end point.
        float endX = startX + velocityX * (SWIPE_DURATION_MS / 1000.0f);
        float endY = startY + velocityY * (SWIPE_DURATION_MS / 1000.0f);
        CoordinateTranslator translator = new CoordinateTranslator(carSpec, carSpec);
        CoordinateTranslator.TranslatedPoint start = translator.translate(startX, startY);
        CoordinateTranslator.TranslatedPoint end = translator.translate(endX, endY);
        GestureSpec spec = GestureSpec.swipe(
                displayController.getDisplayId(),
                start.getX(), start.getY(),
                end.getX(), end.getY(),
                SWIPE_DURATION_MS);
        gestureInjector.inject(spec);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private void releaseDisplay() {
        if (displayController != null) {
            displayController.release();
            displayController = null;
        }
        carSpec = null;
    }
}

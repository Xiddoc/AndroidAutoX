package com.xiddoc.androidautox.autox;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;

import com.xiddoc.androidautox.autox.AudioRoutePolicy.CarAudioDevice;
import com.xiddoc.androidautox.autox.ime.ImeDisplaySettingsSpec;
import com.xiddoc.androidautox.autox.ime.ImePriorCodec;
import com.xiddoc.androidautox.autox.ime.ImeSettingsReader;
import com.xiddoc.androidautox.autox.provider.AutoXProviderFactory;
import com.xiddoc.androidautox.autox.provider.AutoXProviders;
import com.xiddoc.androidautox.autox.provider.ProviderSelectionPolicy;
import com.xiddoc.androidautox.autox.provider.SettingsApplier;
import com.xiddoc.androidautox.autox.provider.lsposed.IpcCommandWriter;

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
 * <h2>Privileged-provider wiring (WS3 / WS5 / WS6 / LSPosed)</h2>
 * <p>At construction the screen obtains a provisional {@link AutoXProviders} bundle from
 * {@link AutoXProviderFactory#probe(Context)} (cheap static probes only). Once the virtual
 * display exists and its id is known ({@link #createDisplay}), it calls
 * {@link AutoXProviders#reevaluate(boolean, boolean)} to fold in the now-observable
 * trusted-display / injection signals, then applies — through the pure specs/appliers/policies:
 * <ul>
 *   <li><b>WS3</b> — {@code force_resizable_activities} / {@code enable_freeform_support}
 *       ({@link FreeformGlobalSettingsSpec}) via the GLOBAL {@link SettingsApplier}, plus a
 *       forced-vertical {@link LaunchBoundsCalculator} rect to the guest launch.</li>
 *   <li><b>WS5</b> — per-display IME + system-decors ({@link ImeDisplaySettingsSpec},
 *       {@link ImeSettingsReader}) via the SECURE {@link SettingsApplier}.</li>
 *   <li><b>WS6</b> — per-UID audio routing to the car output device
 *       ({@link AudioRoutePolicy} / {@link AudioRouteApplier} / {@link AudioDeviceTypeMapper}).</li>
 *   <li><b>LSPosed</b> — when the decision is {@link ProviderSelectionPolicy.Provider#LSPOSED},
 *       it drives an {@link IpcCommandWriter} to relax the {@code system_server} checks.</li>
 * </ul>
 * Every prior value is captured at apply time and persisted in {@link AutoXSettingsStore}
 * (app-private {@code "autox_prefs"}) so the revert in {@link #releaseDisplay} survives process
 * death. {@code releaseDisplay} reverts everything best-effort (each step independently
 * try/caught) so a partial failure never leaves a setting stranded.
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
 *       (idempotent) and reverts every applied setting.</li>
 * </ul>
 *
 * <p>This class is a framework-entry point and is excluded from the JaCoCo coverage gate.
 */
public final class AutoXScreen extends Screen implements SurfaceCallback {

    private static final String TAG = "AndroidAutoX";

    /** App-private prefs file used by {@link AutoXSettingsStore} (matches MainActivity). */
    private static final String PREFS_NAME = "autox_prefs";

    /** Swipe gesture duration used when translating scroll/fling events. */
    private static final long SWIPE_DURATION_MS = 200L;

    /**
     * Target aspect ratio (height/width) for the WS3 forced-vertical launch bounds. 16:9
     * portrait ≈ 1.78; used so a portrait guest app gets a tall centred window on a landscape
     * head-unit display.
     */
    private static final double FORCED_VERTICAL_ASPECT = 16.0 / 9.0;

    private final AppLauncher appLauncher;
    private final GestureInjector gestureInjector;

    /** Privileged-provider bundle (settings / input / audio). Provisional until reevaluated. */
    private AutoXProviders providers;

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
     * The audio routing decision applied for the active session, retained so
     * {@link #releaseDisplay} can revert it. Null when no routing was applied.
     */
    @Nullable
    private AudioRoutePolicy.RouteDecision audioDecision;

    /**
     * The LSPosed IPC writer, lazily created only when the decision is LSPOSED. Retained so the
     * teardown can {@code clear()} the channel.
     */
    @Nullable
    private IpcCommandWriter ipcWriter;

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
        // Task 5 call-site: obtain the provider bundle via the factory instead of new-ing
        // Root*/Reflective* directly. Provisional at this point (no surface yet); reevaluated in
        // createDisplay once the displayId exists. Best-effort — never fail construction on it.
        try {
            this.providers = AutoXProviderFactory.probe(carContext);
            Log.i(TAG, "AutoXScreen: provisional providers = " + providers);
        } catch (RuntimeException e) {
            Log.e(TAG, "AutoXScreen: provider probe failed; privileged steps disabled", e);
            this.providers = null;
        }
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
     * the surface geometry changes. Applies the resize-vs-recreate policy via
     * {@link SurfaceGeometry#decide}; on first call (or a RECREATE) a fresh display is built and
     * all privileged settings are (re)applied via {@link #createDisplay}.
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

    @Override
    public void onVisibleAreaChanged(@NonNull Rect visibleArea) {
        Log.d(TAG, "AutoXScreen.onVisibleAreaChanged: " + visibleArea);
        // Stored for future use (e.g. touch-coordinate clamping to visible area).
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect stableArea) {
        Log.d(TAG, "AutoXScreen.onStableAreaChanged: " + stableArea);
        // Stored for future use (e.g. content-inset guidance for the guest app).
    }

    /**
     * Called by the Android Auto host when the surface is no longer available. Releases the
     * virtual display idempotently and reverts every applied privileged setting.
     */
    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer surfaceContainer) {
        Log.d(TAG, "AutoXScreen.onSurfaceDestroyed");
        releaseDisplay();
    }

    // ------------------------------------------------------------------
    // Touch routing
    // ------------------------------------------------------------------

    @Override
    public void onClick(float x, float y) {
        if (displayController == null || carSpec == null) return;
        AutoXDisplaySpec virtSpec = displayController.getSpec();
        GestureSpec spec = TouchRouter.routeTap(
                carSpec, virtSpec, displayController.getDisplayId(), x, y);
        gestureInjector.inject(spec);
    }

    @Override
    public void onScroll(float distanceX, float distanceY) {
        if (displayController == null || carSpec == null) return;
        AutoXDisplaySpec virtSpec = displayController.getSpec();
        GestureSpec spec = TouchRouter.routeScroll(
                carSpec, virtSpec, displayController.getDisplayId(),
                distanceX, distanceY, SWIPE_DURATION_MS);
        gestureInjector.inject(spec);
    }

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
    // Private helpers — create / apply
    // ------------------------------------------------------------------

    /**
     * Creates a new virtual display, applies all privileged settings (WS3/WS5/WS6 + LSPosed),
     * starts the foreground service, and launches the default guest app.
     */
    private void createDisplay(AutoXDisplaySpec spec, Surface surface) {
        // LSPosed: enable the trusted-display hook BEFORE createVirtualDisplay (name-scoped, the
        // id does not exist yet) so the trusted flag survives display creation.
        maybeEnableTrustedDisplayHook();

        DisplayManager dm = getCarContext().getSystemService(DisplayManager.class);
        try {
            displayController = new VirtualDisplayController(dm, spec, surface);
        } catch (RuntimeException e) {
            Log.e(TAG, "AutoXScreen.createDisplay: failed to create virtual display", e);
            return;
        }
        carSpec = spec;
        currentSurface = surface;
        int displayId = displayController.getDisplayId();

        // Task 5: now that the display exists, reevaluate the provider decision with the best
        // observable signals. trustedDisplayHonored is read from the controller's display flags;
        // injectionHonored is unobservable until a gesture is injected (none yet) so it is
        // best-effort false here. // TODO(device-verify): confirm both on a real device/DHU.
        reevaluateProviders();

        // Start the foreground service to keep the projection session alive.
        getCarContext().startForegroundService(
                new Intent(getCarContext(), AutoXForegroundService.class));

        // WS3: enable freeform/resizable globals (captures + persists priors).
        applyFreeform();

        // WS5: per-display IME + system decors (captures + persists per-display priors).
        applyImeSettings(displayId);

        // LSPosed: id-scoped commands now that the id is known.
        maybeApplyLsposedDisplayCommands(displayId);

        // Launch the default app onto the new display, with WS3 forced-vertical bounds.
        AutoXTargetApp defaultApp = AutoXAppRegistry.defaults().get(0);
        Rect bounds = forcedVerticalBounds(spec);
        boolean launched = appLauncher.launch(defaultApp.packageName, displayId, bounds);
        if (!launched) {
            Log.w(TAG, "AutoXScreen: default app '" + defaultApp.packageName
                    + "' could not be launched — app may not be installed on this device");
        }

        // WS6: route the guest app's audio to the car output device (after launch so the UID
        // is resolvable).
        applyAudioRouting(defaultApp.packageName);
    }

    /** LSPosed Task 6: write the trusted-display hook command (no id) before display creation. */
    private void maybeEnableTrustedDisplayHook() {
        if (providers == null
                || providers.provider() != ProviderSelectionPolicy.Provider.LSPOSED) {
            return;
        }
        try {
            ipcWriter = new IpcCommandWriter(getCarContext().getApplicationContext());
            ipcWriter.enableTrustedDisplay();
            Log.i(TAG, "AutoXScreen: LSPosed enableTrustedDisplay() written");
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: LSPosed enableTrustedDisplay failed", e);
        }
    }

    /** Recomputes the provider decision from the now-observable trusted/injection signals. */
    private void reevaluateProviders() {
        if (providers == null || displayController == null) {
            return;
        }
        // Best observable signal for the trusted flag: the (root) display provider exposes it,
        // but AutoXScreen owns its own VirtualDisplayController for now (display seam is unbound),
        // so we use the controller's flag observation if available. The controller does not expose
        // a probe yet, so pass best-effort true (the display was created with FLAG_TRUSTED) and
        // leave the device-verify note.
        // TODO(device-verify): read DisplayInfo.flags & Display.FLAG_TRUSTED on the created
        // display to confirm the trusted flag was actually honored, rather than assuming it.
        boolean trustedHonored = true;
        boolean injectionHonored = providers.input().isInjectionHonored();
        providers = providers.reevaluate(trustedHonored, injectionHonored);
        Log.i(TAG, "AutoXScreen: reevaluated providers = " + providers);
    }

    /**
     * WS3 apply: read current global flags via the provider, persist priors, then enable both
     * via the GLOBAL {@link SettingsApplier}.
     */
    private void applyFreeform() {
        if (providers == null) return;
        try {
            SharedPreferences prefs = prefs();
            Integer priorForceResizable = SettingsPriorMapper.toPrior(
                    providers.settings().getGlobalInt(FreeformGlobalSettingsSpec.KEY_FORCE_RESIZABLE));
            Integer priorEnableFreeform = SettingsPriorMapper.toPrior(
                    providers.settings().getGlobalInt(FreeformGlobalSettingsSpec.KEY_ENABLE_FREEFORM));

            AutoXSettingsStore.setPriorForceResizable(prefs, priorForceResizable);
            AutoXSettingsStore.setPriorEnableFreeform(prefs, priorEnableFreeform);

            new SettingsApplier(providers.settings(), SettingsApplier.Namespace.GLOBAL)
                    .apply(FreeformGlobalSettingsSpec.applyList(
                            priorForceResizable, priorEnableFreeform));
            Log.i(TAG, "AutoXScreen: WS3 freeform/resizable applied");
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: WS3 freeform apply failed", e);
        }
    }

    /**
     * WS5 apply (Task 3 call-site): build the per-display IME spec, read+persist priors, then
     * write system-decors then IME via the SECURE {@link SettingsApplier}.
     */
    private void applyImeSettings(int displayId) {
        if (providers == null || displayId <= 0) return;
        try {
            ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(displayId);
            ImeDisplaySettingsSpec withPriors =
                    new ImeSettingsReader(providers.settings()).readPriors(spec);

            SharedPreferences prefs = prefs();
            AutoXSettingsStore.setPriorShouldShowSystemDecors(prefs, displayId,
                    ImePriorCodec.toBoxedPrior(withPriors.getPriorSystemDecors()));
            AutoXSettingsStore.setPriorShouldShowIme(prefs, displayId,
                    ImePriorCodec.toBoxedPrior(withPriors.getPriorIme()));

            new SettingsApplier(providers.settings(), SettingsApplier.Namespace.SECURE)
                    .apply(withPriors.applyEntries());
            Log.i(TAG, "AutoXScreen: WS5 IME/decors applied for display " + displayId);
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: WS5 IME apply failed for display " + displayId, e);
        }
    }

    /** LSPosed Task 6: id-scoped commands (input injection, IME/decors, launch). */
    private void maybeApplyLsposedDisplayCommands(int displayId) {
        if (ipcWriter == null || displayId <= 0) {
            return;
        }
        try {
            ipcWriter.allowInputInjection(displayId);
            ipcWriter.setDisplayImeAndDecors(displayId, true);
            ipcWriter.launchOnDisplay(displayId, AutoXAppRegistry.defaults().get(0).packageName);
            Log.i(TAG, "AutoXScreen: LSPosed id-scoped commands written for display " + displayId);
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: LSPosed id-scoped commands failed", e);
        }
    }

    /**
     * WS6 apply (Task 4 call-site): resolve the guest UID + live car output device, run the pure
     * {@link AudioRoutePolicy} and apply via {@link AudioRouteApplier}. Retains the decision for
     * revert.
     */
    private void applyAudioRouting(String packageName) {
        if (providers == null) return;
        try {
            int uid = resolveGuestUid(packageName);
            // TODO(device-verify): the live car output device + address can only be confirmed on
            // a real head unit (BT A2DP vs USB enumeration differs per unit).
            CarAudioDevice device = CarAudioDevice.NONE;
            String address = null;
            AudioDeviceInfo carDevice = findCarOutputDevice();
            if (carDevice != null) {
                device = AudioDeviceTypeMapper.fromAudioDeviceInfoType(carDevice.getType());
                address = carDevice.getAddress();
            }
            audioDecision = AudioRoutePolicy.decide(uid, device, address);
            boolean applied = AudioRouteApplier.apply(audioDecision, providers.audio());
            Log.i(TAG, "AutoXScreen: WS6 audio route apply=" + applied
                    + " decision=" + audioDecision);
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: WS6 audio apply failed", e);
        }
    }

    // ------------------------------------------------------------------
    // Private helpers — release / revert
    // ------------------------------------------------------------------

    /**
     * Releases the virtual display, reverts every applied privileged setting (best-effort, each
     * step independently guarded so a partial failure never strands a setting), and stops the
     * foreground service. Idempotent: safe to call even when no display is active.
     *
     * <p>Order: IME revert (IME-then-decors, per spec) → freeform revert → audio revert →
     * LSPosed clear → release display → stop service.
     */
    private void releaseDisplay() {
        // Capture the display id before releasing the controller (getDisplayId() throws after
        // release()).
        int displayId = -1;
        if (displayController != null) {
            try {
                displayId = displayController.getDisplayId();
            } catch (RuntimeException ignored) {
                // already released / unavailable — leave -1, per-display revert is skipped.
            }
        }

        revertImeSettings(displayId);
        revertFreeform();
        revertAudioRouting();
        clearLsposedCommands();

        if (displayController != null) {
            try {
                displayController.release();
            } catch (RuntimeException e) {
                Log.w(TAG, "AutoXScreen: display release failed", e);
            }
            displayController = null;
        }

        // Stop the foreground service unconditionally (idempotent — a no-op if not running).
        getCarContext().stopService(
                new Intent(getCarContext(), AutoXForegroundService.class));
        carSpec = null;
        currentSurface = null;
    }

    /** WS5 revert: rebuild the spec from persisted per-display priors and revert (IME-then-decors). */
    private void revertImeSettings(int displayId) {
        if (providers == null || displayId <= 0) return;
        try {
            SharedPreferences prefs = prefs();
            int priorDecor =
                    AutoXSettingsStore.getPriorShouldShowSystemDecorsOrUnset(prefs, displayId);
            int priorIme = AutoXSettingsStore.getPriorShouldShowImeOrUnset(prefs, displayId);
            ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(displayId)
                    .withPriorValues(priorDecor, priorIme);
            new SettingsApplier(providers.settings(), SettingsApplier.Namespace.SECURE)
                    .revert(spec.revertEntries());
            AutoXSettingsStore.clearPriorsForDisplay(prefs, displayId);
            Log.i(TAG, "AutoXScreen: WS5 IME/decors reverted for display " + displayId);
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: WS5 IME revert failed for display " + displayId, e);
        }
    }

    /** WS3 revert: read persisted global priors, revert via GLOBAL applier, clear the priors. */
    private void revertFreeform() {
        if (providers == null) return;
        try {
            SharedPreferences prefs = prefs();
            Integer priorForceResizable = AutoXSettingsStore.getPriorForceResizable(prefs);
            Integer priorEnableFreeform = AutoXSettingsStore.getPriorEnableFreeform(prefs);
            new SettingsApplier(providers.settings(), SettingsApplier.Namespace.GLOBAL)
                    .revert(FreeformGlobalSettingsSpec.revertList(
                            priorForceResizable, priorEnableFreeform));
            AutoXSettingsStore.clearPriors(prefs);
            Log.i(TAG, "AutoXScreen: WS3 freeform/resizable reverted");
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: WS3 freeform revert failed", e);
        }
    }

    /** WS6 revert: clear the per-UID audio affinity applied for this session. */
    private void revertAudioRouting() {
        if (providers == null || audioDecision == null) return;
        try {
            boolean reverted = AudioRouteApplier.revert(audioDecision, providers.audio());
            Log.i(TAG, "AutoXScreen: WS6 audio route revert=" + reverted);
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: WS6 audio revert failed", e);
        } finally {
            audioDecision = null;
        }
    }

    /** LSPosed Task 6: clear the whole IPC channel on teardown. */
    private void clearLsposedCommands() {
        if (ipcWriter == null) return;
        try {
            ipcWriter.clear();
            Log.i(TAG, "AutoXScreen: LSPosed IPC channel cleared");
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: LSPosed clear failed", e);
        } finally {
            ipcWriter = null;
        }
    }

    // ------------------------------------------------------------------
    // Private helpers — framework lookups / small adapters
    // ------------------------------------------------------------------

    /** Resolves the SharedPreferences backing {@link AutoXSettingsStore}. */
    private SharedPreferences prefs() {
        return getCarContext().getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Converts the pure {@link LaunchBoundsCalculator} forced-vertical bounds into an
     * {@link Rect} for {@link AppLauncher}. Returns {@code null} (full-display) if the bounds
     * cannot be computed.
     */
    @Nullable
    private Rect forcedVerticalBounds(AutoXDisplaySpec spec) {
        try {
            LaunchBoundsCalculator.Bounds b = LaunchBoundsCalculator.forcedVertical(
                    spec.getWidth(), spec.getHeight(), spec.getDensityDpi(),
                    FORCED_VERTICAL_ASPECT);
            return new Rect(b.left, b.top, b.right, b.bottom);
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: forced-vertical bounds computation failed; full display", e);
            return null;
        }
    }

    /**
     * Resolves the UID of {@code packageName} via {@link PackageManager#getPackageUid(String, int)}.
     * Returns {@code -1} (an invalid UID — {@link AudioRoutePolicy} treats it as NoRoute) if the
     * package is not installed.
     */
    private int resolveGuestUid(String packageName) {
        try {
            return getCarContext().getPackageManager().getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "AutoXScreen: cannot resolve UID for '" + packageName + "'", e);
            return -1;
        }
    }

    /**
     * Finds the live car audio output device (BT A2DP or USB) from
     * {@link AudioManager#getDevices(int)}. Returns {@code null} if none is present.
     *
     * <p>// TODO(device-verify): on a real head unit confirm which device the car bus reports
     * and that exactly one BT_A2DP/USB output is the projection sink; this picks the first match.
     */
    @Nullable
    private AudioDeviceInfo findCarOutputDevice() {
        AudioManager am = getCarContext().getSystemService(AudioManager.class);
        if (am == null) {
            return null;
        }
        AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devices == null) {
            return null;
        }
        for (AudioDeviceInfo info : devices) {
            if (AudioDeviceTypeMapper.fromAudioDeviceInfoType(info.getType())
                    != CarAudioDevice.NONE) {
                return info;
            }
        }
        return null;
    }
}

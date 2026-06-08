package com.xiddoc.androidautox.autox;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
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
import com.xiddoc.androidautox.autox.provider.InputProvider;
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
 * death — including the WS6 audio routing (the routed UID + device/address are persisted via
 * {@link AutoXSettingsStore.AudioRouteState} so a cold-start teardown can reconstruct the clear).
 * {@code releaseDisplay} reverts everything best-effort (each step independently try/caught) so a
 * partial failure never leaves a setting stranded.
 *
 * <h2>Process-death-safe prior capture</h2>
 * <p>Priors are captured+persisted ONLY on a fresh session, gated by {@link PriorCaptureGate} on
 * the durable {@link AutoXSettingsStore#isEnabled} flag. If {@link #createDisplay} re-runs after a
 * process death (AutoX already enabled), it re-applies the AutoX values WITHOUT re-capturing — a
 * re-read would observe AutoX's own written value and permanently strand the setting on revert.
 * {@link #createDisplay} sets the enabled flag last (after a successful apply) and
 * {@link #releaseDisplay} clears it last (after every revert).
 *
 * <h2>Step ordering</h2>
 * <p>The apply order and the (reverse) revert order are pinned in the pure, unit-tested
 * {@link ProjectionStepPlan} ({@link ProjectionStepPlan#applyOrder()} /
 * {@link ProjectionStepPlan#revertOrder()}); the call sites here mirror it.
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

    private final AppLauncher appLauncher;

    /**
     * Fallback gesture injector used ONLY when the provider bundle is unavailable (the factory
     * probe failed → {@code providers == null}) or when a test injects a custom injector via the
     * DI constructor. In the normal production path gestures route through {@code providers.input()}
     * (a single {@link InputProvider} instance) so {@link InputProvider#isInjectionHonored()}
     * reflects the same injections {@link #reevaluateProviders} consults (MUST-FIX 4). May be null
     * in production, where {@code providers.input()} is the real injector.
     */
    @Nullable
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
        // Production: do NOT construct a separate ReflectiveGestureInjector — gestures route
        // through providers.input() (the single InputProvider the factory builds), so
        // isInjectionHonored() reflects the very injections we make (MUST-FIX 4). The fallback
        // injector is null here; injector() falls back to it only if the provider probe failed.
        this(carContext, new AppLauncher(carContext), null);
    }

    /**
     * Package-private constructor for dependency injection (used in tests and for
     * wiring custom collaborators).
     *
     * @param carContext      the {@link CarContext} provided by the framework
     * @param appLauncher     launcher to place a guest app on the virtual display
     * @param gestureInjector fallback injector used only when {@code providers} is null (provider
     *                        probe failed); pass a recording/no-op stub in tests. May be null.
     */
    AutoXScreen(@NonNull CarContext carContext,
                @NonNull AppLauncher appLauncher,
                @Nullable GestureInjector gestureInjector) {
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
        injectGesture(spec);
    }

    @Override
    public void onScroll(float distanceX, float distanceY) {
        if (displayController == null || carSpec == null) return;
        AutoXDisplaySpec virtSpec = displayController.getSpec();
        GestureSpec spec = TouchRouter.routeScroll(
                carSpec, virtSpec, displayController.getDisplayId(),
                distanceX, distanceY, SWIPE_DURATION_MS);
        injectGesture(spec);
    }

    @Override
    public void onFling(float velocityX, float velocityY) {
        if (displayController == null || carSpec == null) return;
        AutoXDisplaySpec virtSpec = displayController.getSpec();
        GestureSpec spec = TouchRouter.routeFling(
                carSpec, virtSpec, displayController.getDisplayId(),
                velocityX, velocityY, SWIPE_DURATION_MS);
        injectGesture(spec);
    }

    /**
     * Routes {@code spec} through the SAME injector instance the provider bundle uses
     * ({@code providers.input()}) so {@link InputProvider#isInjectionHonored()} observed in
     * {@link #reevaluateProviders} reflects the real injections (MUST-FIX 4). Falls back to the
     * DI-injected {@link #gestureInjector} only when the provider probe failed
     * ({@code providers == null}); a no-op if neither is available.
     */
    private void injectGesture(GestureSpec spec) {
        if (providers != null) {
            providers.input().inject(spec);
        } else if (gestureInjector != null) {
            gestureInjector.inject(spec);
        }
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

        // Process-death-safe prior capture (MUST-FIX): capture+persist the genuine priors ONLY
        // on a fresh session. If AutoX is already enabled (e.g. createDisplay re-runs after a
        // process death), the persisted prior is the genuine original — re-apply without
        // re-capturing it (re-reading now would read back AutoX's own value and strand it).
        SharedPreferences prefs = prefs();
        boolean capturePrior = PriorCaptureGate.shouldCapturePrior(
                AutoXSettingsStore.isEnabled(prefs));

        // The apply order mirrors ProjectionStepPlan.applyOrder() (FREEFORM → IME_DECORS →
        // LSPOSED_DISPLAY_COMMANDS → LAUNCH_APP → AUDIO_ROUTING); the revert in releaseDisplay
        // walks ProjectionStepPlan.revertOrder() (the exact reverse).

        // WS3 (FREEFORM): enable freeform/resizable globals.
        applyFreeform(capturePrior);

        // WS5 (IME_DECORS): per-display IME + system decors.
        applyImeSettings(displayId, capturePrior);

        // LSPosed (LSPOSED_DISPLAY_COMMANDS): id-scoped commands now that the id is known.
        maybeApplyLsposedDisplayCommands(displayId);

        // (LAUNCH_APP) Launch the default app onto the new display, with WS3 forced-vertical bounds.
        AutoXTargetApp defaultApp = AutoXAppRegistry.defaults().get(0);
        Rect bounds = forcedVerticalBounds(spec);
        boolean launched = appLauncher.launch(defaultApp.packageName, displayId, bounds);
        if (!launched) {
            Log.w(TAG, "AutoXScreen: default app '" + defaultApp.packageName
                    + "' could not be launched — app may not be installed on this device");
        }

        // WS6 (AUDIO_ROUTING): route the guest app's audio to the car output device (after
        // launch so the UID is resolvable). Skip when the app did not actually start — there is
        // no live UID to route (SHOULD 8).
        if (launched) {
            applyAudioRouting(defaultApp.packageName);
        }

        // Mark the session enabled so a re-entrant createDisplay (post process-death) gates the
        // prior capture off. Done last so a mid-apply crash leaves enabled=false and the next
        // run still captures the genuine priors.
        AutoXSettingsStore.setEnabled(prefs, true);
    }

    /**
     * LSPosed Task 6: write the trusted-display hook command (no id) before display creation.
     *
     * <p>Provisional-gate note (SHOULD 7): this runs in {@code createDisplay} BEFORE
     * {@link #reevaluateProviders}, so it intentionally keys off the <em>provisional</em>
     * {@code provider()} decision — which is driven by the stable {@code lsposedActive} static
     * probe (LSPosed is either installed/active or not; that does not change at surface time).
     * The two surface-time signals that a reevaluate folds in (trusted-display + injection) are
     * exactly the ones this hook exists to make observable, so gating on the post-reevaluate
     * decision would be circular. Keying off the stable provisional LSPOSED selection is the
     * reliable choice for this pre-create gate.
     */
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
        // MUST-FIX: do NOT assume the trusted flag was honored. AutoXScreen owns its own
        // VirtualDisplayController (the WS4 DisplayProvider seam is unbound) and we have not
        // observed DisplayInfo.flags, so the trusted flag is UNPROVEN. Default to the
        // conservative false — matching the provisional phase's stance — so a device that
        // silently dropped FLAG_TRUSTED is not wrongly flipped off DEGRADED. Asserting true here
        // would overclaim a privileged path that was never observed.
        // TODO(device-verify): read DisplayInfo.flags & Display.FLAG_TRUSTED on the created
        // display (fail-closed: only set true if the bit is actually present) and feed it here.
        boolean trustedHonored = false;
        boolean injectionHonored = providers.input().isInjectionHonored();
        providers = providers.reevaluate(trustedHonored, injectionHonored);
        Log.i(TAG, "AutoXScreen: reevaluated providers = " + providers);
    }

    /**
     * WS3 apply: enable freeform/force-resizable globals via the GLOBAL {@link SettingsApplier}.
     *
     * <p>Process-death safe (MUST-FIX): the device's genuine prior values are captured+persisted
     * ONLY on a fresh session — gated by {@link PriorCaptureGate#shouldCapturePrior(boolean)} on
     * {@link AutoXSettingsStore#isEnabled}. On a re-entrant apply (e.g. {@code createDisplay} runs
     * again after process death) the persisted prior is read and re-used, never overwritten with
     * AutoX's own already-applied value (which would strand the setting on revert).
     *
     * @param capturePrior whether to capture+persist the prior now (fresh session) or read the
     *                     existing persisted prior (re-apply)
     */
    private void applyFreeform(boolean capturePrior) {
        if (providers == null) return;
        try {
            SharedPreferences prefs = prefs();
            Integer priorForceResizable;
            Integer priorEnableFreeform;
            if (capturePrior) {
                priorForceResizable = SettingsPriorMapper.toPrior(providers.settings()
                        .getGlobalInt(FreeformGlobalSettingsSpec.KEY_FORCE_RESIZABLE));
                priorEnableFreeform = SettingsPriorMapper.toPrior(providers.settings()
                        .getGlobalInt(FreeformGlobalSettingsSpec.KEY_ENABLE_FREEFORM));
                AutoXSettingsStore.setPriorForceResizable(prefs, priorForceResizable);
                AutoXSettingsStore.setPriorEnableFreeform(prefs, priorEnableFreeform);
            } else {
                // Re-apply: keep the already-persisted genuine prior; do NOT re-read the live
                // (AutoX-written) value.
                priorForceResizable = AutoXSettingsStore.getPriorForceResizable(prefs);
                priorEnableFreeform = AutoXSettingsStore.getPriorEnableFreeform(prefs);
            }

            new SettingsApplier(providers.settings(), SettingsApplier.Namespace.GLOBAL)
                    .apply(FreeformGlobalSettingsSpec.applyList(
                            priorForceResizable, priorEnableFreeform));
            Log.i(TAG, "AutoXScreen: WS3 freeform/resizable applied (capturePrior="
                    + capturePrior + ")");
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: WS3 freeform apply failed", e);
        }
    }

    /**
     * WS5 apply (Task 3 call-site): build the per-display IME spec and write system-decors then
     * IME via the SECURE {@link SettingsApplier}.
     *
     * <p>Process-death safe (MUST-FIX): the genuine per-display priors are read+persisted ONLY
     * when {@code capturePrior} is true (fresh session). On a re-apply the live read (which would
     * now report AutoX's own values) is skipped and the applied AutoX values are simply re-written.
     *
     * @param displayId    the virtual display id
     * @param capturePrior whether to capture+persist the per-display priors now
     */
    private void applyImeSettings(int displayId, boolean capturePrior) {
        if (providers == null || displayId <= 0) return;
        try {
            ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(displayId);
            if (capturePrior) {
                ImeDisplaySettingsSpec withPriors =
                        new ImeSettingsReader(providers.settings()).readPriors(spec);
                SharedPreferences prefs = prefs();
                AutoXSettingsStore.setPriorShouldShowSystemDecors(prefs, displayId,
                        ImePriorCodec.toBoxedPrior(withPriors.getPriorSystemDecors()));
                AutoXSettingsStore.setPriorShouldShowIme(prefs, displayId,
                        ImePriorCodec.toBoxedPrior(withPriors.getPriorIme()));
            }
            new SettingsApplier(providers.settings(), SettingsApplier.Namespace.SECURE)
                    .apply(spec.applyEntries());
            Log.i(TAG, "AutoXScreen: WS5 IME/decors applied for display " + displayId
                    + " (capturePrior=" + capturePrior + ")");
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
            // MUST-FIX: persist enough to reconstruct the ClearAffinity revert after process
            // death (the transient audioDecision is lost on restart). Only persist when a real
            // route was applied — a NoRoute decision has nothing to clear.
            if (applied) {
                AutoXSettingsStore.setAudioRouteState(prefs(),
                        new AutoXSettingsStore.AudioRouteState(uid, device.name(), address));
            }
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

        // Mark the session disabled LAST, after every revert. This is the durable signal that
        // PriorCaptureGate keys off: a future createDisplay now sees enabled=false and re-captures
        // the genuine (restored) priors for the next session.
        try {
            AutoXSettingsStore.setEnabled(prefs(), false);
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: failed to clear enabled flag on release", e);
        }

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

    /**
     * WS6 revert: clear the per-UID audio affinity applied for this session.
     *
     * <p>Process-death safe (MUST-FIX): if the transient {@link #audioDecision} is gone (process
     * restarted) but persisted audio state exists, the {@code ClearAffinity} revert is
     * reconstructed from the persisted UID/device/address via {@link AudioRoutePolicy#decide}.
     * The persisted state is cleared afterwards so it is not re-reverted on a later teardown.
     */
    private void revertAudioRouting() {
        if (providers == null) return;
        try {
            AudioRoutePolicy.RouteDecision decision = audioDecision;
            if (decision == null) {
                decision = reconstructAudioDecision();
            }
            if (decision == null) {
                return; // nothing was applied / persisted — nothing to revert.
            }
            boolean reverted = AudioRouteApplier.revert(decision, providers.audio());
            AutoXSettingsStore.clearAudioRouteState(prefs());
            Log.i(TAG, "AutoXScreen: WS6 audio route revert=" + reverted);
        } catch (RuntimeException e) {
            Log.w(TAG, "AutoXScreen: WS6 audio revert failed", e);
        } finally {
            audioDecision = null;
        }
    }

    /**
     * Rebuilds the routing decision from persisted {@link AutoXSettingsStore.AudioRouteState}
     * so a cold-start teardown can still clear the affinity. Returns {@code null} when no audio
     * state was persisted (nothing to revert).
     */
    @Nullable
    private AudioRoutePolicy.RouteDecision reconstructAudioDecision() {
        AutoXSettingsStore.AudioRouteState state =
                AutoXSettingsStore.getAudioRouteState(prefs());
        if (state == null) {
            return null;
        }
        CarAudioDevice device;
        try {
            device = CarAudioDevice.valueOf(state.deviceName);
        } catch (IllegalArgumentException e) {
            // Corrupt persisted device name — fall back to a direct clear of the UID.
            device = CarAudioDevice.NONE;
        }
        // decide() reproduces the same ClearAffinity(uid) revert step from the persisted inputs.
        return AudioRoutePolicy.decide(state.uid, device, state.deviceAddress);
    }

    /**
     * LSPosed Task 6: clear the whole IPC channel on teardown.
     *
     * <p>Cold-start safe (SHOULD 6): after a process death the in-memory {@link #ipcWriter} is
     * gone, so a stale LSPosed channel from the prior session would leak. When the (provisional)
     * decision is LSPOSED we therefore construct a fresh {@link IpcCommandWriter} and clear the
     * channel even though {@code ipcWriter == null}. The provisional {@code lsposedActive} probe
     * is stable, so {@code provider()} is a reliable signal here.
     */
    private void clearLsposedCommands() {
        if (ipcWriter == null) {
            // No live writer. If this session is (provisionally) LSPosed, a prior session may
            // have left a stale channel — clear it with a fresh writer.
            if (providers != null
                    && providers.provider() == ProviderSelectionPolicy.Provider.LSPOSED) {
                try {
                    new IpcCommandWriter(getCarContext().getApplicationContext()).clear();
                    Log.i(TAG, "AutoXScreen: stale LSPosed IPC channel cleared (cold start)");
                } catch (RuntimeException e) {
                    Log.w(TAG, "AutoXScreen: cold-start LSPosed clear failed", e);
                }
            }
            return;
        }
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
                    LaunchBoundsCalculator.DEFAULT_FORCED_VERTICAL_ASPECT);
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
        if (devices == null || devices.length == 0) {
            return null;
        }
        // First-match selection lives in the pure CarOutputDeviceSelector (tested); this glue
        // only resolves the int types and reads the address off the chosen device.
        int[] types = new int[devices.length];
        for (int i = 0; i < devices.length; i++) {
            types[i] = devices[i].getType();
        }
        int idx = CarOutputDeviceSelector.firstCarDeviceIndex(types);
        return idx == CarOutputDeviceSelector.NONE_INDEX ? null : devices[idx];
    }
}

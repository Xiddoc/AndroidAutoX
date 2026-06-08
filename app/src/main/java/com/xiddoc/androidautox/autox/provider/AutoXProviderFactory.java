package com.xiddoc.androidautox.autox.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.util.Log;

import com.xiddoc.androidautox.autox.ReflectiveGestureInjector;

import com.topjohnwu.superuser.Shell;

/**
 * EXCLUDED GLUE: selects the privileged-provider set for an AutoX session.
 *
 * <h2>Two-phase API</h2>
 * <p>The two projection-critical capabilities (trusted virtual display honored, cross-display
 * input injection honored) are structurally unobservable at session start — there is no
 * {@link android.view.Surface}, display, or injected event yet. Computing the full decision
 * then would always report {@link ProviderSelectionPolicy.Provider#DEGRADED} even on a
 * capable root device, which is misleading. The factory therefore exposes two phases:
 *
 * <ol>
 *   <li>{@link #probe(Context)} — runs the cheap <em>static</em> probes only
 *       (LSPosed-active, platform-signed, root-available), feeds trusted-display /
 *       input-injection as conservatively {@code false}, and returns a <b>provisional</b>
 *       {@link AutoXProviders} ({@link AutoXProviders#isProvisional()} {@code true}) with an
 *       {@link UnboundDisplayProvider} placeholder.</li>
 *   <li>{@link AutoXProviders#reevaluate(boolean, boolean)} — the Wave-2 call site
 *       ({@code AutoXScreen.onSurfaceAvailable}) invokes this once the real display exists and
 *       the trusted / injection state is observable; it purely recomputes the decision via
 *       {@link CapabilityDecider} + {@link ProviderSelectionPolicy}.</li>
 * </ol>
 *
 * <p>{@link #create(Context)} is a thin convenience alias for {@link #probe(Context)}.
 *
 * <p>All probe→capability→decision logic lives in the pure, fully-tested
 * {@link CapabilityDecider} / {@link ProviderSelectionPolicy} / {@link AutoXProviders}; this
 * class only collects live booleans and assembles instances.
 *
 * <h2>Threading</h2>
 * <p>Root detection uses {@link Shell#getCachedShell()} (non-blocking — root is already
 * acquired in the splash flow), so {@link #probe(Context)} does not block on shell
 * acquisition and is safe to call from the main thread.
 *
 * <h2>Display provider caveat</h2>
 * <p>A {@code RootDisplayProvider} needs a {@link android.view.Surface}, available only once
 * the Car App SDK delivers a {@code SurfaceContainer} — strictly after this factory runs. So
 * the factory returns a lightweight {@link UnboundDisplayProvider} placeholder (reporting
 * {@link DisplayProvider#NO_DISPLAY} / not-trusted). {@code AutoXScreen} continues to own its
 * {@code VirtualDisplayController} for now; the WS4 DisplayProvider-seam migration is future
 * work.
 *
 * <p>Excluded from the JaCoCo coverage gate: every method here touches live Android framework
 * services ({@link Context}, {@link ContentResolver}, {@link InputManager},
 * {@link AudioManager}, {@link PackageManager}) and libsu {@link Shell}, none exercisable in
 * the JVM unit-test environment.
 */
public final class AutoXProviderFactory {

    private static final String TAG = "AndroidAutoX";

    /**
     * System property / marker the LSPosed module would expose; checked reflectively. The
     * module itself runs in {@code system_server}, so app-side detection is best-effort.
     */
    private static final String XPOSED_BRIDGE_CLASS = "de.robv.android.xposed.XposedBridge";

    private AutoXProviderFactory() {
    }

    /**
     * Probes the cheap static capabilities and returns a <b>provisional</b>
     * {@link AutoXProviders}. The decision is computed with trusted-display and
     * input-injection conservatively {@code false} (no surface exists yet); the Wave-2 call
     * site must call {@link AutoXProviders#reevaluate(boolean, boolean)} once the surface
     * arrives to obtain the final, non-provisional decision.
     *
     * @param context a non-null application / service context
     * @return the provisional provider set + selection decision
     * @throws IllegalArgumentException if {@code context} is null
     */
    public static AutoXProviders probe(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        ContentResolver resolver = context.getContentResolver();
        InputManager inputManager = context.getSystemService(InputManager.class);
        AudioManager audioManager = context.getSystemService(AudioManager.class);

        // Concrete (best-effort) provider impls. For LSPOSED and ROOT_REFLECTION these are
        // the same app-side impls — the LSPosed hooks relax the system_server checks, the
        // app still calls the framework APIs through these. For DEGRADED they are still
        // returned (best-effort) but the decision flags the degradation.
        // TODO(task-6): select LSPosed-backed impls when decision.provider == LSPOSED. The
        // bundle's seam types (SystemSettingsProvider / InputProvider) are exactly what the
        // future LSPosed-backed impls will implement, so this is a drop-in swap point.
        SystemSettingsProvider settings = new RootSystemSettingsProvider(resolver);
        InputProvider input = new ReflectiveGestureInjector(inputManager);
        AudioRouter audio = new RootAudioRouter(context, audioManager);
        DisplayProvider display = new UnboundDisplayProvider();

        // --- cheap static probes (booleans only; meaning is decided by the pure decider) ---
        boolean lsposedActive = probeLsposedActive();
        boolean platformSigned = probePlatformSignature(context);
        boolean rootAvailable = probeRootAvailable();

        // No Surface yet at session start: the trusted-display and injection capabilities are
        // structurally unobservable. Feed them conservatively false; the Wave-2 call site
        // re-runs the decision via AutoXProviders.reevaluate(...) once the surface exists.
        boolean trustedDisplayHonored = false;
        boolean injectionHonored = false;

        // settingsWritable is NOT determined by a live privileged write — that round-trip
        // would be a side-effecting, observer-firing write that (a) can false-negative on
        // privileged devices when writing back the same value and (b) is never even read by
        // ProviderSelectionPolicy.select(...) today. Derive it conservatively from the static
        // privileged-path signals instead, with no write to any user-facing setting.
        boolean settingsWritable = rootAvailable || platformSigned || lsposedActive;

        // Pure mapping: probe booleans -> capabilities (CapabilityDecider) -> decision
        // (ProviderSelectionPolicy). NOTHING is decided in this excluded class.
        ProviderCapabilities caps = CapabilityDecider.decide(
                lsposedActive,
                platformSigned,
                rootAvailable,
                trustedDisplayHonored,
                injectionHonored,
                settingsWritable);
        ProviderSelectionPolicy.Decision decision = ProviderSelectionPolicy.select(caps);

        Log.i(TAG, "AutoX provisional provider selection: " + decision + " from " + caps);
        return new AutoXProviders(settings, input, display, audio, decision,
                lsposedActive, platformSigned, rootAvailable, settingsWritable,
                /* provisional= */ true);
    }

    /**
     * Thin convenience alias for {@link #probe(Context)}. Returns a provisional bundle; the
     * caller must {@link AutoXProviders#reevaluate(boolean, boolean)} after the surface
     * arrives.
     *
     * @param context a non-null application / service context
     * @return the provisional provider set + selection decision
     * @throws IllegalArgumentException if {@code context} is null
     */
    public static AutoXProviders create(Context context) {
        return probe(context);
    }

    // ------------------------------------------------------------------
    // Live probes (best-effort, never throw)
    // ------------------------------------------------------------------

    /**
     * Best-effort detection of whether the LSPosed module is active. The module runs in
     * {@code system_server}, so the app cannot observe its hooks directly; we check whether
     * the Xposed bridge class is even loadable in this process (LSPosed injects it into
     * hooked processes). Returns {@code false} on any failure.
     */
    private static boolean probeLsposedActive() {
        try {
            Class.forName(XPOSED_BRIDGE_CLASS, false,
                    AutoXProviderFactory.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Best-effort detection of whether the app is signed with the <em>platform</em>
     * signature, i.e. shares the system's signing certificate. Uses
     * {@link PackageManager#checkSignatures(String, String)} against the {@code "android"}
     * package: {@link PackageManager#SIGNATURE_MATCH} means our signing cert equals the
     * platform cert. Fail-closed ({@code false}) on any exception.
     *
     * <p>Note: this is signature parity, distinct from holding {@code INJECT_EVENTS} (an
     * injection capability that root can also confer) — the two must not be conflated.
     *
     * <p>// TODO(device-verify): confirm on a real device that checkSignatures("android", pkg)
     * returns SIGNATURE_MATCH for a genuinely platform-signed build and not for a root-only
     * install. If this cannot be verified off-device, the conservative path is that
     * platformSigned stays false and selection relies on rootAvailable.
     */
    private static boolean probePlatformSignature(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.checkSignatures("android", context.getPackageName())
                    == PackageManager.SIGNATURE_MATCH;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Best-effort detection of a usable root path via libsu, <em>without blocking</em>. Uses
     * {@link Shell#getCachedShell()} (the splash flow already acquired root), so this never
     * spawns/awaits {@code su} on the calling thread. A null cached shell is treated as
     * not-root. Never throws.
     */
    private static boolean probeRootAvailable() {
        try {
            Shell cached = Shell.getCachedShell();
            return cached != null && cached.isRoot();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Placeholder {@link DisplayProvider} returned by the factory at session start, before
     * any {@link android.view.Surface} exists. Reports {@link DisplayProvider#NO_DISPLAY}
     * (the typed unbound signal) and an untrusted state; all mutating calls are no-ops. The
     * real {@code RootDisplayProvider} is built later by the call-site wiring once the
     * surface is delivered (future WS4 work).
     */
    static final class UnboundDisplayProvider implements DisplayProvider {
        @Override
        public int create(com.xiddoc.androidautox.autox.AutoXDisplaySpec spec) {
            return NO_DISPLAY;
        }

        @Override
        public boolean resize(int width, int height, int densityDpi) {
            return false;
        }

        @Override
        public void release() {
            // no-op: nothing to release
        }

        @Override
        public int getDisplayId() {
            return NO_DISPLAY;
        }

        @Override
        public boolean isTrustedDisplayHonored() {
            return false;
        }
    }
}

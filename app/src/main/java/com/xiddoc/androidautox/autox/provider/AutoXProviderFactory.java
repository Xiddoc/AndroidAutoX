package com.xiddoc.androidautox.autox.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.Log;

import com.xiddoc.androidautox.autox.ReflectiveGestureInjector;

import com.topjohnwu.superuser.Shell;

/**
 * EXCLUDED GLUE: selects the privileged-provider set for an AutoX session at session
 * start.
 *
 * <p>{@link #create(Context)} is the entry point. It:
 * <ol>
 *   <li><b>Probes live capabilities</b> using the existing excluded glue and the system
 *       services obtained from the {@link Context}: whether a root path exists (libsu
 *       {@link Shell}), whether the app is platform-signed, whether the LSPosed module is
 *       active in {@code system_server}, whether a protected-settings write round-trips
 *       (write-probe via {@link RootSystemSettingsProvider}), and the input / display
 *       providers' honored-state probes
 *       ({@link ReflectiveGestureInjector#isInjectionHonored()},
 *       {@code RootDisplayProvider#isTrustedDisplayHonored()}).</li>
 *   <li><b>Assembles those probe booleans into a {@link ProviderCapabilities}</b> via the
 *       pure {@link CapabilityDecider} — <em>no</em> probe→caps→decision mapping lives in
 *       this class; all of it is in pure, 100%-tested code.</li>
 *   <li><b>Selects the provider set</b> via the pure {@link ProviderSelectionPolicy}.</li>
 *   <li>Returns an immutable {@link AutoXProviders} bundling the chosen providers plus the
 *       {@link ProviderSelectionPolicy.Decision}.</li>
 * </ol>
 *
 * <h2>Display provider caveat</h2>
 * <p>A {@code RootDisplayProvider} needs a {@link android.view.Surface}, which is only
 * available once the Car App SDK delivers a {@code SurfaceContainer} — strictly after this
 * factory runs at session start. So {@link #create(Context)} cannot construct the real
 * display provider or run the trusted-display write-probe itself. It therefore returns a
 * lightweight {@link UnboundDisplayProvider} placeholder (reporting {@code NO_DISPLAY} /
 * not-trusted) and feeds the trusted-display capability as {@code false} into the decider;
 * the call-site wiring (a later task, in {@code AutoXScreen.onSurfaceAvailable}) will build
 * the real {@code RootDisplayProvider} with the live surface and may re-evaluate the
 * decision once the surface exists.
 *
 * <p>Excluded from the JaCoCo coverage gate: every method here touches live Android
 * framework services ({@link Context}, {@link ContentResolver}, {@link InputManager},
 * {@link AudioManager}) and libsu {@link Shell}, none of which are exercisable in the JVM
 * unit-test environment. All probe→capability→decision logic lives in the pure,
 * fully-tested {@link CapabilityDecider} / {@link ProviderSelectionPolicy}; the holder is
 * the pure {@link AutoXProviders}.
 *
 * <p>// TODO(device-verify): confirm on a real rooted / LSPosed device the platform-signed
 * detection, the LSPosed self-hook marker, the settings write-probe round-trip, and that
 * the input / display honored-probes report correctly after the first real injection /
 * display creation.
 */
public final class AutoXProviderFactory {

    private static final String TAG = "AndroidAutoX";

    /**
     * Benign {@code Settings.Global} key written and read back to detect whether protected
     * settings writes succeed. Chosen because it is an integer key already present on every
     * device, so the write-probe restores its own prior value and leaves no residue.
     */
    private static final String SETTINGS_PROBE_KEY = Settings.Global.AUTO_TIME;

    /**
     * System property / marker the LSPosed module would expose; checked reflectively. The
     * module itself runs in {@code system_server}, so app-side detection is best-effort.
     */
    private static final String XPOSED_BRIDGE_CLASS = "de.robv.android.xposed.XposedBridge";

    private AutoXProviderFactory() {
    }

    /**
     * Probes the live system and returns the selected {@link AutoXProviders}.
     *
     * @param context a non-null application / service context
     * @return the chosen provider set + selection decision
     * @throws IllegalArgumentException if {@code context} is null
     */
    public static AutoXProviders create(Context context) {
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
        SystemSettingsProvider settings = new RootSystemSettingsProvider(resolver);
        InputProvider input = new ReflectiveGestureInjector(inputManager);
        AudioRouter audio = new RootAudioRouter(context, audioManager);
        DisplayProvider display = new UnboundDisplayProvider();

        // --- live probes (booleans only; meaning is decided by the pure decider) ---
        boolean lsposedActive = probeLsposedActive();
        boolean platformSigned = probePlatformSignature(context);
        boolean rootAvailable = probeRootAvailable();
        // No Surface yet at session start — the real trusted-display probe runs once a
        // surface is delivered (later call-site wiring). Conservatively false here.
        boolean trustedDisplayHonored = display.isTrustedDisplayHonored();
        // No gesture has been injected yet, so the injector reports "unproven" (false)
        // until the first real injection. Conservatively reflects that here.
        boolean injectionHonored = input.isInjectionHonored();
        boolean settingsWritable = probeSettingsWritable(settings);

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

        Log.i(TAG, "AutoX provider selection: " + decision + " from " + caps);
        return new AutoXProviders(settings, input, display, audio, decision);
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
     * Best-effort detection of whether the app is signed with the platform signature, i.e.
     * shares a UID / signature with the system. Approximated by checking whether the app
     * holds a signature-level permission that only platform-signed apps are granted.
     * Returns {@code false} on any failure.
     */
    private static boolean probePlatformSignature(Context context) {
        try {
            // INJECT_EVENTS is signature|privileged; a third-party app is never granted it
            // unless it shares the platform signature. PackageManager.checkPermission returns
            // PERMISSION_GRANTED (0) only when actually held.
            int granted = context.getPackageManager().checkPermission(
                    "android.permission.INJECT_EVENTS", context.getPackageName());
            return granted == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Best-effort detection of a usable root path via libsu. Returns {@code true} only if a
     * shell with real root status is available. Never throws.
     */
    private static boolean probeRootAvailable() {
        try {
            return Shell.getShell().isRoot();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Write-probe: reads the current value of {@link #SETTINGS_PROBE_KEY}, writes it back,
     * and reports whether the round-trip succeeded (write {@code OK}). Restores nothing
     * destructively — it writes back the value it just read. Returns {@code false} if the
     * key is unreadable or the write is denied.
     */
    private static boolean probeSettingsWritable(SystemSettingsProvider settings) {
        SettingsResult read = settings.getGlobalInt(SETTINGS_PROBE_KEY);
        if (!read.isOk()) {
            return false;
        }
        SettingsResult write = settings.putGlobalInt(SETTINGS_PROBE_KEY, read.value);
        return write.isOk();
    }

    /**
     * Placeholder {@link DisplayProvider} returned by the factory at session start, before
     * any {@link android.view.Surface} exists. Reports no display and an untrusted state;
     * all mutating calls are no-ops. The real {@code RootDisplayProvider} is built later by
     * the call-site wiring once the surface is delivered.
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

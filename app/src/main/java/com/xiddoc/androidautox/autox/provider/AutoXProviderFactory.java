package com.xiddoc.androidautox.autox.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.util.Log;

import com.xiddoc.androidautox.autox.GestureSpec;
import com.xiddoc.androidautox.autox.provider.lsposed.LsposedInputInjector;

import com.topjohnwu.superuser.Shell;

/**
 * EXCLUDED GLUE: selects the privileged-provider set for an AutoX session.
 *
 * <h2>LSPosed-first single path</h2>
 * <p>AutoX requires root (baseline) AND LSPosed. The trusted-display flag and cross-display
 * input injection go through LSPosed exclusively (there is no stable root-only path), so the
 * {@link InputProvider} is the LSPosed-backed {@link LsposedInputInjector} when LSPosed is
 * active and a no-op {@link BlockedInputProvider} otherwise (AutoX is BLOCKED then, so no
 * injector is needed). Settings writes stay on root ({@link RootSystemSettingsProvider}); audio
 * routing stays {@link RootAudioRouter}.
 *
 * <h2>Two-phase API</h2>
 * <p>The two projection-critical capabilities (trusted virtual display honored, cross-display
 * input injection honored) are structurally unobservable at session start — there is no
 * {@link android.view.Surface}, display, or injected event yet. The factory therefore exposes
 * two phases:
 *
 * <ol>
 *   <li>{@link #probe(Context)} — runs the cheap <em>static</em> probes only
 *       (LSPosed-active, platform-signed, root-available), feeds trusted-display /
 *       input-injection OPTIMISTICALLY equal to LSPosed-active (trusted until a device read
 *       proves otherwise), and returns a <b>provisional</b>
 *       {@link AutoXProviders} ({@link AutoXProviders#isProvisional()} {@code true}) with an
 *       {@link UnboundDisplayProvider} placeholder. With LSPosed active this is provisionally
 *       {@link ProviderSelectionPolicy.Provider#LSPOSED}; without it,
 *       {@link ProviderSelectionPolicy.Provider#BLOCKED}.</li>
 *   <li>{@link AutoXProviders#reevaluate(boolean, boolean)} — the Wave-2 call site
 *       ({@code AutoXScreen.onSurfaceAvailable}) invokes this once the real display exists and
 *       the trusted / injection state is observable; it purely recomputes the decision via
 *       {@link CapabilityDecider} + {@link ProviderSelectionPolicy}. If a hook is ineffective
 *       the decision flips to {@link ProviderSelectionPolicy.Provider#BLOCKED}.</li>
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
 * <p>The display needs a {@link android.view.Surface}, available only once the Car App SDK
 * delivers a {@code SurfaceContainer} — strictly after this factory runs. So the factory
 * returns a lightweight {@link UnboundDisplayProvider} placeholder (reporting
 * {@link DisplayProvider#NO_DISPLAY} / not-trusted). {@code AutoXScreen} owns its
 * {@code VirtualDisplayController}; the WS4 DisplayProvider-seam migration is future work.
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
     * input-injection fed OPTIMISTICALLY equal to LSPosed-active (no surface exists yet, so we
     * trust the LSPosed hooks until a device read proves otherwise); the Wave-2 call site must
     * call {@link AutoXProviders#reevaluate(boolean, boolean)} once the surface arrives to obtain
     * the final, non-provisional decision.
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

        // --- cheap static probes (booleans only; meaning is decided by the pure decider) ---
        boolean lsposedActive = probeLsposedActive();
        boolean platformSigned = probePlatformSignature(context);
        boolean rootAvailable = probeRootAvailable();

        // LSPosed-first single path: settings stay on root (the clean, stable answer) and audio
        // stays on root; the InputProvider is the LSPosed-backed injector ONLY when LSPosed is
        // active (the system_server hook relaxes the per-display ownership check). When LSPosed
        // is inactive AutoX is BLOCKED, so a no-op injector is wired in — there is no root
        // injection fallback for AutoX (the trusted-display + injection checks have no stable
        // root path).
        SystemSettingsProvider settings = new RootSystemSettingsProvider(resolver);
        InputProvider input = lsposedActive
                ? new LsposedInputInjector(inputManager)
                : new BlockedInputProvider();
        AudioRouter audio = new RootAudioRouter(context, audioManager);
        DisplayProvider display = new UnboundDisplayProvider();

        // No Surface yet at session start: the trusted-display and injection capabilities are
        // structurally unobservable. When LSPosed is active we feed them OPTIMISTICALLY true:
        // LSPosed is the privileged mechanism, so we trust the hook is effective until a real
        // device read proves otherwise — this makes the provisional decision resolve to LSPOSED
        // (the intent) instead of BLOCKED. When LSPosed is inactive the values are irrelevant
        // (selection blocks on lsposedModuleActive first). The Wave-2 call site re-runs the
        // decision via AutoXProviders.reevaluate(...) once the surface exists.
        // TODO(device-verify): replace optimistic true with the real trusted-flag
        // (DisplayInfo.flags & FLAG_TRUSTED) and post-injection reads.
        boolean trustedDisplayHonored = lsposedActive;
        boolean injectionHonored = lsposedActive;

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
     * No-op {@link InputProvider} wired in when LSPosed is inactive. AutoX is BLOCKED in that
     * case (no silent root-injection fallback), so this never injects and reports the injection
     * hook as not honored, keeping the selection decision at
     * {@link ProviderSelectionPolicy.Provider#BLOCKED}.
     */
    static final class BlockedInputProvider implements InputProvider {
        @Override
        public boolean inject(GestureSpec spec) {
            return false;
        }

        @Override
        public boolean isInjectionHonored() {
            return false;
        }
    }

    /**
     * Placeholder {@link DisplayProvider} returned by the factory at session start, before
     * any {@link android.view.Surface} exists. Reports {@link DisplayProvider#NO_DISPLAY}
     * (the typed unbound signal) and an untrusted state; all mutating calls are no-ops.
     * {@code AutoXScreen} owns the real {@code VirtualDisplayController}; the DisplayProvider-seam
     * migration that would bind a real display here is future WS4 work.
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

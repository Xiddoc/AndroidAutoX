package com.xiddoc.androidautox.autox.provider;

/**
 * Immutable holder bundling the concrete privileged-provider set chosen for an AutoX
 * session, together with the {@link ProviderSelectionPolicy.Decision} that produced it.
 *
 * <p>Produced by the excluded-glue {@code AutoXProviderFactory}: the factory runs the cheap
 * static probes (LSPosed-active, platform-signed, root-available), runs the pure
 * {@link CapabilityDecider} + {@link ProviderSelectionPolicy}, and packages the resolved
 * {@link SystemSettingsProvider} / {@link InputProvider} / {@link DisplayProvider} /
 * {@link AudioRouter} here so call sites depend only on the seam, not on how privilege is
 * obtained.
 *
 * <h2>Two-phase decision (provisional → reevaluated)</h2>
 * <p>At session start, before the Car App SDK delivers a {@code Surface}, the two
 * projection-critical capabilities — whether the trusted virtual-display flag is honored
 * and whether cross-display input injection is honored — are <em>structurally</em>
 * unobservable (there is no display or injected event yet). The factory therefore returns a
 * <b>provisional</b> bundle (see {@link #isProvisional()}) whose decision was computed with
 * those two inputs fed OPTIMISTICALLY equal to {@code lsposedModuleActive}; with LSPosed active
 * that provisionally reports {@link ProviderSelectionPolicy.Provider#LSPOSED} (LSPosed is active
 * and trusted until a device read proves otherwise), and
 * {@link ProviderSelectionPolicy.Provider#BLOCKED} when LSPosed is inactive.
 *
 * <p>Once the surface exists (Wave-2 call site, {@code AutoXScreen.onSurfaceAvailable}) the
 * caller observes the real trusted-display / injection state and calls
 * {@link #reevaluate(boolean, boolean)} to obtain an updated, non-provisional bundle whose
 * decision was recomputed from the same static probes plus the now-known booleans. The
 * recompute is <b>pure</b>: it only re-runs {@link CapabilityDecider} +
 * {@link ProviderSelectionPolicy}; the provider instances carry over unchanged.
 *
 * <h2>What the providers are, per decision</h2>
 * <ul>
 *   <li><b>LSPOSED</b> — the LSPosed hooks relax the {@code system_server} trusted-display and
 *       input-injection checks; the app injects via the LSPosed-backed {@code LsposedInputInjector}
 *       ({@link InputProvider}), and settings writes still go through
 *       {@link RootSystemSettingsProvider} (root is the clean, stable path for those). Audio
 *       routing stays {@link RootAudioRouter}.</li>
 *   <li><b>BLOCKED</b> — LSPosed is inactive, or a hook is ineffective. AutoX does NOT silently
 *       degrade: {@link #isBlocked()} is {@code true} and the decision reason explains what is
 *       missing so the caller can block enabling AutoX with a clear "requires LSPosed" message.</li>
 * </ul>
 *
 * <h2>Display seam caveat</h2>
 * <p>{@code AutoXScreen} continues to own its {@code VirtualDisplayController} for now; the
 * WS4 DisplayProvider-seam migration is future work. The factory's {@link #display()} is
 * provisional/unbound at session start — an {@code UnboundDisplayProvider} placeholder whose
 * {@link DisplayProvider#getDisplayId()} returns {@link DisplayProvider#NO_DISPLAY}, which is
 * the typed signal that it is not a real display. {@link #reevaluate(boolean, boolean)} does
 * not change the display instance; it only refreshes the decision.
 *
 * <p>Framework-free value object (no Android imports): all fields are final, the
 * constructor only validates non-null and assigns, the predicate getters delegate to the
 * pure {@link ProviderSelectionPolicy.Decision}, and {@link #reevaluate(boolean, boolean)}
 * is pure. It is therefore in scope for the 100% coverage gate (not excluded) and fully
 * unit tested.
 */
public final class AutoXProviders {

    private final SystemSettingsProvider settings;
    private final InputProvider input;
    private final DisplayProvider display;
    private final AudioRouter audio;
    private final ProviderSelectionPolicy.Decision decision;

    /** Static-probe inputs retained so {@link #reevaluate(boolean, boolean)} can recompute. */
    private final boolean lsposedActive;
    private final boolean platformSigned;
    private final boolean rootAvailable;
    private final boolean settingsWritable;

    /** True until {@link #reevaluate(boolean, boolean)} folds in the real surface-time probes. */
    private final boolean provisional;

    /**
     * Full constructor capturing the static-probe inputs alongside the resolved providers and
     * decision so the bundle can later {@link #reevaluate(boolean, boolean)} purely.
     *
     * @param settings         the chosen settings provider; must not be null
     * @param input            the chosen input provider; must not be null
     * @param display          the chosen display provider; must not be null
     * @param audio            the chosen audio router; must not be null
     * @param decision         the selection decision (provider + reason); must not be null
     * @param lsposedActive    whether the LSPosed module was detected active
     * @param platformSigned   whether the app is platform-signed
     * @param rootAvailable    whether a root path was detected
     * @param settingsWritable the (conservatively derived) protected-settings-writable input
     * @param provisional      {@code true} if the decision is provisional (pre-surface)
     * @throws IllegalArgumentException if any provider/decision argument is null
     */
    public AutoXProviders(SystemSettingsProvider settings,
                          InputProvider input,
                          DisplayProvider display,
                          AudioRouter audio,
                          ProviderSelectionPolicy.Decision decision,
                          boolean lsposedActive,
                          boolean platformSigned,
                          boolean rootAvailable,
                          boolean settingsWritable,
                          boolean provisional) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("display must not be null");
        }
        if (audio == null) {
            throw new IllegalArgumentException("audio must not be null");
        }
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        this.settings = settings;
        this.input = input;
        this.display = display;
        this.audio = audio;
        this.decision = decision;
        this.lsposedActive = lsposedActive;
        this.platformSigned = platformSigned;
        this.rootAvailable = rootAvailable;
        this.settingsWritable = settingsWritable;
        this.provisional = provisional;
    }

    /** @return the chosen {@link SystemSettingsProvider}. Never null. */
    public SystemSettingsProvider settings() {
        return settings;
    }

    /** @return the chosen {@link InputProvider}. Never null. */
    public InputProvider input() {
        return input;
    }

    /**
     * @return the chosen {@link DisplayProvider}. Never null. While {@link #isProvisional()}
     *         this is an unbound placeholder whose {@link DisplayProvider#getDisplayId()} is
     *         {@link DisplayProvider#NO_DISPLAY} — callers must not treat it as a real display.
     */
    public DisplayProvider display() {
        return display;
    }

    /** @return the chosen {@link AudioRouter}. Never null. */
    public AudioRouter audio() {
        return audio;
    }

    /** @return the {@link ProviderSelectionPolicy.Decision} behind this set. Never null. */
    public ProviderSelectionPolicy.Decision decision() {
        return decision;
    }

    /**
     * The canonical accessor callers should steer toward when branching on capability.
     *
     * @return the selected {@link ProviderSelectionPolicy.Provider}. Never null.
     */
    public ProviderSelectionPolicy.Provider provider() {
        return decision.provider;
    }

    /** @return the human-readable reason the providers were selected. Never null. */
    public String reason() {
        return decision.reason;
    }

    /**
     * @return {@code true} if this bundle's decision is provisional — computed from the
     *         cheap static probes only, with trusted-display and input-injection fed
     *         optimistically equal to LSPosed-active because no surface existed yet (trusted
     *         until a device read proves otherwise). Provisional until
     *         {@link #reevaluate(boolean, boolean)} is called after the surface arrives.
     */
    public boolean isProvisional() {
        return provisional;
    }

    /**
     * Recomputes the decision now that the surface-time capabilities are observable, and
     * returns an updated, non-provisional bundle. Pure: it re-runs {@link CapabilityDecider}
     * + {@link ProviderSelectionPolicy} with the retained static probes plus the supplied
     * booleans; the provider instances (including the display placeholder) carry over
     * unchanged — this method does not bind a real display (that remains future WS4 work).
     *
     * @param trustedDisplayHonored whether the created virtual display actually carries the
     *                              trusted flag (observed from the real display)
     * @param injectionHonored      whether cross-display input injection is actually honored
     *                              (observed from a real injection)
     * @return a new {@link AutoXProviders} with the recomputed decision and
     *         {@link #isProvisional()} {@code false}
     */
    public AutoXProviders reevaluate(boolean trustedDisplayHonored, boolean injectionHonored) {
        ProviderCapabilities caps = CapabilityDecider.decide(
                lsposedActive,
                platformSigned,
                rootAvailable,
                trustedDisplayHonored,
                injectionHonored,
                settingsWritable);
        ProviderSelectionPolicy.Decision recomputed = ProviderSelectionPolicy.select(caps);
        return new AutoXProviders(settings, input, display, audio, recomputed,
                lsposedActive, platformSigned, rootAvailable, settingsWritable, false);
    }

    /**
     * @return {@code true} if the selection is {@link ProviderSelectionPolicy.Provider#BLOCKED}
     *         — AutoX cannot run (no LSPosed / hooks ineffective) and the caller must block
     *         enabling it with a clear "requires LSPosed" message (no silent degrade).
     */
    public boolean isBlocked() {
        return decision.provider == ProviderSelectionPolicy.Provider.BLOCKED;
    }

    /**
     * @return {@code true} if the selection routes through the LSPosed module
     *         ({@link ProviderSelectionPolicy.Provider#LSPOSED}).
     */
    public boolean usesLsposed() {
        return decision.provider == ProviderSelectionPolicy.Provider.LSPOSED;
    }

    @Override
    public String toString() {
        return "AutoXProviders{decision=" + decision + ", provisional=" + provisional + '}';
    }
}

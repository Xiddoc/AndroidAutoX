package com.xiddoc.androidautox.autox.provider;

/**
 * Immutable holder bundling the concrete privileged-provider set chosen for an AutoX
 * session, together with the {@link ProviderSelectionPolicy.Decision} that produced it.
 *
 * <p>Produced by the excluded-glue {@code AutoXProviderFactory} at session start: the
 * factory probes the live system, runs the pure {@link CapabilityDecider} +
 * {@link ProviderSelectionPolicy}, and packages the resolved
 * {@link SystemSettingsProvider} / {@link InputProvider} / {@link DisplayProvider} /
 * {@link AudioRouter} here so call sites depend only on the seam, not on how privilege is
 * obtained.
 *
 * <h2>What the providers are, per decision</h2>
 * <ul>
 *   <li><b>LSPOSED</b> — the same root-reflection app-side impls (the LSPosed hooks relax
 *       the {@code system_server} checks; the app still calls the framework APIs through
 *       {@link RootSystemSettingsProvider} / {@code RootDisplayProvider} /
 *       {@code ReflectiveGestureInjector} / {@link RootAudioRouter}), with the decision
 *       reporting that the privileged path is the LSPosed module.</li>
 *   <li><b>ROOT_REFLECTION</b> — the {@code Root*}/{@code Reflective*} impls driving the
 *       {@code @hide} APIs directly from a root / platform-signed process.</li>
 *   <li><b>DEGRADED</b> — best-effort {@code Root*} impls are still returned so a caller
 *       can attempt projection, but {@link #isDegraded()} is {@code true} and the decision
 *       reason explains why projection may not work, so callers can warn the user.</li>
 * </ul>
 *
 * <p>Framework-free value object (no Android imports): all fields are final, the
 * constructor only validates non-null and assigns, and the few predicate getters delegate
 * to the pure {@link ProviderSelectionPolicy.Decision}. It is therefore in scope for the
 * 100% coverage gate (not excluded) and fully unit tested.
 */
public final class AutoXProviders {

    private final SystemSettingsProvider settings;
    private final InputProvider input;
    private final DisplayProvider display;
    private final AudioRouter audio;
    private final ProviderSelectionPolicy.Decision decision;

    /**
     * @param settings the chosen settings provider; must not be null
     * @param input    the chosen input provider; must not be null
     * @param display  the chosen display provider; must not be null
     * @param audio    the chosen audio router; must not be null
     * @param decision the selection decision (provider + reason); must not be null
     * @throws IllegalArgumentException if any argument is null
     */
    public AutoXProviders(SystemSettingsProvider settings,
                          InputProvider input,
                          DisplayProvider display,
                          AudioRouter audio,
                          ProviderSelectionPolicy.Decision decision) {
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
    }

    /** @return the chosen {@link SystemSettingsProvider}. Never null. */
    public SystemSettingsProvider settings() {
        return settings;
    }

    /** @return the chosen {@link InputProvider}. Never null. */
    public InputProvider input() {
        return input;
    }

    /** @return the chosen {@link DisplayProvider}. Never null. */
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

    /** @return the selected {@link ProviderSelectionPolicy.Provider}. Never null. */
    public ProviderSelectionPolicy.Provider provider() {
        return decision.provider;
    }

    /** @return the human-readable reason the providers were selected. Never null. */
    public String reason() {
        return decision.reason;
    }

    /**
     * @return {@code true} if the selection is {@link ProviderSelectionPolicy.Provider#DEGRADED}
     *         — projection may not work reliably and the caller should warn the user.
     */
    public boolean isDegraded() {
        return decision.provider == ProviderSelectionPolicy.Provider.DEGRADED;
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
        return "AutoXProviders{decision=" + decision + '}';
    }
}

package com.xiddoc.androidautox.autox;

/**
 * Pure decision logic that determines whether the AutoX virtual-display projection
 * feature can be activated.
 *
 * <p>All methods are {@code static} and have no side-effects — they only compute and return
 * values. No Android framework objects are referenced here, so every method can be exercised
 * in a plain JUnit test on the JVM without Robolectric, keeping it fully visible to JaCoCo.
 *
 * <h3>Inputs</h3>
 * <ul>
 *   <li><b>enabled</b> — the user has toggled AutoX on via {@link AutoXSettingsStore}.</li>
 *   <li><b>targetChosen</b> — a target package has been persisted (non-null, non-empty).</li>
 *   <li><b>providerAvailable</b> — the Android Auto host is available and can receive a
 *       {@code CarAppService} connection (e.g. AA is installed and its version is
 *       compatible). In the current implementation this is always {@code true} on a device
 *       that has Android Auto installed; it is surfaced as an explicit input so policy tests
 *       can cover the unavailable branch without needing a PackageManager.</li>
 * </ul>
 *
 * <h3>Decision</h3>
 * All three inputs must be {@code true} for projection to be allowed.
 */
public final class AutoXEnablementPolicy {

    private AutoXEnablementPolicy() {
    }

    /**
     * Reason code returned inside a {@link Decision} when projection is blocked.
     *
     * <p>Reason ordering reflects the priority in which checks are performed: the first
     * failing check is reported so the user receives the most actionable message.
     */
    public enum Reason {
        /**
         * All conditions are satisfied — projection can proceed.
         * Only set when {@link Decision#canProject} is {@code true}.
         */
        OK,

        /**
         * The AutoX feature has not been enabled by the user.
         * Actionable fix: toggle the AutoX button in the tweak list.
         */
        NOT_ENABLED,

        /**
         * No target package has been chosen yet.
         * Actionable fix: open the app picker and select a target app.
         */
        NO_TARGET_CHOSEN,

        /**
         * The Android Auto provider (Gearhead) is not available on this device.
         * Actionable fix: install or update Android Auto.
         */
        PROVIDER_UNAVAILABLE,
    }

    /**
     * Immutable result of a {@link AutoXEnablementPolicy#evaluate} call.
     *
     * <p>Always carries both {@link #canProject} and a {@link #reason} so callers
     * can display a precise message to the user.
     */
    public static final class Decision {

        /** {@code true} iff AutoX is allowed to begin projecting. */
        public final boolean canProject;

        /**
         * Human-readable reason code. {@link Reason#OK} when {@link #canProject} is
         * {@code true}; one of the blocking reasons otherwise.
         */
        public final Reason reason;

        private Decision(boolean canProject, Reason reason) {
            this.canProject = canProject;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return "Decision{canProject=" + canProject + ", reason=" + reason + "}";
        }
    }

    /**
     * Evaluates all conditions and returns an immutable {@link Decision}.
     *
     * <p>Checks are performed in order:
     * <ol>
     *   <li>Feature enabled?</li>
     *   <li>Target package chosen?</li>
     *   <li>Provider available?</li>
     * </ol>
     * The first failing check determines the returned {@link Reason}.
     *
     * @param enabled           whether the user has enabled AutoX.
     * @param targetChosen      whether a target package name has been persisted.
     * @param providerAvailable whether the Android Auto host is available on the device.
     * @return a {@link Decision} describing whether projection is allowed and why not if
     *         it is blocked.
     */
    public static Decision evaluate(boolean enabled,
                                    boolean targetChosen,
                                    boolean providerAvailable) {
        if (!enabled) {
            return new Decision(false, Reason.NOT_ENABLED);
        }
        if (!targetChosen) {
            return new Decision(false, Reason.NO_TARGET_CHOSEN);
        }
        if (!providerAvailable) {
            return new Decision(false, Reason.PROVIDER_UNAVAILABLE);
        }
        return new Decision(true, Reason.OK);
    }
}

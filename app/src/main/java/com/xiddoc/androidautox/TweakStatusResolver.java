package com.xiddoc.androidautox;

/**
 * Pure, Android-free decision logic that maps three inputs to the {@link TweakStatus}
 * that should be displayed for a tweak.
 *
 * <h3>Inputs</h3>
 * <ul>
 *   <li>{@code enabled} — the stored "user switched this tweak on" preference.</li>
 *   <li>{@code rebootPending} — stored marker meaning "applied but awaiting a reboot /
 *       confirmation before we can verify it in the DB".</li>
 *   <li>{@code appliedInDb} — tri-state ground-truth from the phenotype DB:
 *       {@link Boolean#TRUE} = confirmed applied, {@link Boolean#FALSE} = confirmed NOT
 *       applied, {@code null} = unknown (e.g. root not yet granted or DB not checked).</li>
 * </ul>
 *
 * <h3>Precedence rules</h3>
 * <ol>
 *   <li><b>Reality wins.</b> {@code appliedInDb == TRUE} → {@link TweakStatus#APPLIED}.
 *       A DB-confirmed applied state is green regardless of any pending-reboot flag.</li>
 *   <li><b>Waiting on reboot.</b> {@code enabled && rebootPending} →
 *       {@link TweakStatus#REBOOT_PENDING}. The user applied the tweak; the DB hasn't
 *       confirmed it yet (or was not checked); the yellow state is preserved.</li>
 *   <li><b>Optimistic / no-root fallback.</b> {@code enabled && appliedInDb == null} →
 *       {@link TweakStatus#APPLIED}. When we simply couldn't check the DB (no root,
 *       service not bound, etc.) we fall back to the boolean preference and show green,
 *       matching today's behaviour so we never regress to red just because we couldn't
 *       verify.</li>
 *   <li><b>Everything else</b> → {@link TweakStatus#DISABLED}. Not enabled, or enabled
 *       but the DB confirms the tweak is gone and no reboot is pending (drift / failure).</li>
 * </ol>
 *
 * <p>This class has no Android dependencies and no mutable state. All logic is in the
 * single static method {@link #resolve(boolean, boolean, Boolean)} so it can be exercised
 * with plain JUnit without any Robolectric scaffolding.
 */
public final class TweakStatusResolver {

    private TweakStatusResolver() {}

    /**
     * Resolves the display status for a tweak.
     *
     * @param enabled      whether the user preference marks this tweak as enabled
     * @param rebootPending whether the "applied but awaiting reboot/confirmation" marker
     *                      is set in persistent storage
     * @param appliedInDb  tri-state DB check result: {@code TRUE} = confirmed applied,
     *                     {@code FALSE} = confirmed not applied, {@code null} = unknown
     * @return the {@link TweakStatus} that should be shown in the UI
     */
    public static TweakStatus resolve(boolean enabled, boolean rebootPending,
            Boolean appliedInDb) {
        // Rule 1: DB ground-truth confirmed → always green.
        if (Boolean.TRUE.equals(appliedInDb)) {
            return TweakStatus.APPLIED;
        }

        // Rule 2: User applied it; not yet confirmed in DB; waiting on reboot → yellow.
        if (enabled && rebootPending) {
            return TweakStatus.REBOOT_PENDING;
        }

        // Rule 3: Enabled and DB state is unknown (null) → optimistic green fallback.
        if (enabled && appliedInDb == null) {
            return TweakStatus.APPLIED;
        }

        // Rule 4: Disabled, or enabled but DB confirmed gone with no pending reboot.
        return TweakStatus.DISABLED;
    }
}

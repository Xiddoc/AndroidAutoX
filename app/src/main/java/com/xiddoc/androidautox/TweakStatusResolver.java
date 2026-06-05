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
 * <h3>Precedence rules (ORDER IS LOAD-BEARING — do not reorder)</h3>
 * <ol>
 *   <li><b>Not enabled.</b> {@code !enabled} → {@link TweakStatus#DISABLED} immediately.
 *       Nothing else matters if the user hasn't switched the tweak on.</li>
 *   <li><b>Waiting on reboot.</b> {@code rebootPending} → {@link TweakStatus#REBOOT_PENDING}.
 *       A tweak that was just written to the DB has {@code rebootPending == true}; we must
 *       show yellow until the device reboots and the marker is cleared. This rule MUST
 *       precede the {@code appliedInDb == TRUE} check: immediately after writing flags
 *       the DB <em>is</em> TRUE, but the consuming process hasn't restarted yet — showing
 *       green at that point would be premature and misleading.</li>
 *   <li><b>DB confirms applied.</b> {@code Boolean.TRUE.equals(appliedInDb)} →
 *       {@link TweakStatus#APPLIED}. We only reach here when no reboot is pending, so a
 *       TRUE result genuinely means the tweak is live.</li>
 *   <li><b>Optimistic / no-root fallback.</b> {@code appliedInDb == null} →
 *       {@link TweakStatus#APPLIED}. We reach here (null not consumed by rule 3) only
 *       because rule 3 already handled TRUE. When the DB couldn't be checked (no root,
 *       service not bound, decode error) we trust the stored intent and show green,
 *       preserving legacy behaviour and preventing false-red regressions.</li>
 *   <li><b>Confirmed gone.</b> {@code appliedInDb == FALSE} → {@link TweakStatus#DISABLED}.
 *       Flags are confirmed absent — tweak drifted or failed to apply.</li>
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
        // Rule 1: tweak is not enabled by the user — always red, check nothing else.
        if (!enabled) {
            return TweakStatus.DISABLED;
        }

        // Rule 2: reboot pending takes priority over the DB state (LOAD-BEARING ORDER).
        // Right after writing flags to the DB, appliedInDb will be TRUE — but the consuming
        // process hasn't restarted yet, so we must stay yellow until the reboot clears this
        // marker.  This rule MUST come before the appliedInDb == TRUE check.
        if (rebootPending) {
            return TweakStatus.REBOOT_PENDING;
        }

        // Rule 3: DB confirms the flags are live.  We only arrive here when rebootPending is
        // false, so TRUE genuinely means the tweak is applied and running.
        if (Boolean.TRUE.equals(appliedInDb)) {
            return TweakStatus.APPLIED;
        }

        // Rule 4: DB was not readable (null — could not determine).  Note: TRUE was already
        // consumed by rule 3 above, so null is the only remaining non-FALSE value here.
        // Fall back to "optimistic green": trust the stored intent rather than regressing to
        // red just because root/DB wasn't available at query time.
        if (appliedInDb == null) {
            return TweakStatus.APPLIED;
        }

        // Rule 5: appliedInDb == FALSE — flags confirmed absent (drift or failed apply).
        return TweakStatus.DISABLED;
    }
}

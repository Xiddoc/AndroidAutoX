package com.xiddoc.androidautox.autox;

/**
 * Pure decision: should the current apply pass <em>capture and persist</em> the prior
 * (pre-AutoX) values, or skip the capture and merely re-apply the AutoX values?
 *
 * <h2>Why this matters (process-death correctness)</h2>
 * <p>AutoX captures the device's original setting values (e.g. {@code force_resizable_activities},
 * the per-display IME/decors flags) at apply time and persists them so the revert in
 * {@code releaseDisplay} can restore the genuine originals even across a process death.
 *
 * <p>The hazard: after a process-death restart the framework re-delivers the surface and
 * {@code AutoXScreen.createDisplay} runs <em>again</em>. If apply re-captured the prior
 * unconditionally, it would now read back AutoX's <em>own</em> written value (e.g.
 * {@code force_resizable=1}) and persist <em>that</em> as the "prior", permanently stranding the
 * privileged setting on revert (revert would "restore" 1, never the true original 0/absent).
 *
 * <p>The fix is to capture priors only on the <em>first</em> apply of a session — i.e. only when
 * AutoX is not already marked enabled. The persisted {@code autox_enabled} flag (see
 * {@link AutoXSettingsStore#isEnabled}) is the durable session marker: it survives process death,
 * so a re-entrant {@code createDisplay} sees {@code enabled == true} and skips the (now poisonous)
 * re-capture, while a genuine fresh session sees {@code enabled == false} and captures the real
 * originals.
 *
 * <p>Framework-free (no Android imports) and fully unit-tested.
 */
public final class PriorCaptureGate {

    private PriorCaptureGate() {
        // Static utility class; prevent instantiation.
    }

    /**
     * Decides whether the current apply pass should capture+persist the prior values.
     *
     * @param alreadyEnabled whether AutoX is already marked enabled (a session is in progress,
     *                       possibly resumed after process death) — typically
     *                       {@link AutoXSettingsStore#isEnabled(android.content.SharedPreferences)}
     * @return {@code true} when priors should be captured and persisted (fresh session, no
     *         AutoX value has been written yet); {@code false} when AutoX is already enabled
     *         and the existing persisted prior must be preserved (re-apply only)
     */
    public static boolean shouldCapturePrior(boolean alreadyEnabled) {
        return !alreadyEnabled;
    }
}

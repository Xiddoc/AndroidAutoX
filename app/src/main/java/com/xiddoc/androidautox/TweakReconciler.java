package com.xiddoc.androidautox;

/**
 * Pure-ish reconciliation logic that decides the {@link TweakStatus} to display for a
 * single tweak by combining the stored per-tweak state ({@link TweakStateStore}) with the
 * ground-truth DB check result ({@code appliedInDb}).
 *
 * <p>Beyond merely resolving a status, this class performs one targeted self-heal:
 * if the phenotype DB confirms a tweak's flags are applied ({@code appliedInDb ==
 * Boolean.TRUE}) but the stored "enabled" boolean was lost (e.g. it was never written, or
 * an old build cleared it), we restore {@code enabled = true}. The flags are genuinely live,
 * so the user must have enabled the tweak and the boolean simply drifted — healing it keeps
 * the legacy {@code load(key)}-based callers (re-apply job, button labels) consistent.
 *
 * <h3>Healing safety (LOAD-BEARING)</h3>
 * Healing happens <em>only</em> when {@code appliedInDb == Boolean.TRUE}. It must never fire
 * on {@code null} (UNKNOWN — no root / unreadable DB) or {@link Boolean#FALSE} (confirmed
 * absent). A genuinely reverted tweak restores the baselines and therefore reads FALSE, so
 * never healing on non-TRUE prevents resurrecting a tweak the user turned off.
 *
 * <p>No Android UI dependencies: takes a {@link TweakStateStore} (itself a thin prefs wrapper)
 * so the logic can be exercised with a fake/in-memory store under plain JUnit.
 */
public final class TweakReconciler {

    private TweakReconciler() {}

    /**
     * Reconciles stored state against the DB truth for a single tweak.
     *
     * @param key         the tweak key (e.g. {@code "aa_six_tap"})
     * @param appliedInDb tri-state DB check: {@link Boolean#TRUE} = confirmed applied,
     *                    {@link Boolean#FALSE} = confirmed absent, {@code null} = unknown
     * @param store       persistent per-tweak state; may be mutated (enabled healed) only
     *                    when {@code appliedInDb == Boolean.TRUE}
     * @return the {@link TweakStatus} that should be shown for {@code key}
     */
    public static TweakStatus reconcile(String key, Boolean appliedInDb, TweakStateStore store) {
        boolean enabled = store.isEnabled(key);

        // Heal a lost enabled-boolean: the flags are confirmed live in the DB, so the user
        // really enabled this tweak. Only ever heal on confirmed-TRUE — never on null/UNKNOWN
        // or FALSE, otherwise a reverted/absent tweak could be resurrected.
        if (Boolean.TRUE.equals(appliedInDb) && !enabled) {
            store.setEnabled(key, true);
            enabled = true;
        }

        return TweakStatusResolver.resolve(enabled, store.isRebootPending(key), appliedInDb);
    }
}

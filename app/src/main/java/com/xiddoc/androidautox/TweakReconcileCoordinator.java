package com.xiddoc.androidautox;

import androidx.annotation.WorkerThread;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure, Android-UI-free orchestration of the post-root tweak-status reconcile pass.
 *
 * <p>Extracted out of {@code MainActivity} so the per-key loop, the {@link TweakReconciler}
 * call, the heal side-effect and (critically) the enabled/disabled label selection can all be
 * exercised under plain JUnit with fakes, instead of living inside an anonymous {@code Thread}
 * that needs the root-heavy Activity to run.
 *
 * <p>For each registered key the coordinator:
 * <ol>
 *   <li>reads the DB-truth tri-state via the injected {@link Checker} ({@code key -> Boolean});</li>
 *   <li>feeds it to {@link TweakReconciler#reconcile} (which also heals a lost enabled-boolean
 *       in the {@link TweakStateStore} when the DB confirms the flags are live);</li>
 *   <li>resolves the label to paint (DISABLED uses {@link Target#disabledLabel}; APPLIED /
 *       REBOOT_PENDING keep {@link Target#enabledLabel} — the tweak is on either way), and</li>
 *   <li>invokes the {@link Sink} exactly once with the key, the resolved {@link TweakStatus}
 *       and that label (which may be {@code null} for icon-only targets).</li>
 * </ol>
 *
 * <p>{@code MainActivity} keeps only the Android-specific edges: building the {@link Target}s
 * from its views, supplying a {@link Checker}/{@link TweakStateStore}, and a {@link Sink} that
 * marshals the paint onto the UI thread (with its destroyed-activity guard).
 */
public final class TweakReconcileCoordinator {

    /**
     * One reconcile target: the labels to choose between for the status. Deliberately holds NO
     * Android views — the UI views live on the {@code MainActivity} side and are keyed back from
     * the {@link Sink} callback. Icon-only targets (dynamic-value tweaks) pass {@code null} for
     * both labels so the sink only repaints the icon.
     */
    public static final class Target {
        final String enabledLabel;   // label to show when applied / reboot-pending
        final String disabledLabel;  // label to show when disabled

        public Target(String enabledLabel, String disabledLabel) {
            this.enabledLabel = enabledLabel;
            this.disabledLabel = disabledLabel;
        }
    }

    /** Reads DB-truth for one tweak key. Mirrors {@link TweakAppliedChecker#appliedState}. */
    public interface Checker {
        /**
         * @return {@link Boolean#TRUE} = confirmed applied, {@link Boolean#FALSE} = confirmed
         *         absent, {@code null} = UNKNOWN. Must not throw.
         */
        Boolean appliedState(String key);
    }

    /** Receives the resolved status (and label) for a single key, once. */
    public interface Sink {
        /**
         * @param key         the tweak key
         * @param status      the reconciled status to paint
         * @param labelOrNull the button label to set, or {@code null} for icon-only targets
         */
        void paint(String key, TweakStatus status, String labelOrNull);
    }

    private final LinkedHashMap<String, Target> targets;
    private final Checker checker;
    private final TweakStateStore store;

    /**
     * @param targets key → label data, in iteration order; must not be null
     * @param checker DB-truth probe; must not be null
     * @param store   per-tweak state, mutated only to heal a lost enabled-boolean; must not be null
     */
    public TweakReconcileCoordinator(LinkedHashMap<String, Target> targets,
                                     Checker checker, TweakStateStore store) {
        this.targets = Objects.requireNonNull(targets, "targets");
        this.checker = Objects.requireNonNull(checker, "checker");
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Runs the reconcile for every target, invoking {@code sink} once per key. Performs blocking
     * root IPC through the {@link Checker}, so callers must run this off the main thread.
     *
     * @param sink receives each key's resolved status + label; must not be null
     */
    @WorkerThread
    public void run(Sink sink) {
        Objects.requireNonNull(sink, "sink");
        for (Map.Entry<String, Target> e : targets.entrySet()) {
            String key = e.getKey();
            Target target = e.getValue();
            Boolean appliedInDb = checker.appliedState(key);
            TweakStatus status = TweakReconciler.reconcile(key, appliedInDb, store);
            // DISABLED uses the disabled label; APPLIED / REBOOT_PENDING keep the enabled label
            // (the tweak is on either way). Icon-only targets carry null labels.
            String label = (status == TweakStatus.DISABLED)
                    ? target.disabledLabel : target.enabledLabel;
            sink.paint(key, status, label);
        }
    }

    /** Package-private accessor used by the invariant test that every flag-mapped tweak is registered. */
    java.util.Set<String> keys() {
        return targets.keySet();
    }
}

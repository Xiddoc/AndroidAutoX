package com.xiddoc.androidautox;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pure, Android-free planner for the diff-based "patch apps" apply.
 *
 * <p>The "patch apps" flow is no longer an all-or-nothing global toggle: the user maintains a
 * whitelist of apps (the <em>desired</em> set) and a single Apply reconciles it against the set
 * of apps whose installer we have already re-stamped (the <em>applied</em> set). This class turns
 * those two sets into the two disjoint per-app actions Apply must perform:
 *
 * <ul>
 *   <li><b>enable</b> = {@code desired \ applied} — newly whitelisted apps to set-installer; and</li>
 *   <li><b>disable</b> = {@code applied \ desired} — apps removed from the whitelist whose original
 *       installer must be restored.</li>
 * </ul>
 *
 * <p>Because the two actions are computed as a set difference they are always <b>disjoint</b>:
 * enabling one app and disabling a different app in the same Apply cannot collide (that is the
 * "opposite actions" safety the caller relies on). An app present in both inputs (already applied
 * and still desired) appears in neither action — it is a no-op that the caller leaves untouched.
 */
public final class PatchAppsPlan {

    private PatchAppsPlan() {
    }

    /** The two disjoint per-app action sets a single Apply must perform. */
    public static final class Diff {
        /** Apps to newly patch (desired but not yet applied). Insertion order preserved. */
        public final Set<String> toEnable;
        /** Apps to un-patch / restore (applied but no longer desired). Insertion order preserved. */
        public final Set<String> toDisable;

        Diff(Set<String> toEnable, Set<String> toDisable) {
            this.toEnable = toEnable;
            this.toDisable = toDisable;
        }
    }

    /** What the Select-apps screen's Apply FAB should do, given the current state. */
    public enum FabAction {
        /** Nothing selected and nothing applied: the FAB is hidden (no action to offer). */
        HIDDEN,
        /** The selection differs from what's applied: tapping applies the pending changes. */
        APPLY,
        /** Everything selected is already applied (no pending changes): tapping reverts all. */
        REVERT_ALL
    }

    /**
     * Decides the Apply FAB's action from the desired whitelist and the already-applied set.
     * Pending changes (a non-empty {@link #computeDiff} in either direction) mean {@link
     * FabAction#APPLY}; otherwise a non-empty applied set means everything is applied and the
     * FAB offers {@link FabAction#REVERT_ALL}; with nothing selected and nothing applied there is
     * nothing to do, so {@link FabAction#HIDDEN}. Both arguments are treated as empty when null.
     */
    public static FabAction decideFab(Set<String> desired, Set<String> applied) {
        Diff diff = computeDiff(desired, applied);
        if (!diff.toEnable.isEmpty() || !diff.toDisable.isEmpty()) {
            return FabAction.APPLY;
        }
        boolean anyApplied = applied != null && !applied.isEmpty();
        return anyApplied ? FabAction.REVERT_ALL : FabAction.HIDDEN;
    }

    /**
     * Computes the enable/disable diff between the desired whitelist and the already-applied set.
     * Both arguments are treated as empty when {@code null}; the returned sets are independent
     * copies (mutating the inputs afterwards does not affect them) and preserve iteration order.
     *
     * @param desired packages the user currently wants patched (the whitelist)
     * @param applied packages whose installer has already been re-stamped
     * @return a {@link Diff} whose {@code toEnable} and {@code toDisable} are disjoint
     */
    public static Diff computeDiff(Set<String> desired, Set<String> applied) {
        Set<String> d = desired != null ? desired : java.util.Collections.<String>emptySet();
        Set<String> a = applied != null ? applied : java.util.Collections.<String>emptySet();

        Set<String> toEnable = new LinkedHashSet<>(d);
        toEnable.removeAll(a);

        Set<String> toDisable = new LinkedHashSet<>(a);
        toDisable.removeAll(d);

        return new Diff(toEnable, toDisable);
    }
}

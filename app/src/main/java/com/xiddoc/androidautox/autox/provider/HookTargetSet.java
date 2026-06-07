package com.xiddoc.androidautox.autox.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The set of {@link HookDescriptor}s the LSPosed module should install for one SDK
 * level, plus the {@code resolved} flag distinguishing a known SDK from the
 * "no descriptor" fallback returned for unknown SDKs.
 *
 * <p>Pure (no Android imports). Indexed by {@link HookDescriptor.Target} so the glue can
 * ask for a single target without scanning a list.
 */
public final class HookTargetSet {

    /** The SDK_INT this set was resolved for. */
    public final int sdkInt;
    /** True for a known SDK; false for the unknown-SDK fallback (empty descriptor map). */
    public final boolean resolved;

    private final Map<HookDescriptor.Target, HookDescriptor> byTarget;

    HookTargetSet(int sdkInt, boolean resolved, List<HookDescriptor> descriptors) {
        this.sdkInt = sdkInt;
        this.resolved = resolved;
        Map<HookDescriptor.Target, HookDescriptor> map =
                new LinkedHashMap<HookDescriptor.Target, HookDescriptor>();
        for (HookDescriptor d : descriptors) {
            map.put(d.target, d);
        }
        this.byTarget = Collections.unmodifiableMap(map);
    }

    /** Builds the "unknown SDK — no descriptors" fallback for {@code sdkInt}. */
    static HookTargetSet unresolved(int sdkInt) {
        return new HookTargetSet(sdkInt, false, Collections.<HookDescriptor>emptyList());
    }

    /**
     * @param target the logical hook role
     * @return the descriptor for {@code target}, or {@code null} if this set has none
     *         (always {@code null} for the unresolved fallback)
     */
    public HookDescriptor get(HookDescriptor.Target target) {
        return byTarget.get(target);
    }

    /**
     * @return an unmodifiable, insertion-ordered view of all descriptors in this set
     *         (empty for the unresolved fallback)
     */
    public Map<HookDescriptor.Target, HookDescriptor> all() {
        return byTarget;
    }

    /** @return {@code true} iff this set has a descriptor for every {@link HookDescriptor.Target}. */
    public boolean isComplete() {
        return byTarget.size() == HookDescriptor.Target.values().length;
    }

    @Override
    public String toString() {
        return "HookTargetSet{sdkInt=" + sdkInt + ", resolved=" + resolved
                + ", targets=" + byTarget.keySet() + '}';
    }
}

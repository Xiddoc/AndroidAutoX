package com.xiddoc.androidautox.autox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Pure formatter for a one-shot AutoX environment / runtime snapshot, logged at key lifecycle
 * points (session start, post-create, teardown) so a single {@link AutoXLog} dump carries the
 * full picture of <em>what the device looked like</em> when AutoX ran — not just the step trace.
 *
 * <p>When a tester reports "it crashed", the first questions are always: which SDK? was the
 * provider decision actually {@code LSPOSED} (or did it silently fall through to BLOCKED)? what
 * display id / geometry did the host hand us? did any injection get honored yet? This block
 * answers all of those at a glance. It also renders an explicit {@link #verdict} line so the
 * common "not actually LSPosed" misconfiguration is obvious without reading the whole trace.
 *
 * <p>No Android imports — every input is a primitive captured by the (excluded) glue and passed
 * in, so this class is fully unit-tested and NOT excluded from the coverage gate.
 */
public final class AutoXDiagnostics {

    private AutoXDiagnostics() {
    }

    /**
     * Formats a multi-line environment snapshot.
     *
     * @param phase            a short lifecycle label (e.g. {@code "createDisplay"},
     *                         {@code "releaseDisplay"}) so multiple snapshots in one dump are
     *                         distinguishable
     * @param sdkInt           {@code Build.VERSION.SDK_INT} of the device
     * @param providerDecision the resolved provider-selection decision name (e.g. {@code "LSPOSED"}
     *                         / {@code "BLOCKED"}); {@code null} renders as {@code "null"} (the
     *                         provider probe failed — itself a useful signal)
     * @param displayId        the AutoX virtual display id, or a negative sentinel when none exists
     * @param width            virtual-display width in px
     * @param height           virtual-display height in px
     * @param dpi              virtual-display density in dpi
     * @param injectionHonored whether a real cross-display injection has been observed accepted yet
     * @return a newline-terminated, human-readable diagnostics block
     */
    @NonNull
    public static String report(@NonNull String phase,
                                int sdkInt,
                                @Nullable String providerDecision,
                                int displayId,
                                int width,
                                int height,
                                int dpi,
                                boolean injectionHonored) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== AutoX diagnostics [").append(phase).append("] ====\n");
        sb.append("  sdkInt           = ").append(sdkInt).append('\n');
        sb.append("  providerDecision = ").append(providerDecision).append('\n');
        sb.append("  displayId        = ").append(displayId).append('\n');
        sb.append("  geometry         = ")
                .append(String.format(Locale.US, "%dx%d @ %ddpi", width, height, dpi)).append('\n');
        sb.append("  injectionHonored = ").append(injectionHonored).append('\n');
        sb.append("  verdict          = ").append(verdict(providerDecision)).append('\n');
        return sb.toString();
    }

    /**
     * One-line human verdict keyed off the provider decision: AutoX only runs on the single
     * {@code LSPOSED} path, so anything else is a clean block (the common misconfiguration is
     * "LSPosed module not enabled in the manager", which surfaces here as {@code BLOCKED}).
     */
    @NonNull
    static String verdict(@Nullable String providerDecision) {
        if ("LSPOSED".equals(providerDecision)) {
            return "OK — LSPosed path active";
        }
        return "BLOCKED — AutoX requires the LSPosed module (decision=" + providerDecision + ")";
    }
}

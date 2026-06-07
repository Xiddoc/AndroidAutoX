package com.xiddoc.androidautox.autox.provider.lsposed;

/**
 * Pure (no Android imports) decision logic that decides whether an LSPosed hook body should
 * <em>act</em> for the in-flight {@code system_server} call.
 *
 * <h2>Why this exists</h2>
 * <p>The original WS4 hooks forced trusted-display / {@code shouldShowSystemDecors} /
 * {@code shouldShowIme} / launch-on-display behaviour <b>system-wide</b>: any virtual
 * display, any display id, would be affected. That is an over-reach — it changes platform
 * behaviour for displays that have nothing to do with AutoX. This policy narrows each hook
 * so it only acts for <b>AutoX's own display</b>, identified by the display name AutoX uses
 * when it creates its virtual display ({@code VirtualDisplayConfig.DISPLAY_NAME}) and/or the
 * display id AutoX is currently driving.
 *
 * <h2>Inputs are primitives only</h2>
 * <p>The bridges/module extract the relevant primitives from the live framework objects (the
 * hooked display's name, the target display id, whether the matching IPC command is enabled)
 * and pass them here. Keeping the decision in primitives makes it 100% unit-testable while
 * the (untestable) reflective extraction stays in the excluded glue.
 *
 * <h2>Fail-closed</h2>
 * <p>Every method defaults to {@code false} (do NOT act) when inputs are missing or
 * ambiguous, so a hook that cannot positively confirm it is operating on AutoX's display
 * leaves the platform behaviour untouched.
 */
public final class HookGatePolicy {

    /** Sentinel for "no display id known" — never a valid Android display id. */
    public static final int NO_DISPLAY_ID = -1;

    private HookGatePolicy() {
        // Static utility class; prevent instantiation.
    }

    /**
     * Decides whether a hook keyed on a <b>display name</b> should act.
     *
     * <p>Used by the trusted-display hook: the {@code createVirtualDisplay} frame carries the
     * caller-supplied display name, which the bridge compares against AutoX's expected name.
     * The hook acts only when the matching IPC command is enabled <em>and</em> the names match
     * exactly.
     *
     * @param ipcEnabled         whether the matching IPC command is currently enabled by the app
     * @param hookedDisplayName  the display name observed in the live call frame (may be null)
     * @param expectedDisplayName AutoX's own display name (may be null/blank → never matches)
     * @return {@code true} iff the hook should act for this call
     */
    public static boolean shouldActForDisplayName(boolean ipcEnabled,
                                                  String hookedDisplayName,
                                                  String expectedDisplayName) {
        if (!ipcEnabled) {
            return false;
        }
        if (expectedDisplayName == null || expectedDisplayName.trim().isEmpty()) {
            return false;
        }
        return expectedDisplayName.equals(hookedDisplayName);
    }

    /**
     * Decides whether a hook keyed on a <b>display id</b> should act.
     *
     * <p>Used by the per-display gate hooks ({@code shouldShowIme},
     * {@code shouldShowSystemDecors}, launch-on-display) and the input-injection hook: the
     * frame carries the target display id, which the bridge compares against the display id
     * AutoX is currently driving. The hook acts only when the matching IPC command is enabled,
     * both ids are valid ({@code >= 0}), and they are equal.
     *
     * @param ipcEnabled        whether the matching IPC command is currently enabled by the app
     * @param hookedDisplayId   the display id observed in the live call frame
     * @param autoxDisplayId    the display id AutoX is currently driving
     *                          ({@link #NO_DISPLAY_ID} when AutoX has no active display)
     * @return {@code true} iff the hook should act for this call
     */
    public static boolean shouldActForDisplayId(boolean ipcEnabled,
                                                int hookedDisplayId,
                                                int autoxDisplayId) {
        if (!ipcEnabled) {
            return false;
        }
        if (autoxDisplayId < 0 || hookedDisplayId < 0) {
            return false;
        }
        return hookedDisplayId == autoxDisplayId;
    }
}

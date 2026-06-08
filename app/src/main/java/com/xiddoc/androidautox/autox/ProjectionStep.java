package com.xiddoc.androidautox.autox;

/**
 * The ordered privileged steps that make up bringing an AutoX projection session up (and
 * tearing it down). Used by {@link ProjectionStepPlan} to express — in a pure, testable form —
 * the apply order and the (reverse) revert order that {@code AutoXScreen} iterates against,
 * instead of leaving the ordering implicit in excluded glue + Javadoc.
 *
 * <p>Framework-free; the enum carries no behaviour, only identity/order.
 */
public enum ProjectionStep {

    /** LSPosed trusted-display hook, written before the virtual display is created. */
    LSPOSED_TRUSTED_DISPLAY,

    /** Create the virtual display (the {@code VirtualDisplayController}). */
    CREATE_DISPLAY,

    /** WS3: enable freeform / force-resizable global flags. */
    FREEFORM,

    /** WS5: per-display IME + system-decors. */
    IME_DECORS,

    /** LSPosed id-scoped commands (input injection / IME-decors / launch on display). */
    LSPOSED_DISPLAY_COMMANDS,

    /** Launch the guest app onto the display. */
    LAUNCH_APP,

    /** WS6: per-UID audio routing to the car output device. */
    AUDIO_ROUTING
}

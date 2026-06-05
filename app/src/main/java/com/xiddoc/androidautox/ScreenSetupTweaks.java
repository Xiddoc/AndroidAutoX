package com.xiddoc.androidautox;

/**
 * Pure naming logic for the SCREEN SETUP tweaks. The screen-layout handler in
 * {@link MainActivity} maps a portrait-breakpoint value to the name of the SQLite
 * trigger (and matching saved-preference key) that represents the chosen layout —
 * {@code force_ws} / {@code force_no_ws} / {@code force_portrait}. That name is fed
 * straight into the {@code CREATE TRIGGER <name> ...} SQL the handler builds, so it
 * belongs next to the trigger logic rather than with the {@code su} shell builders.
 *
 * <p>This is pure string logic (no device access), so it is unit-testable without a
 * root shell or the Activity.
 */
public final class ScreenSetupTweaks {

    private ScreenSetupTweaks() {
    }

    /**
     * Map a portrait-breakpoint value to the trigger / preference name of the
     * screen-layout tweak it represents. Mirrors the inline {@code switch (value)}
     * in the screen-setup handler; returns {@code ""} for unknown values (the
     * inline default left {@code decideWhat} empty).
     */
    public static String breakpointTweakName(int breakpointValue) {
        switch (breakpointValue) {
            case 470:
                return "force_ws";
            case 1920:
                return "force_no_ws";
            case 10:
                return "force_portrait";
            default:
                return "";
        }
    }
}

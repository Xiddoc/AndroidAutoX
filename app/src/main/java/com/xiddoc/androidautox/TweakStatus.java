package com.xiddoc.androidautox;

/**
 * Canonical tri-state status of a tweak, matching the int codes that
 * {@code MainActivity.changeStatus(ImageView, int, boolean)} already uses:
 * <ul>
 *   <li>{@link #DISABLED} = 0 → red icon</li>
 *   <li>{@link #REBOOT_PENDING} = 1 → yellow icon</li>
 *   <li>{@link #APPLIED} = 2 → green icon</li>
 * </ul>
 *
 * <p>Using an enum with an explicit {@link #code()} accessor keeps the semantics
 * self-documenting while still producing the exact {@code int} that the existing
 * UI path expects.
 */
public enum TweakStatus {

    /** Tweak is not applied (red). Maps to {@code changeStatus(view, 0, …)}. */
    DISABLED(0),

    /** Tweak was applied but a reboot / confirmation is still needed (yellow).
     *  Maps to {@code changeStatus(view, 1, …)}. */
    REBOOT_PENDING(1),

    /** Tweak is confirmed applied (green). Maps to {@code changeStatus(view, 2, …)}. */
    APPLIED(2);

    private final int code;

    TweakStatus(int code) {
        this.code = code;
    }

    /** Returns the {@code int} expected by {@code MainActivity.changeStatus}. */
    public int code() {
        return code;
    }

    /**
     * Maps an {@code int} code (0 / 1 / 2) back to a {@link TweakStatus}.
     *
     * @param code the integer produced by the UI layer
     * @return the corresponding status
     * @throws IllegalArgumentException if {@code code} is not 0, 1, or 2
     */
    public static TweakStatus fromCode(int code) {
        for (TweakStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown TweakStatus code: " + code);
    }
}

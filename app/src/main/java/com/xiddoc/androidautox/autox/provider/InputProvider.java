package com.xiddoc.androidautox.autox.provider;

import com.xiddoc.androidautox.autox.GestureSpec;

/**
 * Privileged-provider seam for injecting input events / gestures onto a specific
 * (virtual) display.
 *
 * <p>This extends the spirit of the existing
 * {@link com.xiddoc.androidautox.autox.GestureInjector} seam — it carries the same
 * {@link #inject(GestureSpec)} contract so existing call sites keep working — but adds
 * a {@link #isInjectionHonored()} capability probe so the provider-selection layer can
 * tell whether {@code injectInputEvent} actually reached the target display (root
 * reflection on a hardened device may silently drop cross-display injection, whereas an
 * LSPosed hook that relaxes the display check honors it).
 *
 * <p>{@link com.xiddoc.androidautox.autox.GestureInjector} is intentionally a
 * super-set-free, narrower interface; {@code InputProvider} is the WS4 provider-seam
 * extension. {@code ReflectiveGestureInjector} implements both.
 */
public interface InputProvider {

    /**
     * Injects the gesture described by {@code spec} onto its target display.
     *
     * <p>Best-effort: returns {@code false} (never throws) if the privilege is absent
     * or the underlying platform call is unavailable.
     *
     * @param spec the gesture to inject; must not be {@code null}
     * @return {@code true} if the input subsystem accepted the gesture
     */
    boolean inject(GestureSpec spec);

    /**
     * Reports whether cross-display input injection is actually honored by this
     * provider on the live system, as last observed.
     *
     * <p>Implementations should set this from a real probe (e.g. a no-op injection to
     * the target display whose return value / side effect is inspected) rather than
     * assuming success. The provider-selection policy uses this to decide between the
     * LSPosed and root-reflection seams and to flag the DEGRADED case.
     *
     * @return {@code true} if injection to the target display is believed to work
     */
    boolean isInjectionHonored();
}

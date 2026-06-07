package com.xiddoc.androidautox.autox;

/**
 * Pure decision logic for routing a projected app's audio to a specific car output device.
 *
 * <p>WS6 — given the target app UID and the available car output device (typed as
 * {@link CarAudioDevice} — BT_A2DP, USB, or NONE, together with a device address string),
 * this class computes:
 *
 * <ul>
 *   <li>an <em>apply step</em> — {@link RouteStep.SetAffinity} to bind the UID to the
 *       device address, or {@link RouteStep.NoRoute} when routing is impossible.</li>
 *   <li>a <em>revert step</em> — {@link RouteStep.ClearAffinity} to release the binding
 *       when the AutoX session ends.</li>
 * </ul>
 *
 * <p>Both steps are returned together in an immutable {@link RouteDecision}.
 *
 * <h3>Validation rules</h3>
 * <ol>
 *   <li><b>Invalid UID</b> (zero or negative — real app UIDs are &ge; 1000) → rejected;
 *       apply step is {@link RouteStep.NoRoute}.</li>
 *   <li><b>NONE device type</b> → cannot route; apply step is {@link RouteStep.NoRoute}.</li>
 *   <li><b>Null/blank device address</b> → treated as unusable device; apply step is
 *       {@link RouteStep.NoRoute}.</li>
 *   <li>Otherwise → apply step is {@link RouteStep.SetAffinity}; revert step is
 *       {@link RouteStep.ClearAffinity}.</li>
 * </ol>
 *
 * <p>No Android imports — fully unit testable on the plain JVM. No side effects.
 */
public final class AudioRoutePolicy {

    /**
     * Represents the type of car audio output device available to route projected-app audio.
     */
    public enum CarAudioDevice {
        /** Bluetooth A2DP output (e.g. headunit via BT). */
        BT_A2DP,
        /** USB audio output (e.g. USB-connected headunit audio bus). */
        USB,
        /** No car audio device is available; routing is impossible. */
        NONE
    }

    /**
     * Sealed hierarchy of routing steps. Each step is an immutable value object.
     *
     * <p>Three concrete leaf types:
     * <ul>
     *   <li>{@link SetAffinity} — instruct the {@code AudioRouter} to pin {@code uid} to
     *       {@code deviceAddress}.</li>
     *   <li>{@link ClearAffinity} — instruct the {@code AudioRouter} to release the
     *       per-UID affinity for {@code uid}.</li>
     *   <li>{@link NoRoute} — no routing action is possible or needed; the caller must
     *       not call any {@code AudioRouter} method for this step.</li>
     * </ul>
     */
    public abstract static class RouteStep {

        private RouteStep() {
        }

        /**
         * Instructs the AudioRouter to pin {@code uid}'s audio to {@code deviceAddress}.
         * Both fields are validated non-empty by the {@link AudioRoutePolicy} factory.
         */
        public static final class SetAffinity extends RouteStep {
            /** The app UID whose audio is pinned. Always &ge; 1. */
            public final int uid;
            /** The target device address. Never null or blank. */
            public final String deviceAddress;

            SetAffinity(int uid, String deviceAddress) {
                this.uid = uid;
                this.deviceAddress = deviceAddress;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof SetAffinity)) return false;
                SetAffinity s = (SetAffinity) o;
                return uid == s.uid && deviceAddress.equals(s.deviceAddress);
            }

            @Override
            public int hashCode() {
                return 31 * uid + deviceAddress.hashCode();
            }

            @Override
            public String toString() {
                return "SetAffinity{uid=" + uid + ", deviceAddress='" + deviceAddress + "'}";
            }
        }

        /**
         * Instructs the AudioRouter to release the per-UID affinity for {@code uid},
         * returning the app to default audio routing.
         */
        public static final class ClearAffinity extends RouteStep {
            /** The app UID whose affinity is released. Always &ge; 1. */
            public final int uid;

            ClearAffinity(int uid) {
                this.uid = uid;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof ClearAffinity)) return false;
                ClearAffinity c = (ClearAffinity) o;
                return uid == c.uid;
            }

            @Override
            public int hashCode() {
                return uid;
            }

            @Override
            public String toString() {
                return "ClearAffinity{uid=" + uid + "}";
            }
        }

        /**
         * Indicates that no routing action is possible for this step. The caller must
         * not invoke any {@code AudioRouter} method. Contains a human-readable reason.
         */
        public static final class NoRoute extends RouteStep {
            /** Human-readable explanation of why routing is not possible. Never null. */
            public final String reason;

            NoRoute(String reason) {
                this.reason = reason;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof NoRoute)) return false;
                NoRoute n = (NoRoute) o;
                return reason.equals(n.reason);
            }

            @Override
            public int hashCode() {
                return reason.hashCode();
            }

            @Override
            public String toString() {
                return "NoRoute{reason='" + reason + "'}";
            }
        }
    }

    /**
     * Immutable result of {@link AudioRoutePolicy#decide}: the step to apply when a
     * projection session starts and the step to revert when it ends.
     *
     * <p>If {@link #applyStep} is a {@link RouteStep.NoRoute}, the session must not
     * call the AudioRouter; {@link #revertStep} will also be {@link RouteStep.NoRoute}
     * in that case.
     */
    public static final class RouteDecision {
        /** The step to apply at session start. Never null. */
        public final RouteStep applyStep;
        /** The step to apply when the session ends or is disabled. Never null. */
        public final RouteStep revertStep;

        RouteDecision(RouteStep applyStep, RouteStep revertStep) {
            this.applyStep = applyStep;
            this.revertStep = revertStep;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RouteDecision)) return false;
            RouteDecision d = (RouteDecision) o;
            return applyStep.equals(d.applyStep) && revertStep.equals(d.revertStep);
        }

        @Override
        public int hashCode() {
            return 31 * applyStep.hashCode() + revertStep.hashCode();
        }

        @Override
        public String toString() {
            return "RouteDecision{applyStep=" + applyStep + ", revertStep=" + revertStep + "}";
        }
    }

    private AudioRoutePolicy() {
    }

    /**
     * Computes the routing decision for the given target-app UID and available car device.
     *
     * <p>Decision rules (applied in order):
     * <ol>
     *   <li>If {@code uid} is &le; 0 → NoRoute (invalid UID) + NoRoute revert.</li>
     *   <li>If {@code deviceType} is {@code NONE} → NoRoute (no device) + NoRoute revert.</li>
     *   <li>If {@code deviceAddress} is null or blank → NoRoute (unusable address) + NoRoute
     *       revert.</li>
     *   <li>Otherwise → SetAffinity(uid, deviceAddress) + ClearAffinity(uid).</li>
     * </ol>
     *
     * @param uid           the target app UID (must be &ge; 1 for a routable request)
     * @param deviceType    the available car audio output device type; must not be null
     * @param deviceAddress the audio device address string (e.g. a BT MAC or USB bus
     *                      address); may be null
     * @return an immutable {@link RouteDecision}; never null
     * @throws IllegalArgumentException if {@code deviceType} is null
     */
    public static RouteDecision decide(int uid, CarAudioDevice deviceType, String deviceAddress) {
        if (deviceType == null) {
            throw new IllegalArgumentException("deviceType must not be null");
        }

        if (uid <= 0) {
            return noRoutePair("Invalid UID: " + uid + " — must be >= 1");
        }

        if (deviceType == CarAudioDevice.NONE) {
            return noRoutePair("No car audio device available (NONE)");
        }

        if (deviceAddress == null || deviceAddress.trim().isEmpty()) {
            return noRoutePair("Device address is null or blank — cannot route");
        }

        return new RouteDecision(
                new RouteStep.SetAffinity(uid, deviceAddress),
                new RouteStep.ClearAffinity(uid));
    }

    /** Convenience: builds a decision where both steps are {@link RouteStep.NoRoute}. */
    private static RouteDecision noRoutePair(String reason) {
        return new RouteDecision(
                new RouteStep.NoRoute(reason),
                new RouteStep.NoRoute(reason));
    }
}

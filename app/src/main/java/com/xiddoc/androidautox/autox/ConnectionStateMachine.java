package com.xiddoc.androidautox.autox;

/**
 * Pure (no Android imports) connection lifecycle state machine for the AutoX Car App
 * session.
 *
 * <h2>Purpose</h2>
 * <p>The Android Auto host can connect, disconnect, and reconnect to
 * {@link AutoXCarAppService} multiple times over the lifetime of the app.  Within a
 * single session the {@link AutoXScreen}'s {@code SurfaceCallback} methods fire
 * {@code onSurfaceAvailable} / {@code onSurfaceDestroyed} independently of the higher-
 * level session connect/disconnect events.  This class models the combined lifecycle
 * as a single deterministic state machine so the glue layer can query the current state
 * and decide what actions to take without duplicating transition logic.
 *
 * <h2>States</h2>
 * <pre>
 *   IDLE            — no host connected, no surface
 *   CONNECTED       — host connected, no surface yet
 *   SURFACE_ACTIVE  — host connected AND surface available (virtual display running)
 *   DISCONNECTED    — host disconnected (surface was active or just connected)
 *   RECONNECTING    — reconnect attempt in progress (between backoff retries)
 * </pre>
 *
 * <h2>Events</h2>
 * <ul>
 *   <li>{@link Event#CONNECT}           — host connected (session created)</li>
 *   <li>{@link Event#DISCONNECT}        — host disconnected (session finished)</li>
 *   <li>{@link Event#SURFACE_AVAILABLE} — surface callback delivered a valid surface</li>
 *   <li>{@link Event#SURFACE_DESTROYED} — surface callback reported surface gone</li>
 *   <li>{@link Event#RECONNECT}         — a reconnect attempt is being made</li>
 * </ul>
 *
 * <h2>Transition table</h2>
 * <pre>
 *   IDLE            + CONNECT           → CONNECTED
 *   IDLE            + RECONNECT         → RECONNECTING
 *   CONNECTED       + SURFACE_AVAILABLE → SURFACE_ACTIVE
 *   CONNECTED       + DISCONNECT        → DISCONNECTED
 *   SURFACE_ACTIVE  + SURFACE_DESTROYED → CONNECTED
 *   SURFACE_ACTIVE  + DISCONNECT        → DISCONNECTED
 *   DISCONNECTED    + RECONNECT         → RECONNECTING
 *   RECONNECTING    + CONNECT           → CONNECTED
 *   RECONNECTING    + DISCONNECT        → DISCONNECTED
 * </pre>
 * All other (state, event) pairs are no-ops (state unchanged) so the machine is total
 * and crash-safe.
 *
 * <p>This class is pure Java with <b>no Android imports</b> and must remain at 100%
 * line + branch coverage.
 */
public final class ConnectionStateMachine {

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /**
     * The lifecycle states of an AutoX Car App session.
     */
    public enum State {
        /** No host connected, no surface. Initial state after construction. */
        IDLE,
        /** Host connected but no surface delivered yet. */
        CONNECTED,
        /** Host connected and surface available — virtual display is running. */
        SURFACE_ACTIVE,
        /** Host disconnected (was in CONNECTED or SURFACE_ACTIVE). */
        DISCONNECTED,
        /** A reconnect attempt is in progress. */
        RECONNECTING,
    }

    // ------------------------------------------------------------------
    // Event
    // ------------------------------------------------------------------

    /**
     * Events that drive the connection lifecycle.
     */
    public enum Event {
        /** Android Auto host connected (session created). */
        CONNECT,
        /** Android Auto host disconnected. */
        DISCONNECT,
        /** Car App SDK delivered a valid surface ({@code onSurfaceAvailable}). */
        SURFACE_AVAILABLE,
        /** Car App SDK reported the surface is gone ({@code onSurfaceDestroyed}). */
        SURFACE_DESTROYED,
        /** A reconnect attempt is being initiated. */
        RECONNECT,
    }

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    private State current;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    /**
     * Creates a new {@code ConnectionStateMachine} in the {@link State#IDLE} state.
     */
    public ConnectionStateMachine() {
        this.current = State.IDLE;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Returns the current state without modifying it.
     *
     * @return non-null current {@link State}
     */
    public State getState() {
        return current;
    }

    /**
     * Applies an event to the state machine, advancing to the next state if the
     * transition is defined.
     *
     * <p>Unknown (state, event) pairs are silently ignored (no-op): the state machine
     * is total and crash-safe for any input.
     *
     * @param event the {@link Event} to process; must not be {@code null}
     * @return the new state after the transition (same as {@link #getState()})
     * @throws NullPointerException if {@code event} is {@code null}
     */
    public State transition(Event event) {
        if (event == null) {
            throw new NullPointerException("event must not be null");
        }
        current = nextState(current, event);
        return current;
    }

    // ------------------------------------------------------------------
    // Private transition table
    // ------------------------------------------------------------------

    private static State nextState(State state, Event event) {
        if (state == State.IDLE) {
            if (event == Event.CONNECT)    return State.CONNECTED;
            if (event == Event.RECONNECT)  return State.RECONNECTING;
            return state;
        }
        if (state == State.CONNECTED) {
            if (event == Event.SURFACE_AVAILABLE) return State.SURFACE_ACTIVE;
            if (event == Event.DISCONNECT)        return State.DISCONNECTED;
            return state;
        }
        if (state == State.SURFACE_ACTIVE) {
            if (event == Event.SURFACE_DESTROYED) return State.CONNECTED;
            if (event == Event.DISCONNECT)        return State.DISCONNECTED;
            return state;
        }
        if (state == State.DISCONNECTED) {
            if (event == Event.RECONNECT) return State.RECONNECTING;
            return state;
        }
        // state == RECONNECTING (or any future state added)
        if (event == Event.CONNECT)    return State.CONNECTED;
        if (event == Event.DISCONNECT) return State.DISCONNECTED;
        return state;
    }
}

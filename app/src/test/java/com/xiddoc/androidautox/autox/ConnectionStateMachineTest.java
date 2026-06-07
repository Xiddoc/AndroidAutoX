package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link ConnectionStateMachine} — no Android runtime required.
 *
 * <p>Covers every defined transition in the table plus:
 * <ul>
 *   <li>Initial state.</li>
 *   <li>All no-op (undefined) transitions — state must be unchanged.</li>
 *   <li>Null event — must throw {@link NullPointerException}.</li>
 *   <li>{@link ConnectionStateMachine#getState()} without transitions.</li>
 *   <li>Multi-step chains (happy path + reconnect path).</li>
 *   <li>Enum sanity — all states and events declared.</li>
 * </ul>
 */
public class ConnectionStateMachineTest {

    // ------------------------------------------------------------------
    // Initial state
    // ------------------------------------------------------------------

    @Test
    public void initialState_isIdle() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        assertEquals(ConnectionStateMachine.State.IDLE, sm.getState());
    }

    // ------------------------------------------------------------------
    // IDLE transitions
    // ------------------------------------------------------------------

    @Test
    public void idle_connect_becomesConnected() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        assertEquals(ConnectionStateMachine.State.CONNECTED,
                sm.transition(ConnectionStateMachine.Event.CONNECT));
    }

    @Test
    public void idle_reconnect_becomesReconnecting() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        assertEquals(ConnectionStateMachine.State.RECONNECTING,
                sm.transition(ConnectionStateMachine.Event.RECONNECT));
    }

    @Test
    public void idle_disconnect_isNoop() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        assertEquals(ConnectionStateMachine.State.IDLE,
                sm.transition(ConnectionStateMachine.Event.DISCONNECT));
    }

    @Test
    public void idle_surfaceAvailable_isNoop() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        assertEquals(ConnectionStateMachine.State.IDLE,
                sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE));
    }

    @Test
    public void idle_surfaceDestroyed_isNoop() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        assertEquals(ConnectionStateMachine.State.IDLE,
                sm.transition(ConnectionStateMachine.Event.SURFACE_DESTROYED));
    }

    // ------------------------------------------------------------------
    // CONNECTED transitions
    // ------------------------------------------------------------------

    @Test
    public void connected_surfaceAvailable_becomesSurfaceActive() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.CONNECTED);
        assertEquals(ConnectionStateMachine.State.SURFACE_ACTIVE,
                sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE));
    }

    @Test
    public void connected_disconnect_becomesDisconnected() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.CONNECTED);
        assertEquals(ConnectionStateMachine.State.DISCONNECTED,
                sm.transition(ConnectionStateMachine.Event.DISCONNECT));
    }

    @Test
    public void connected_connect_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.CONNECTED);
        assertEquals(ConnectionStateMachine.State.CONNECTED,
                sm.transition(ConnectionStateMachine.Event.CONNECT));
    }

    @Test
    public void connected_reconnect_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.CONNECTED);
        assertEquals(ConnectionStateMachine.State.CONNECTED,
                sm.transition(ConnectionStateMachine.Event.RECONNECT));
    }

    @Test
    public void connected_surfaceDestroyed_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.CONNECTED);
        assertEquals(ConnectionStateMachine.State.CONNECTED,
                sm.transition(ConnectionStateMachine.Event.SURFACE_DESTROYED));
    }

    // ------------------------------------------------------------------
    // SURFACE_ACTIVE transitions
    // ------------------------------------------------------------------

    @Test
    public void surfaceActive_surfaceDestroyed_becomesConnected() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.SURFACE_ACTIVE);
        assertEquals(ConnectionStateMachine.State.CONNECTED,
                sm.transition(ConnectionStateMachine.Event.SURFACE_DESTROYED));
    }

    @Test
    public void surfaceActive_disconnect_becomesDisconnected() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.SURFACE_ACTIVE);
        assertEquals(ConnectionStateMachine.State.DISCONNECTED,
                sm.transition(ConnectionStateMachine.Event.DISCONNECT));
    }

    @Test
    public void surfaceActive_connect_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.SURFACE_ACTIVE);
        assertEquals(ConnectionStateMachine.State.SURFACE_ACTIVE,
                sm.transition(ConnectionStateMachine.Event.CONNECT));
    }

    @Test
    public void surfaceActive_reconnect_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.SURFACE_ACTIVE);
        assertEquals(ConnectionStateMachine.State.SURFACE_ACTIVE,
                sm.transition(ConnectionStateMachine.Event.RECONNECT));
    }

    @Test
    public void surfaceActive_surfaceAvailable_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.SURFACE_ACTIVE);
        assertEquals(ConnectionStateMachine.State.SURFACE_ACTIVE,
                sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE));
    }

    // ------------------------------------------------------------------
    // DISCONNECTED transitions
    // ------------------------------------------------------------------

    @Test
    public void disconnected_reconnect_becomesReconnecting() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.DISCONNECTED);
        assertEquals(ConnectionStateMachine.State.RECONNECTING,
                sm.transition(ConnectionStateMachine.Event.RECONNECT));
    }

    @Test
    public void disconnected_connect_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.DISCONNECTED);
        assertEquals(ConnectionStateMachine.State.DISCONNECTED,
                sm.transition(ConnectionStateMachine.Event.CONNECT));
    }

    @Test
    public void disconnected_disconnect_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.DISCONNECTED);
        assertEquals(ConnectionStateMachine.State.DISCONNECTED,
                sm.transition(ConnectionStateMachine.Event.DISCONNECT));
    }

    @Test
    public void disconnected_surfaceAvailable_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.DISCONNECTED);
        assertEquals(ConnectionStateMachine.State.DISCONNECTED,
                sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE));
    }

    @Test
    public void disconnected_surfaceDestroyed_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.DISCONNECTED);
        assertEquals(ConnectionStateMachine.State.DISCONNECTED,
                sm.transition(ConnectionStateMachine.Event.SURFACE_DESTROYED));
    }

    // ------------------------------------------------------------------
    // RECONNECTING transitions
    // ------------------------------------------------------------------

    @Test
    public void reconnecting_connect_becomesConnected() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.RECONNECTING);
        assertEquals(ConnectionStateMachine.State.CONNECTED,
                sm.transition(ConnectionStateMachine.Event.CONNECT));
    }

    @Test
    public void reconnecting_disconnect_becomesDisconnected() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.RECONNECTING);
        assertEquals(ConnectionStateMachine.State.DISCONNECTED,
                sm.transition(ConnectionStateMachine.Event.DISCONNECT));
    }

    @Test
    public void reconnecting_reconnect_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.RECONNECTING);
        assertEquals(ConnectionStateMachine.State.RECONNECTING,
                sm.transition(ConnectionStateMachine.Event.RECONNECT));
    }

    @Test
    public void reconnecting_surfaceAvailable_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.RECONNECTING);
        assertEquals(ConnectionStateMachine.State.RECONNECTING,
                sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE));
    }

    @Test
    public void reconnecting_surfaceDestroyed_isNoop() {
        ConnectionStateMachine sm = inState(ConnectionStateMachine.State.RECONNECTING);
        assertEquals(ConnectionStateMachine.State.RECONNECTING,
                sm.transition(ConnectionStateMachine.Event.SURFACE_DESTROYED));
    }

    // ------------------------------------------------------------------
    // Null event
    // ------------------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void transition_nullEvent_throwsNPE() {
        new ConnectionStateMachine().transition(null);
    }

    // ------------------------------------------------------------------
    // Multi-step chains
    // ------------------------------------------------------------------

    @Test
    public void happyPath_idleToSurfaceActive() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        sm.transition(ConnectionStateMachine.Event.CONNECT);
        assertEquals(ConnectionStateMachine.State.CONNECTED, sm.getState());
        sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE);
        assertEquals(ConnectionStateMachine.State.SURFACE_ACTIVE, sm.getState());
    }

    @Test
    public void reconnectPath_fullCycle() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        sm.transition(ConnectionStateMachine.Event.CONNECT);
        sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE);
        sm.transition(ConnectionStateMachine.Event.DISCONNECT);
        assertEquals(ConnectionStateMachine.State.DISCONNECTED, sm.getState());
        sm.transition(ConnectionStateMachine.Event.RECONNECT);
        assertEquals(ConnectionStateMachine.State.RECONNECTING, sm.getState());
        sm.transition(ConnectionStateMachine.Event.CONNECT);
        assertEquals(ConnectionStateMachine.State.CONNECTED, sm.getState());
        sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE);
        assertEquals(ConnectionStateMachine.State.SURFACE_ACTIVE, sm.getState());
    }

    @Test
    public void surfaceDestroyedThenRestoredWithinSession() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        sm.transition(ConnectionStateMachine.Event.CONNECT);
        sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE);
        sm.transition(ConnectionStateMachine.Event.SURFACE_DESTROYED);
        assertEquals(ConnectionStateMachine.State.CONNECTED, sm.getState());
        sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE);
        assertEquals(ConnectionStateMachine.State.SURFACE_ACTIVE, sm.getState());
    }

    @Test
    public void reconnectFromIdle_thenConnect() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        sm.transition(ConnectionStateMachine.Event.RECONNECT);
        assertEquals(ConnectionStateMachine.State.RECONNECTING, sm.getState());
        sm.transition(ConnectionStateMachine.Event.CONNECT);
        assertEquals(ConnectionStateMachine.State.CONNECTED, sm.getState());
    }

    // ------------------------------------------------------------------
    // Enum sanity
    // ------------------------------------------------------------------

    @Test
    public void state_allValuesExist() {
        assertNotNull(ConnectionStateMachine.State.IDLE);
        assertNotNull(ConnectionStateMachine.State.CONNECTED);
        assertNotNull(ConnectionStateMachine.State.SURFACE_ACTIVE);
        assertNotNull(ConnectionStateMachine.State.DISCONNECTED);
        assertNotNull(ConnectionStateMachine.State.RECONNECTING);
        assertEquals(5, ConnectionStateMachine.State.values().length);
    }

    @Test
    public void event_allValuesExist() {
        assertNotNull(ConnectionStateMachine.Event.CONNECT);
        assertNotNull(ConnectionStateMachine.Event.DISCONNECT);
        assertNotNull(ConnectionStateMachine.Event.SURFACE_AVAILABLE);
        assertNotNull(ConnectionStateMachine.Event.SURFACE_DESTROYED);
        assertNotNull(ConnectionStateMachine.Event.RECONNECT);
        assertEquals(5, ConnectionStateMachine.Event.values().length);
    }

    // ------------------------------------------------------------------
    // Helper: reach a target state via the minimal transition path
    // ------------------------------------------------------------------

    private static ConnectionStateMachine inState(ConnectionStateMachine.State target) {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        switch (target) {
            case IDLE:
                break;
            case CONNECTED:
                sm.transition(ConnectionStateMachine.Event.CONNECT);
                break;
            case SURFACE_ACTIVE:
                sm.transition(ConnectionStateMachine.Event.CONNECT);
                sm.transition(ConnectionStateMachine.Event.SURFACE_AVAILABLE);
                break;
            case DISCONNECTED:
                sm.transition(ConnectionStateMachine.Event.CONNECT);
                sm.transition(ConnectionStateMachine.Event.DISCONNECT);
                break;
            case RECONNECTING:
                sm.transition(ConnectionStateMachine.Event.CONNECT);
                sm.transition(ConnectionStateMachine.Event.DISCONNECT);
                sm.transition(ConnectionStateMachine.Event.RECONNECT);
                break;
        }
        assertEquals("helper: could not reach state " + target, target, sm.getState());
        return sm;
    }
}

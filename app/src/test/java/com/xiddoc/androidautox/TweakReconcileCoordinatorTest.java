package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Robolectric tests for {@link TweakReconcileCoordinator}, the pure orchestration extracted
 * out of {@code MainActivity}.  Verifies:
 * <ul>
 *   <li>label selection: DISABLED → disabledLabel; APPLIED / REBOOT_PENDING → enabledLabel;
 *       icon-only (null-label) targets stay null;</li>
 *   <li>the sink is invoked exactly once per key, in registration order;</li>
 *   <li>healing side-effects flow through the real {@link TweakStateStore}.</li>
 * </ul>
 *
 * <p>Uses a real {@link TweakStateStore} (final class) over Robolectric's in-memory prefs, plus
 * a map-driven fake checker and a recording sink.
 */
@RunWith(RobolectricTestRunner.class)
public class TweakReconcileCoordinatorTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private TweakStateStore store() {
        return new TweakStateStore(context);
    }

    /** One recorded sink call. */
    private static final class Painted {
        final String key;
        final TweakStatus status;
        final String label;

        Painted(String key, TweakStatus status, String label) {
            this.key = key;
            this.status = status;
            this.label = label;
        }
    }

    /** Records every paint, in order. */
    private static final class RecordingSink implements TweakReconcileCoordinator.Sink {
        final List<Painted> calls = new ArrayList<Painted>();

        @Override
        public void paint(String key, TweakStatus status, String labelOrNull) {
            calls.add(new Painted(key, status, labelOrNull));
        }
    }

    /** Fake checker driven by a key → tri-state map. */
    private static TweakReconcileCoordinator.Checker checkerOf(final Map<String, Boolean> m) {
        return new TweakReconcileCoordinator.Checker() {
            @Override
            public Boolean appliedState(String key) {
                return m.get(key);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Label selection
    // -------------------------------------------------------------------------

    @Test
    public void disabledStatus_usesDisabledLabel() {
        TweakStateStore store = store();
        // enabled=false → DISABLED regardless of DB.
        LinkedHashMap<String, TweakReconcileCoordinator.Target> targets = new LinkedHashMap<>();
        targets.put("k", new TweakReconcileCoordinator.Target("ON-label", "OFF-label"));
        Map<String, Boolean> db = new LinkedHashMap<>();
        db.put("k", Boolean.FALSE);

        RecordingSink sink = new RecordingSink();
        new TweakReconcileCoordinator(targets, checkerOf(db), store).run(sink);

        assertEquals(1, sink.calls.size());
        assertEquals(TweakStatus.DISABLED, sink.calls.get(0).status);
        assertEquals("OFF-label", sink.calls.get(0).label);
    }

    @Test
    public void appliedStatus_usesEnabledLabel() {
        TweakStateStore store = store();
        store.setEnabled("k", true);
        LinkedHashMap<String, TweakReconcileCoordinator.Target> targets = new LinkedHashMap<>();
        targets.put("k", new TweakReconcileCoordinator.Target("ON-label", "OFF-label"));
        Map<String, Boolean> db = new LinkedHashMap<>();
        db.put("k", Boolean.TRUE);

        RecordingSink sink = new RecordingSink();
        new TweakReconcileCoordinator(targets, checkerOf(db), store).run(sink);

        assertEquals(TweakStatus.APPLIED, sink.calls.get(0).status);
        assertEquals("ON-label", sink.calls.get(0).label);
    }

    @Test
    public void rebootPendingStatus_usesEnabledLabel() {
        TweakStateStore store = store();
        store.setEnabled("k", true);
        store.setRebootPending("k", true);
        LinkedHashMap<String, TweakReconcileCoordinator.Target> targets = new LinkedHashMap<>();
        targets.put("k", new TweakReconcileCoordinator.Target("ON-label", "OFF-label"));
        Map<String, Boolean> db = new LinkedHashMap<>();
        db.put("k", Boolean.TRUE); // TRUE but reboot pending → yellow, still the enabled label.

        RecordingSink sink = new RecordingSink();
        new TweakReconcileCoordinator(targets, checkerOf(db), store).run(sink);

        assertEquals(TweakStatus.REBOOT_PENDING, sink.calls.get(0).status);
        assertEquals("ON-label", sink.calls.get(0).label);
    }

    @Test
    public void iconOnlyTarget_keepsNullLabel_inBothBranches() {
        TweakStateStore store = store();
        LinkedHashMap<String, TweakReconcileCoordinator.Target> targets = new LinkedHashMap<>();
        // null labels = icon-only (dynamic-value) target.
        targets.put("disabledKey", new TweakReconcileCoordinator.Target(null, null));
        store.setEnabled("appliedKey", true);
        targets.put("appliedKey", new TweakReconcileCoordinator.Target(null, null));
        Map<String, Boolean> db = new LinkedHashMap<>();
        db.put("disabledKey", Boolean.FALSE);
        db.put("appliedKey", Boolean.TRUE);

        RecordingSink sink = new RecordingSink();
        new TweakReconcileCoordinator(targets, checkerOf(db), store).run(sink);

        assertEquals(2, sink.calls.size());
        assertEquals(TweakStatus.DISABLED, sink.calls.get(0).status);
        assertNull("disabled icon-only target must carry a null label", sink.calls.get(0).label);
        assertEquals(TweakStatus.APPLIED, sink.calls.get(1).status);
        assertNull("applied icon-only target must carry a null label", sink.calls.get(1).label);
    }

    // -------------------------------------------------------------------------
    // Sink invoked once per key, in order
    // -------------------------------------------------------------------------

    @Test
    public void sinkInvokedOncePerKey_inRegistrationOrder() {
        TweakStateStore store = store();
        LinkedHashMap<String, TweakReconcileCoordinator.Target> targets = new LinkedHashMap<>();
        targets.put("a", new TweakReconcileCoordinator.Target("a-on", "a-off"));
        targets.put("b", new TweakReconcileCoordinator.Target("b-on", "b-off"));
        targets.put("c", new TweakReconcileCoordinator.Target("c-on", "c-off"));
        Map<String, Boolean> db = new LinkedHashMap<>(); // all UNKNOWN

        RecordingSink sink = new RecordingSink();
        new TweakReconcileCoordinator(targets, checkerOf(db), store).run(sink);

        assertEquals(3, sink.calls.size());
        assertEquals("a", sink.calls.get(0).key);
        assertEquals("b", sink.calls.get(1).key);
        assertEquals("c", sink.calls.get(2).key);
    }

    // -------------------------------------------------------------------------
    // Healing side-effect flows through the store
    // -------------------------------------------------------------------------

    @Test
    public void dbTrue_healsLostEnabledBoolean_viaStore() {
        TweakStateStore store = store();
        // enabled boolean was lost (defaults false), but DB confirms applied.
        LinkedHashMap<String, TweakReconcileCoordinator.Target> targets = new LinkedHashMap<>();
        targets.put("k", new TweakReconcileCoordinator.Target("ON", "OFF"));
        Map<String, Boolean> db = new LinkedHashMap<>();
        db.put("k", Boolean.TRUE);

        new TweakReconcileCoordinator(targets, checkerOf(db), store).run(new RecordingSink());

        assertTrue("coordinator must heal the enabled boolean through the store",
                store.isEnabled("k"));
        // The persisted value is the one the legacy load(key) path reads.
        assertTrue(context.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .getBoolean("k", false));
    }

    @Test
    public void dbFalse_neverHeals() {
        TweakStateStore store = store();
        LinkedHashMap<String, TweakReconcileCoordinator.Target> targets = new LinkedHashMap<>();
        targets.put("k", new TweakReconcileCoordinator.Target("ON", "OFF"));
        Map<String, Boolean> db = new LinkedHashMap<>();
        db.put("k", Boolean.FALSE);

        new TweakReconcileCoordinator(targets, checkerOf(db), store).run(new RecordingSink());

        assertEquals(TweakStatus.DISABLED, TweakReconciler.reconcile("k", Boolean.FALSE, store));
        org.junit.Assert.assertFalse("FALSE must never resurrect a disabled tweak",
                store.isEnabled("k"));
    }
}

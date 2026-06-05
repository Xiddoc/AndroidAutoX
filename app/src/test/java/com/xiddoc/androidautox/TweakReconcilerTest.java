package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Exhaustive Robolectric tests for {@link TweakReconciler#reconcile}.
 *
 * <p>Uses a real {@link TweakStateStore} (the class is {@code final}, so it cannot be faked
 * by subclassing) backed by Robolectric's in-memory SharedPreferences. "Store not written"
 * assertions are made by checking the persisted enabled boolean did not change.
 *
 * <h3>Healing matrix (the load-bearing behaviour)</h3>
 * <pre>
 * appliedInDb | enabled before | rebootPending | heals enabled? | result
 * ------------+----------------+---------------+----------------+----------------
 *   TRUE      |     false      |    false      |     YES         | APPLIED
 *   TRUE      |     false      |    true        |     YES         | REBOOT_PENDING
 *   TRUE      |     true       |    false      |     no-op       | APPLIED
 *   FALSE     |     true       |    false      |     NEVER       | DISABLED
 *   FALSE     |     false      |    false      |     NEVER       | DISABLED
 *   null      |     true       |    false      |     NEVER       | APPLIED (optimistic)
 *   null      |     false      |    false      |     NEVER       | DISABLED
 * </pre>
 */
@RunWith(RobolectricTestRunner.class)
public class TweakReconcilerTest {

    private static final String KEY = "aa_six_tap";

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

    /** Reads the raw persisted enabled boolean (bypassing the store) for write-assertions. */
    private boolean rawEnabled() {
        return context.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY, false);
    }

    // -------------------------------------------------------------------------
    // appliedInDb == TRUE  → heals a lost enabled boolean
    // -------------------------------------------------------------------------

    @Test
    public void true_healsFalseBoolean_andReturnsApplied() {
        TweakStateStore store = store();
        // enabled defaults to false (boolean was lost).
        assertFalse(store.isEnabled(KEY));

        TweakStatus result = TweakReconciler.reconcile(KEY, Boolean.TRUE, store);

        assertEquals(TweakStatus.APPLIED, result);
        assertTrue("enabled boolean must be healed to true", store.isEnabled(KEY));
        assertTrue("heal must be persisted", rawEnabled());
    }

    @Test
    public void true_withRebootPending_healsEnabled_andReturnsRebootPending() {
        TweakStateStore store = store();
        store.setRebootPending(KEY, true);
        assertFalse(store.isEnabled(KEY));

        TweakStatus result = TweakReconciler.reconcile(KEY, Boolean.TRUE, store);

        assertEquals(TweakStatus.REBOOT_PENDING, result);
        assertTrue("enabled must still be healed even when reboot is pending",
                store.isEnabled(KEY));
        assertTrue(rawEnabled());
    }

    @Test
    public void true_whenAlreadyEnabled_isNoOp_andReturnsApplied() {
        TweakStateStore store = store();
        store.setEnabled(KEY, true);

        TweakStatus result = TweakReconciler.reconcile(KEY, Boolean.TRUE, store);

        assertEquals(TweakStatus.APPLIED, result);
        assertTrue(store.isEnabled(KEY));
    }

    // -------------------------------------------------------------------------
    // appliedInDb == FALSE  → never heals; DISABLED
    // -------------------------------------------------------------------------

    @Test
    public void false_withEnabled_returnsDisabled_andDoesNotHeal() {
        TweakStateStore store = store();
        store.setEnabled(KEY, true);

        TweakStatus result = TweakReconciler.reconcile(KEY, Boolean.FALSE, store);

        // Confirmed-absent flags: tweak drifted/failed -> red. enabled is left untouched
        // (the resolver paints DISABLED regardless; we must NOT rewrite the boolean here).
        assertEquals(TweakStatus.DISABLED, result);
        assertTrue("FALSE must not clear or alter the enabled boolean", store.isEnabled(KEY));
    }

    @Test
    public void false_whenNotEnabled_returnsDisabled_andDoesNotHeal() {
        TweakStateStore store = store();
        assertFalse(store.isEnabled(KEY));

        TweakStatus result = TweakReconciler.reconcile(KEY, Boolean.FALSE, store);

        assertEquals(TweakStatus.DISABLED, result);
        assertFalse("FALSE must never resurrect a disabled tweak", store.isEnabled(KEY));
        assertFalse("store must not be written in the FALSE/not-enabled case", rawEnabled());
    }

    // -------------------------------------------------------------------------
    // appliedInDb == null (UNKNOWN)  → never heals
    // -------------------------------------------------------------------------

    @Test
    public void null_withEnabled_returnsApplied_optimistic_noHeal() {
        TweakStateStore store = store();
        store.setEnabled(KEY, true);

        TweakStatus result = TweakReconciler.reconcile(KEY, null, store);

        assertEquals(TweakStatus.APPLIED, result);
        assertTrue(store.isEnabled(KEY));
    }

    @Test
    public void null_whenNotEnabled_returnsDisabled_andDoesNotHeal() {
        TweakStateStore store = store();
        assertFalse(store.isEnabled(KEY));

        TweakStatus result = TweakReconciler.reconcile(KEY, null, store);

        assertEquals(TweakStatus.DISABLED, result);
        assertFalse("UNKNOWN must never heal a lost/absent boolean", store.isEnabled(KEY));
        assertFalse("store must not be written in the null/not-enabled case", rawEnabled());
    }

    // -------------------------------------------------------------------------
    // Heal interaction with reboot-pending precedence
    // -------------------------------------------------------------------------

    @Test
    public void enabledTrue_rebootPending_dbTrue_staysYellow() {
        TweakStateStore store = store();
        store.setEnabled(KEY, true);
        store.setRebootPending(KEY, true);

        // DB is TRUE right after writing flags, but reboot hasn't happened: stay yellow.
        assertEquals(TweakStatus.REBOOT_PENDING,
                TweakReconciler.reconcile(KEY, Boolean.TRUE, store));
    }
}

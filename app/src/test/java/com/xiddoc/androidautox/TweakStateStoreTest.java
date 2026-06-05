package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Robolectric unit tests for {@link TweakStateStore}.
 *
 * <p>Verifies persistence semantics, key-collision safety, defaults, cross-instance
 * reads (survival of a fresh store instance), and back-compat with the legacy
 * plain-boolean write path used by {@code MainActivity.save()}.
 */
@RunWith(RobolectricTestRunner.class)
public class TweakStateStoreTest {

    private static final String KEY = "aa_six_tap";
    private static final String KEY2 = "kill_telemetry";

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // Start each test with a clean slate in the shared prefs file.
        context.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    // -------------------------------------------------------------------------
    // Defaults
    // -------------------------------------------------------------------------

    @Test
    public void enabled_defaultsToFalse() {
        TweakStateStore store = new TweakStateStore(context);
        assertFalse(store.isEnabled(KEY));
    }

    @Test
    public void rebootPending_defaultsToFalse() {
        TweakStateStore store = new TweakStateStore(context);
        assertFalse(store.isRebootPending(KEY));
    }

    // -------------------------------------------------------------------------
    // Set / get enabled
    // -------------------------------------------------------------------------

    @Test
    public void setEnabled_true_isReadBackAsTrue() {
        TweakStateStore store = new TweakStateStore(context);
        store.setEnabled(KEY, true);
        assertTrue(store.isEnabled(KEY));
    }

    @Test
    public void setEnabled_false_isReadBackAsFalse() {
        TweakStateStore store = new TweakStateStore(context);
        store.setEnabled(KEY, true);
        store.setEnabled(KEY, false);
        assertFalse(store.isEnabled(KEY));
    }

    @Test
    public void setEnabled_doesNotAffectOtherKey() {
        TweakStateStore store = new TweakStateStore(context);
        store.setEnabled(KEY, true);
        assertFalse(store.isEnabled(KEY2));
    }

    // -------------------------------------------------------------------------
    // Set / get reboot-pending
    // -------------------------------------------------------------------------

    @Test
    public void setRebootPending_true_isReadBackAsTrue() {
        TweakStateStore store = new TweakStateStore(context);
        store.setRebootPending(KEY, true);
        assertTrue(store.isRebootPending(KEY));
    }

    @Test
    public void setRebootPending_false_isReadBackAsFalse() {
        TweakStateStore store = new TweakStateStore(context);
        store.setRebootPending(KEY, true);
        store.setRebootPending(KEY, false);
        assertFalse(store.isRebootPending(KEY));
    }

    @Test
    public void setRebootPending_doesNotAffectOtherKey() {
        TweakStateStore store = new TweakStateStore(context);
        store.setRebootPending(KEY, true);
        assertFalse(store.isRebootPending(KEY2));
    }

    // -------------------------------------------------------------------------
    // Key-collision safety: reboot-pending key must NOT overlap the tweak key
    // -------------------------------------------------------------------------

    /**
     * Checks that writing the reboot-pending marker for KEY does not disturb the
     * enabled boolean stored under the plain KEY.
     */
    @Test
    public void rebootPendingKey_doesNotCollideWithEnabledKey() {
        TweakStateStore store = new TweakStateStore(context);
        store.setEnabled(KEY, true);
        store.setRebootPending(KEY, true);

        // Both should read back independently.
        assertTrue(store.isEnabled(KEY));
        assertTrue(store.isRebootPending(KEY));

        // Clear one; the other should be unaffected.
        store.setRebootPending(KEY, false);
        assertTrue(store.isEnabled(KEY));
        assertFalse(store.isRebootPending(KEY));
    }

    /**
     * Edge case: if a tweak key were named "foo__reboot_pending", it should still
     * not collide with the reboot-pending marker for "foo" stored under
     * "foo__reboot_pending__reboot_pending".
     */
    @Test
    public void rebootPendingKey_derivedKeyIsDistinctFromRawKey() {
        String fakeCollisionKey = KEY + TweakStateStore.REBOOT_PENDING_SUFFIX;
        TweakStateStore store = new TweakStateStore(context);

        store.setEnabled(fakeCollisionKey, true);
        store.setRebootPending(KEY, true);

        // Enabled state of "aa_six_tap__reboot_pending" should be unaffected by
        // setRebootPending("aa_six_tap").
        assertTrue(store.isEnabled(fakeCollisionKey));
        assertTrue(store.isRebootPending(KEY));

        // The storage key for the reboot marker of KEY must be different from KEY.
        assertFalse(TweakStateStore.rebootPendingKey(KEY).equals(KEY));
    }

    // -------------------------------------------------------------------------
    // Persistence: values survive a fresh store instance
    // -------------------------------------------------------------------------

    @Test
    public void enabled_survivesNewStoreInstance() {
        new TweakStateStore(context).setEnabled(KEY, true);

        // Create a brand-new instance backed by the same prefs file.
        assertTrue(new TweakStateStore(context).isEnabled(KEY));
    }

    @Test
    public void rebootPending_survivesNewStoreInstance() {
        new TweakStateStore(context).setRebootPending(KEY, true);

        assertTrue(new TweakStateStore(context).isRebootPending(KEY));
    }

    // -------------------------------------------------------------------------
    // Back-compat: TweakStateStore reads a boolean written under the plain key
    // by the legacy MainActivity.save() path (which uses the same prefs file).
    // -------------------------------------------------------------------------

    @Test
    public void legacyBooleanWrite_isReadableByStore() {
        // Simulate what MainActivity.save(key, true) does internally:
        // putBoolean under the bare key in the "MainActivity" SharedPreferences file.
        context.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY, true)
                .commit();

        TweakStateStore store = new TweakStateStore(context);
        assertTrue("TweakStateStore must read legacy booleans written by MainActivity",
                store.isEnabled(KEY));
    }

    @Test
    public void storeBooleanWrite_isReadableByLegacyPath() {
        TweakStateStore store = new TweakStateStore(context);
        store.setEnabled(KEY, true);

        // Simulate what MainActivity.load(key) does: read from the same prefs file.
        boolean legacyRead = context.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY, false);
        assertTrue("Legacy load() path must see booleans written by TweakStateStore",
                legacyRead);
    }

    // -------------------------------------------------------------------------
    // Key-scheme verification
    // -------------------------------------------------------------------------

    @Test
    public void rebootPendingKey_hasSuffix() {
        String derived = TweakStateStore.rebootPendingKey(KEY);
        assertTrue(derived.startsWith(KEY));
        assertTrue(derived.endsWith(TweakStateStore.REBOOT_PENDING_SUFFIX));
        assertEquals(KEY + TweakStateStore.REBOOT_PENDING_SUFFIX, derived);
    }

    @Test
    public void rebootPendingKey_isNeverEqualToTweakKey() {
        // Ensure the suffix is non-empty, so the derived key always differs.
        assertFalse(TweakStateStore.REBOOT_PENDING_SUFFIX.isEmpty());
        assertFalse(TweakStateStore.rebootPendingKey(KEY).equals(KEY));
    }
}

package com.xiddoc.androidautox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Robolectric tests for {@link BootReceiver}'s reboot-pending clearing.
 *
 * <p>The discriminating behaviour: a genuine device reboot ({@code BOOT_COMPLETED} /
 * {@code LOCKED_BOOT_COMPLETED}) clears every per-tweak reboot-pending marker, whereas an
 * in-place app update ({@code MY_PACKAGE_REPLACED}) — and any unrelated action — must NOT,
 * because an app update is not the reboot those markers were waiting for.
 */
@RunWith(RobolectricTestRunner.class)
public class BootReceiverTest {

    /** A few representative keys drawn from TweakRegistry.ALL_KEYS. */
    private static final String[] SEEDED = {
            "aa_six_tap", "bluetooth_pairing_off", "kill_telemetry", "multi_display",
    };

    private Context context;
    private TweakStateStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
        store = new TweakStateStore(context);
    }

    private void seedAllPending() {
        for (String key : SEEDED) {
            store.setRebootPending(key, true);
        }
    }

    private void assertAllPending(boolean expected) {
        for (String key : SEEDED) {
            if (expected) {
                assertTrue("expected reboot-pending set for " + key, store.isRebootPending(key));
            } else {
                assertFalse("expected reboot-pending cleared for " + key, store.isRebootPending(key));
            }
        }
    }

    @Test
    public void bootCompleted_clearsAllRebootPendingMarkers() {
        seedAllPending();

        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertAllPending(false);
    }

    @Test
    public void lockedBootCompleted_clearsAllRebootPendingMarkers() {
        seedAllPending();

        new BootReceiver().onReceive(context,
                new Intent("android.intent.action.LOCKED_BOOT_COMPLETED"));

        assertAllPending(false);
    }

    @Test
    public void myPackageReplaced_doesNotClearRebootPendingMarkers() {
        seedAllPending();

        // An in-place app update is NOT a reboot, so the markers must survive.
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));

        assertAllPending(true);
    }

    @Test
    public void unrelatedAction_doesNotClearRebootPendingMarkers() {
        seedAllPending();

        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_VIEW));

        assertAllPending(true);
    }

    @Test
    public void nullIntent_isNoOp() {
        seedAllPending();

        new BootReceiver().onReceive(context, null);

        assertAllPending(true);
    }
}

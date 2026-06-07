package com.xiddoc.androidautox;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Invariant test: every LIVE flag-mapped tweak in {@link TweakRegistry#ALL_KEYS} is registered
 * for the post-root reconcile pass, so a newly-added tweak can't silently miss reconciliation
 * (which would let its status icon drift from DB-truth).
 *
 * <p>Inflates {@link MainActivity} via Robolectric and reads back
 * {@link MainActivity#registeredReconcileKeys()}, then asserts it is a superset of ALL_KEYS
 * except a documented, explicit exclusion set.
 */
@RunWith(RobolectricTestRunner.class)
public class MainActivityReconcileRegistrationTest {

    /**
     * Keys deliberately NOT registered for reconciliation, with the reason.
     *
     * <p>{@code "aa_speed_hack"}: its entire tweak UI block in {@code MainActivity} is currently
     * commented out (dead code), so there is no status view to register. If that block is ever
     * re-enabled, remove this entry — the test will then require it to be registered, making the
     * omission impossible to forget.
     *
     * <p>{@code "aa_patched_apps"}: the "patch apps" flow no longer has a status icon on the main
     * screen — it is driven entirely from the Select-apps screen (AppsList) and applied via the
     * intent path — so there is no main-screen view to reconcile against.
     */
    private static final Set<String> EXPECTED_UNREGISTERED =
            new HashSet<String>(Arrays.asList("aa_speed_hack", "aa_patched_apps"));

    @Test
    public void everyLiveFlagMappedTweakIsRegisteredForReconcile() {
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).create();
        try {
            MainActivity activity = controller.get();
            Set<String> registered = activity.registeredReconcileKeys();

            Set<String> missing = new TreeSet<String>();
            for (String key : TweakRegistry.ALL_KEYS) {
                if (EXPECTED_UNREGISTERED.contains(key)) continue;
                if (!registered.contains(key)) missing.add(key);
            }

            assertTrue("flag-mapped tweaks missing from the reconcile registry "
                            + "(register them in MainActivity.onCreate, or add to "
                            + "EXPECTED_UNREGISTERED with a reason): " + missing,
                    missing.isEmpty());
        } finally {
            controller.close();
        }
    }
}

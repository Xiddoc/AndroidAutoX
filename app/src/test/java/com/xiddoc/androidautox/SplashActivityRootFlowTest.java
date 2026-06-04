package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Looper;

import androidx.fragment.app.DialogFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Robolectric tests for {@link SplashActivity}'s event-driven root-request flow.
 *
 * <p>The libsu boundary is replaced with an injectable {@link SplashActivity.RootRequester}
 * stub so the tests run off-device. The async request runs on a real background thread and
 * posts its result back to the main looper; tests use {@link #settle(SplashActivity)} to
 * spin the main looper until the posted result has been delivered (the activity clears its
 * in-flight flag inside the posted runnable), which avoids racing on the worker thread.
 */
@RunWith(RobolectricTestRunner.class)
public class SplashActivityRootFlowTest {

    /** Records what the activity asked of the libsu boundary, and what to answer with. */
    private static final class StubRequester implements SplashActivity.RootRequester {
        final AtomicInteger acquireCalls = new AtomicInteger();
        final AtomicInteger grantedCalls = new AtomicInteger();
        final AtomicBoolean lastForceReprompt = new AtomicBoolean();
        volatile boolean grantRoot;

        StubRequester(boolean grantRoot) {
            this.grantRoot = grantRoot;
        }

        @Override
        public boolean acquireRoot(boolean forceReprompt) {
            acquireCalls.incrementAndGet();
            lastForceReprompt.set(forceReprompt);
            return grantRoot;
        }

        @Override
        public void onRootGranted() {
            grantedCalls.incrementAndGet();
        }
    }

    /** Drains all work currently posted to the main looper. */
    private static void flushMainLooper() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /**
     * Waits for the in-flight background request to post its result and then delivers it.
     * The request thread clears {@code rootRequestInFlight} inside its posted runnable, so
     * idling the looper until that flips guarantees the result has been applied (no latch
     * race against runOnUiThread()'s enqueue).
     */
    private static void settle(SplashActivity activity) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (activity.rootRequestInFlight && System.currentTimeMillis() < deadline) {
            flushMainLooper();
            Thread.sleep(5);
        }
        flushMainLooper();
    }

    /**
     * Builds a SplashActivity with the stub injected before onResume kicks off the initial
     * request, then settles so the result is delivered. Returns the controller.
     */
    private ActivityController<SplashActivity> startWith(StubRequester stub) throws Exception {
        ActivityController<SplashActivity> controller =
                Robolectric.buildActivity(SplashActivity.class);
        SplashActivity activity = controller.get();
        activity.rootRequester = stub; // inject before resume() fires requestRootAsync
        controller.create().start().resume();
        settle(activity);
        return controller;
    }

    // --- granted result proceeds ------------------------------------------

    @Test
    public void grantedResult_thenProceed_startsMainActivity() throws Exception {
        StubRequester stub = new StubRequester(true);
        ActivityController<SplashActivity> controller = startWith(stub);
        SplashActivity activity = controller.get();

        // User taps Proceed: result already granted -> should navigate to MainActivity.
        activity.proceedIfRooted();
        flushMainLooper();

        ShadowActivity shadow = shadowOf(activity);
        Intent next = shadow.getNextStartedActivity();
        assertNotNull("granted root should start an activity", next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
        assertTrue("activity should finish after proceeding", activity.isFinishing());
        assertEquals("root service should be pre-bound once", 1, stub.grantedCalls.get());
    }

    // --- pending result does NOT proceed (race regression guard) ----------

    @Test
    public void pendingResult_doesNotProceed() throws Exception {
        ActivityController<SplashActivity> controller =
                Robolectric.buildActivity(SplashActivity.class);
        SplashActivity activity = controller.get();
        // A requester that blocks so the result stays pending (null) during the assertions.
        final CountDownLatch block = new CountDownLatch(1);
        activity.rootRequester = new SplashActivity.RootRequester() {
            @Override
            public boolean acquireRoot(boolean forceReprompt) {
                try { block.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                return false;
            }
            @Override
            public void onRootGranted() { }
        };
        controller.create().start().resume();

        // Result still pending: tapping Proceed must WAIT -- not navigate, not show retry.
        assertNull("result must be pending", activity.isDeviceRooted);
        activity.proceedIfRooted();
        flushMainLooper();

        ShadowActivity shadow = shadowOf(activity);
        assertNull("pending root must NOT start MainActivity", shadow.getNextStartedActivity());
        assertFalse("pending root must NOT finish the splash", activity.isFinishing());
        assertNull("no NoRootDialog while pending",
                activity.getSupportFragmentManager().findFragmentByTag("NoRootDialog"));
        block.countDown();
        settle(activity);
    }

    // --- denied result shows retry ----------------------------------------

    @Test
    public void deniedResult_thenProceed_showsRetryDialog() throws Exception {
        StubRequester stub = new StubRequester(false);
        ActivityController<SplashActivity> controller = startWith(stub);
        SplashActivity activity = controller.get();

        activity.proceedIfRooted();
        flushMainLooper();

        ShadowActivity shadow = shadowOf(activity);
        assertNull("denied root must not start MainActivity", shadow.getNextStartedActivity());
        DialogFragment dialog = (DialogFragment)
                activity.getSupportFragmentManager().findFragmentByTag("NoRootDialog");
        assertNotNull("denied root should show NoRootDialog", dialog);
        assertEquals("root must not have been granted", 0, stub.grantedCalls.get());
    }

    // --- retry re-issues a request and forces the shell reset -------------

    @Test
    public void retry_reissuesRequest_withForceReprompt() throws Exception {
        StubRequester stub = new StubRequester(false);
        ActivityController<SplashActivity> controller = startWith(stub);
        SplashActivity activity = controller.get();

        // First proceed -> denied -> retry path available.
        activity.proceedIfRooted();
        flushMainLooper();
        int callsBeforeRetry = stub.acquireCalls.get();
        // Let the retry succeed so we can also confirm it advances.
        stub.grantRoot = true;

        activity.retryRootRequest();
        settle(activity);

        assertEquals("retry should issue exactly one more acquireRoot",
                callsBeforeRetry + 1, stub.acquireCalls.get());
        assertTrue("retry must force a fresh su (shell reset)", stub.lastForceReprompt.get());

        // Retry granted -> should now navigate (userRequestedProceed was already true).
        ShadowActivity shadow = shadowOf(activity);
        Intent next = shadow.getNextStartedActivity();
        assertNotNull("granted retry should advance into MainActivity", next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
    }

    // --- no work posted after destroy -------------------------------------

    @Test
    public void resultAfterDestroy_doesNotNavigate() throws Exception {
        // Block acquireRoot until we have destroyed the activity, then release it so the
        // result is posted to a destroyed activity and must be dropped.
        final CountDownLatch gate = new CountDownLatch(1);
        final CountDownLatch entered = new CountDownLatch(1);
        final StubRequester stub = new StubRequester(true);

        ActivityController<SplashActivity> controller =
                Robolectric.buildActivity(SplashActivity.class);
        final SplashActivity activity = controller.get();
        activity.rootRequester = new SplashActivity.RootRequester() {
            @Override
            public boolean acquireRoot(boolean forceReprompt) {
                entered.countDown();
                try { gate.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                return stub.acquireRoot(forceReprompt);
            }
            @Override
            public void onRootGranted() { stub.onRootGranted(); }
        };
        controller.create().start().resume();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        activity.proceedIfRooted(); // pending -> WAIT (sets userRequestedProceed)
        flushMainLooper();

        // Destroy while the request is still in flight, then release it.
        controller.pause().stop().destroy();
        gate.countDown();
        settle(activity);

        ShadowActivity shadow = shadowOf(activity);
        assertNull("no navigation should be posted after onDestroy",
                shadow.getNextStartedActivity());
    }
}

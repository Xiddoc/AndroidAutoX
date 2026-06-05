package com.xiddoc.androidautox;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.topjohnwu.superuser.ipc.RootService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

/**
 * Plain-JUnit + Mockito tests for {@link RootDb}'s Looper-driven bind path and its
 * inner {@link ServiceConnection}/{@code Runnable} ({@code RootDb$1}/{@code $2}).
 *
 * <p>Avoids the Robolectric runner (whose sandbox classloader drops JaCoCo probes).
 * Instead {@code Looper}/{@code RootService} statics are mocked and {@code new
 * Handler} is intercepted so the posted bind {@code Runnable} runs synchronously on
 * the test thread; the mocked {@code RootService.bind} then drives
 * {@code onServiceConnected}, counting the latch down before {@code get()} awaits.
 * This keeps everything single-threaded so the real {@link RootDb} body executes and
 * its probes register.
 */
public class RootDbBindTest {

    @Before
    public void setUp() throws Exception {
        setSvc(null);
        setAppCtx(mock(android.content.Context.class));
    }

    @After
    public void tearDown() throws Exception {
        setSvc(null);
    }

    private static void setSvc(IPhixitRoot v) throws Exception {
        Field f = RootDb.class.getDeclaredField("svc");
        f.setAccessible(true);
        f.set(null, v);
    }

    private static void setAppCtx(android.content.Context c) throws Exception {
        Field f = RootDb.class.getDeclaredField("appCtx");
        f.setAccessible(true);
        f.set(null, c);
    }

    /**
     * Mocks {@code new Handler(...)} so {@code post(Runnable)} runs the Runnable
     * synchronously on the calling thread (this is the {@code RootDb$2} bind task).
     */
    private static MockedConstruction<Handler> handlerRunsInline() {
        return mockConstruction(Handler.class, (h, ctx) ->
                when(h.post(any(Runnable.class))).thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(0)).run();
                    return true;
                }));
    }

    /** Stubs Looper so the off-main-thread guard passes (myLooper != mainLooper). */
    private static void stubOffMainThread(MockedStatic<Looper> looper) {
        Looper main = mock(Looper.class);
        looper.when(Looper::getMainLooper).thenReturn(main);
        looper.when(Looper::myLooper).thenReturn(null); // null != main -> not main thread
    }

    @Test
    public void get_bindsAndReturnsConnectedService() throws Exception {
        final IPhixitRoot connected = mock(IPhixitRoot.class);
        final IBinder binder = mock(IBinder.class);
        when(binder.queryLocalInterface(anyString())).thenReturn(connected);

        try (MockedStatic<Looper> looper = mockStatic(Looper.class);
             MockedStatic<RootService> rs = mockStatic(RootService.class);
             MockedConstruction<Handler> h = handlerRunsInline()) {
            stubOffMainThread(looper);
            // When bind is invoked, immediately connect the service (counts the latch
            // down before get() awaits), driving RootDb$1.onServiceConnected.
            rs.when(() -> RootService.bind(any(), any(ServiceConnection.class)))
                    .thenAnswer(inv -> {
                        ServiceConnection conn = inv.getArgument(1);
                        conn.onServiceConnected(new ComponentName("p", "c"), binder);
                        return null;
                    });

            assertSame(connected, RootDb.get());
        }
    }

    @Test
    public void onServiceDisconnected_clearsService() throws Exception {
        final IPhixitRoot connected = mock(IPhixitRoot.class);
        final IBinder binder = mock(IBinder.class);
        when(binder.queryLocalInterface(anyString())).thenReturn(connected);
        final ServiceConnection[] captured = new ServiceConnection[1];

        try (MockedStatic<Looper> looper = mockStatic(Looper.class);
             MockedStatic<RootService> rs = mockStatic(RootService.class);
             MockedConstruction<Handler> h = handlerRunsInline()) {
            stubOffMainThread(looper);
            rs.when(() -> RootService.bind(any(), any(ServiceConnection.class)))
                    .thenAnswer(inv -> {
                        ServiceConnection conn = inv.getArgument(1);
                        captured[0] = conn;
                        conn.onServiceConnected(new ComponentName("p", "c"), binder);
                        return null;
                    });

            RootDb.get();
            // Now disconnect -> RootDb$1.onServiceDisconnected sets svc null.
            captured[0].onServiceDisconnected(new ComponentName("p", "c"));
        }

        Field f = RootDb.class.getDeclaredField("svc");
        f.setAccessible(true);
        assertNull(f.get(null));
    }

    @Test
    public void get_timesOut_returnsNullService() throws Exception {
        try (MockedStatic<Looper> looper = mockStatic(Looper.class);
             MockedStatic<RootService> rs = mockStatic(RootService.class);
             MockedConstruction<Handler> h = handlerRunsInline()) {
            stubOffMainThread(looper);
            rs.when(() -> RootService.bind(any(), any(ServiceConnection.class)))
                    .thenAnswer(inv -> null); // posted, but never connects

            // Short timeout via the @VisibleForTesting overload exercises the
            // latch-times-out branch without a 40-second wall-clock wait.
            assertNull(RootDb.get(1));
        }
    }

    @Test
    public void get_interruptedWhileWaiting_setsInterruptFlag() throws Exception {
        final IPhixitRoot[] out = new IPhixitRoot[1];
        final boolean[] interrupted = new boolean[1];
        // Deterministic signal: the bind stub (which runs inline on the worker,
        // immediately before get() calls latch.await()) counts this down. We wait on
        // it instead of a fixed Thread.sleep, so the interrupt is gated on the worker
        // actually reaching the await rather than on a timing guess.
        final CountDownLatch reachedAwait = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            try (MockedStatic<Looper> looper = mockStatic(Looper.class);
                 MockedStatic<RootService> rs = mockStatic(RootService.class);
                 MockedConstruction<Handler> h = handlerRunsInline()) {
                stubOffMainThread(looper);
                rs.when(() -> RootService.bind(any(), any(ServiceConnection.class)))
                        .thenAnswer(inv -> {
                            // Worker is about to await (bind runs inline just before it);
                            // never connect, so get() blocks until interrupted.
                            reachedAwait.countDown();
                            return null;
                        });
                out[0] = RootDb.get(40);
                interrupted[0] = Thread.currentThread().isInterrupted();
            }
        });
        t.start();
        // Wait until the worker has reached the await, then interrupt it. (await() on
        // an already-interrupted thread throws immediately, so this is race-free even
        // if the interrupt lands a hair before the worker enters await().)
        assertTrue(reachedAwait.await(5, java.util.concurrent.TimeUnit.SECONDS));
        t.interrupt();
        t.join(5000);

        assertNull(out[0]);
        assertTrue(interrupted[0]); // catch block re-asserts the interrupt
    }
}

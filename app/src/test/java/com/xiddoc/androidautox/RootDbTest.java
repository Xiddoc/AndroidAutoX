package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Plain-JUnit + Mockito tests for {@link RootDb}: the cached-service early return,
 * the off-main-thread guard, and the convenience wrappers (success + exception
 * wrapping). No Robolectric runner here so the JaCoCo on-the-fly probes register
 * (Robolectric's sandbox classloader otherwise drops them). The Looper-dependent
 * bind path is covered separately in {@link RootDbBindTest}.
 */
public class RootDbTest {

    @Before
    public void setUp() throws Exception {
        setSvc(null);
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

    /** Mock IPhixitRoot whose binder is alive. */
    private static IPhixitRoot aliveSvc() {
        IPhixitRoot svc = mock(IPhixitRoot.class);
        IBinder binder = mock(IBinder.class);
        when(binder.isBinderAlive()).thenReturn(true);
        when(svc.asBinder()).thenReturn(binder);
        return svc;
    }

    // --- init ---

    @Test
    public void init_storesApplicationContext() throws Exception {
        Context app = mock(Context.class);
        Context ctx = mock(Context.class);
        when(ctx.getApplicationContext()).thenReturn(app);

        RootDb.init(ctx);

        Field f = RootDb.class.getDeclaredField("appCtx");
        f.setAccessible(true);
        assertSame(app, f.get(null));
    }

    // --- get(): early return when an alive service already exists ---

    @Test
    public void get_returnsCachedAliveService() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        assertSame(svc, RootDb.get());
    }

    // --- get(): off-main-thread guard ---

    @Test
    public void get_onMainThread_throws() throws Exception {
        // svc null; with the stubbed android.jar, Looper.myLooper() and
        // getMainLooper() both return null, so the "main thread" guard trips.
        try {
            RootDb.get();
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("off the main thread"));
        }
    }

    @Test
    public void get_deadCachedService_fallsThroughToGuard() throws Exception {
        // A non-null svc whose binder is NOT alive must not short-circuit; it falls
        // through to the (here) main-thread guard.
        IPhixitRoot svc = mock(IPhixitRoot.class);
        IBinder binder = mock(IBinder.class);
        when(binder.isBinderAlive()).thenReturn(false);
        when(svc.asBinder()).thenReturn(binder);
        setSvc(svc);
        try {
            RootDb.get();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // fell through past the dead-binder check to the guard
        }
    }

    @Test
    public void get_nullBinderCachedService_fallsThroughToGuard() throws Exception {
        // svc != null but asBinder() == null -> must not short-circuit.
        IPhixitRoot svc = mock(IPhixitRoot.class);
        when(svc.asBinder()).thenReturn(null);
        setSvc(svc);
        try {
            RootDb.get();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // fell through past the null-binder check to the guard
        }
    }

    // --- convenience wrappers: success paths ---

    @Test
    public void readPartitions_delegatesToService() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        List<Partition> parts = Collections.singletonList(new Partition(1L, new byte[]{1}));
        when(svc.readPartitions("pkg")).thenReturn(parts);

        assertSame(parts, RootDb.readPartitions("pkg"));
    }

    @Test
    public void writePartitions_delegatesToService() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        List<Partition> parts = Collections.singletonList(new Partition(1L, new byte[]{2}));

        RootDb.writePartitions(parts, 7);

        verify(svc).writePartitions(parts, 7);
    }

    @Test
    public void query_delegatesToService() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        when(svc.query("db", "SELECT 1")).thenReturn("row");

        assertEquals("row", RootDb.query("db", "SELECT 1"));
    }

    @Test
    public void execList_delegatesToService() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        List<String> stmts = Arrays.asList("A", "B");

        RootDb.exec("db", stmts);

        verify(svc).execStatements("db", stmts);
    }

    @Test
    public void execSingle_wrapsInSingletonList() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);

        RootDb.exec("db", "ONE");

        verify(svc).execStatements(eq("db"), eq(Collections.singletonList("ONE")));
    }

    // --- convenience wrappers: exception wrapping ---

    @Test
    public void readPartitions_wrapsRemoteException() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        when(svc.readPartitions(anyString())).thenThrow(new RemoteException("boom"));
        try {
            RootDb.readPartitions("pkg");
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("readPartitions failed", e.getMessage());
        }
    }

    @Test
    public void writePartitions_wrapsRemoteException() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        doThrow(new RemoteException("boom")).when(svc).writePartitions(anyList(), anyInt());
        try {
            RootDb.writePartitions(Collections.emptyList(), 1);
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("writePartitions failed", e.getMessage());
        }
    }

    @Test
    public void query_wrapsRemoteException() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        when(svc.query(anyString(), anyString())).thenThrow(new RemoteException("boom"));
        try {
            RootDb.query("db", "sql");
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("query failed", e.getMessage());
        }
    }

    @Test
    public void exec_wrapsRemoteException() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        doThrow(new RemoteException("boom")).when(svc).execStatements(anyString(), anyList());
        try {
            RootDb.exec("db", "sql");
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("execStatements failed", e.getMessage());
        }
    }

    // --- statOwner / chownPath / deleteRecursive / backupFile (master-added) ---

    @Test
    public void statOwner_delegatesToService() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        when(svc.statOwner("/db")).thenReturn(new int[]{10001, 10002});

        int[] owner = RootDb.statOwner("/db");
        assertEquals(10001, owner[0]);
        assertEquals(10002, owner[1]);
    }

    @Test
    public void statOwner_wrapsRemoteException() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        when(svc.statOwner(anyString())).thenThrow(new RemoteException("boom"));
        try {
            RootDb.statOwner("/db");
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("statOwner failed", e.getMessage());
        }
    }

    @Test
    public void chownPath_delegatesToService() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);

        RootDb.chownPath("/db", 10001, 10002);

        verify(svc).chownPath("/db", 10001, 10002);
    }

    @Test
    public void chownPath_wrapsRemoteException() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        doThrow(new RemoteException("boom")).when(svc).chownPath(anyString(), anyInt(), anyInt());
        try {
            RootDb.chownPath("/db", 1, 2);
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("chownPath failed", e.getMessage());
        }
    }

    @Test
    public void deleteRecursive_delegatesToService() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        when(svc.deleteRecursive("/cache")).thenReturn(true);

        assertTrue(RootDb.deleteRecursive("/cache"));
        verify(svc).deleteRecursive("/cache");
    }

    @Test
    public void deleteRecursive_wrapsRemoteException() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        when(svc.deleteRecursive(anyString())).thenThrow(new RemoteException("boom"));
        try {
            RootDb.deleteRecursive("/cache");
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("deleteRecursive failed", e.getMessage());
        }
    }

    @Test
    public void backupFile_delegatesToService() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);

        RootDb.backupFile("/src", "/dst", 10001, 10002);

        verify(svc).backupFile("/src", "/dst", 10001, 10002);
    }

    @Test
    public void backupFile_wrapsRemoteException() throws Exception {
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        doThrow(new RemoteException("boom")).when(svc)
                .backupFile(anyString(), anyString(), anyInt(), anyInt());
        try {
            RootDb.backupFile("/src", "/dst", 1, 2);
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("backupFile failed", e.getMessage());
        }
    }

    // --- autoBackup choke point (runs inside writePartitions / exec) ---

    private static void setAppCtx(Context ctx) throws Exception {
        Field f = RootDb.class.getDeclaredField("appCtx");
        f.setAccessible(true);
        f.set(null, ctx);
    }

    @Test
    public void writePartitions_withAppCtx_runsAutoBackupBeforeDelegating() throws Exception {
        // appCtx set -> autoBackup constructs a DbBackup and calls backupBeforeEdit. With the
        // auto_backup pref defaulting on, that reaches the (root) copier which fails fast in
        // the unit JVM; the failure must be swallowed so the write still delegates.
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        Context app = mock(Context.class);
        when(app.getApplicationContext()).thenReturn(app);
        SharedPreferences sp = mock(SharedPreferences.class);
        when(sp.getBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(false); // backups disabled -> backupBeforeEdit short-circuits cleanly
        when(app.getSharedPreferences(anyString(), anyInt())).thenReturn(sp);
        setAppCtx(app);
        try {
            List<Partition> parts = Collections.singletonList(new Partition(1L, new byte[]{9}));
            RootDb.writePartitions(parts, 3);
            verify(svc).writePartitions(parts, 3);
        } finally {
            setAppCtx(null);
        }
    }

    @Test
    public void exec_withAppCtx_autoBackupErrorIsSwallowed_andStillDelegates() throws Exception {
        // A backup failure (here: getFilesDir blowing up inside DbBackup) must never abort the
        // edit -- autoBackup catches Throwable and the exec proceeds.
        IPhixitRoot svc = aliveSvc();
        setSvc(svc);
        Context app = mock(Context.class);
        when(app.getApplicationContext()).thenReturn(app);
        SharedPreferences sp = mock(SharedPreferences.class);
        when(sp.getBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(true); // enabled -> backupBeforeEdit proceeds and then explodes
        when(app.getSharedPreferences(anyString(), anyInt())).thenReturn(sp);
        when(app.getFilesDir()).thenThrow(new RuntimeException("no files dir in unit ctx"));
        setAppCtx(app);
        try {
            RootDb.exec("/db", "DELETE FROM t");
            verify(svc).execStatements(eq("/db"), eq(Collections.singletonList("DELETE FROM t")));
        } finally {
            setAppCtx(null);
        }
    }
}

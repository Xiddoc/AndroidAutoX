package com.xiddoc.androidautox;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.topjohnwu.superuser.ipc.RootService;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * App-side handle to {@link PhixitRootService}. Binds the root service once and
 * keeps it for the process lifetime, exposing the {@link IPhixitRoot} bridge.
 *
 * <p>libsu requires {@link RootService#bind} to be called on the main thread, so
 * {@link #get()} posts the bind there and blocks the (background) caller on a latch
 * until connected. It therefore MUST be called off the main thread.
 */
public final class RootDb {

    private static final String TAG = "AndroidAutoX";
    private static final long BIND_TIMEOUT_SECONDS = 40;

    private static volatile IPhixitRoot svc;
    private static Context appCtx;

    private RootDb() {}

    public static void init(Context ctx) {
        appCtx = ctx.getApplicationContext();
    }

    /** Returns the connected bridge, binding on first use. Background-thread only. */
    public static synchronized IPhixitRoot get() {
        if (svc != null && svc.asBinder() != null && svc.asBinder().isBinderAlive()) {
            return svc;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("RootDb.get() must be called off the main thread");
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                svc = IPhixitRoot.Stub.asInterface(binder);
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                svc = null;
            }
        };
        final Intent intent = new Intent(appCtx, PhixitRootService.class);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                RootService.bind(intent, conn);
            }
        });
        try {
            if (!latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Log.e(TAG, "Timed out binding PhixitRootService");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return svc;
    }

    // --- convenience wrappers (RemoteException -> unchecked) ---

    public static List<Partition> readPartitions(String pkg) {
        try {
            return get().readPartitions(pkg);
        } catch (Exception e) {
            throw new RuntimeException("readPartitions failed", e);
        }
    }

    public static void writePartitions(List<Partition> parts, int servingVersion) {
        try {
            get().writePartitions(parts, servingVersion);
        } catch (Exception e) {
            throw new RuntimeException("writePartitions failed", e);
        }
    }

    public static String query(String dbPath, String sql) {
        try {
            return get().query(dbPath, sql);
        } catch (Exception e) {
            throw new RuntimeException("query failed", e);
        }
    }

    public static void exec(String dbPath, List<String> statements) {
        try {
            get().execStatements(dbPath, statements);
        } catch (Exception e) {
            throw new RuntimeException("execStatements failed", e);
        }
    }

    public static void exec(String dbPath, String statement) {
        exec(dbPath, Arrays.asList(statement));
    }

    /** {st_uid, st_gid} for {@code path} (root-process {@code Os.stat}). */
    public static int[] statOwner(String path) {
        try {
            return get().statOwner(path);
        } catch (Exception e) {
            throw new RuntimeException("statOwner failed", e);
        }
    }

    /** Restores ownership of {@code path} (root-process {@code Os.chown}). */
    public static void chownPath(String path, int uid, int gid) {
        try {
            get().chownPath(path, uid, gid);
        } catch (Exception e) {
            throw new RuntimeException("chownPath failed", e);
        }
    }

    /** Recursively deletes {@code path} in the root process (replaces {@code rm -rf}). */
    public static boolean deleteRecursive(String path) {
        try {
            return get().deleteRecursive(path);
        } catch (Exception e) {
            throw new RuntimeException("deleteRecursive failed", e);
        }
    }

    /** Root-process byte copy of {@code srcPath} -> {@code destPath} (DB backup). */
    public static void backupFile(String srcPath, String destPath) {
        try {
            get().backupFile(srcPath, destPath);
        } catch (Exception e) {
            throw new RuntimeException("backupFile failed", e);
        }
    }
}

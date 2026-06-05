package com.xiddoc.androidautox;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

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
    /** Default bind timeout for the public {@link #get()} entry point. */
    private static final long BIND_TIMEOUT_SECONDS = 40;

    private static volatile IPhixitRoot svc;
    private static Context appCtx;

    private RootDb() {}

    public static void init(Context ctx) {
        appCtx = ctx.getApplicationContext();
    }

    /** Returns the connected bridge, binding on first use. Background-thread only. */
    public static synchronized IPhixitRoot get() {
        return get(BIND_TIMEOUT_SECONDS);
    }

    /**
     * Bind+await implementation behind {@link #get()}. Package-private so off-device
     * tests can pass a short timeout to exercise the timeout branch without a
     * 40-second wall-clock wait, instead of mutating shared static state. The public
     * {@link #get()} always delegates here with {@link #BIND_TIMEOUT_SECONDS}, so its
     * behavior is unchanged.
     */
    @VisibleForTesting
    static synchronized IPhixitRoot get(long timeoutSeconds) {
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
            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.e(TAG, "Timed out binding PhixitRootService");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return svc;
    }

    // --- auto-backup choke point ----------------------------------------------

    /**
     * Central safety-net hook run before EVERY mutating DB call below. Backs up the target
     * DB (gated by the default-on {@code auto_backup_dbs} pref) so phenotype writes, raw
     * phenotype edits, AND CarRemover's {@code carservicedata.db} delete are all covered
     * with no per-call-site code. Read-only methods ({@link #query}, {@link #readPartitions})
     * deliberately do NOT call this. Non-blocking: {@link DbBackup#backupBeforeEdit} swallows
     * and logs any failure, so a backup problem never aborts the edit.
     */
    private static void autoBackup(String dbPath) {
        Context ctx = appCtx;
        if (ctx == null) return; // not initialized (e.g. unit context) -> skip silently
        try {
            new DbBackup(ctx).backupBeforeEdit(dbPath);
        } catch (Throwable t) {
            Log.w(TAG, "auto-backup of " + dbPath + " errored (continuing): " + t);
        }
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
        // Mutating -> back up phenotype.db first (default-on; non-blocking).
        autoBackup(GmsPaths.PHENO_DB);
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
        // Mutating -> back up the target DB first (default-on; non-blocking).
        autoBackup(dbPath);
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

    /**
     * Root-process consistent, restorable backup of the SQLite DB {@code srcPath} ->
     * {@code destPath} (main file + WAL/SHM/journal sidecars, checkpointed and atomically
     * written). When {@code uid >= 0} the written files are chowned to {@code uid}/{@code gid}
     * so the app can later prune/restore them.
     */
    public static void backupFile(String srcPath, String destPath, int uid, int gid) {
        try {
            get().backupFile(srcPath, destPath, uid, gid);
        } catch (Exception e) {
            throw new RuntimeException("backupFile failed", e);
        }
    }
}

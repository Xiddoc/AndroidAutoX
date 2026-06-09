package com.xiddoc.androidautox.autox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;

import com.xiddoc.androidautox.R;

/**
 * Foreground {@link Service} that keeps the AutoX virtual-display session alive while
 * the Android Auto projection is active.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Holds a {@link PowerManager#PARTIAL_WAKE_LOCK} so the CPU does not suspend
 *       while the guest app is rendering on the virtual display. The lock is released
 *       in {@link #onDestroy()}.</li>
 *   <li>Posts a foreground notification (required for {@code foregroundServiceType=
 *       specialUse} on Android 12+) so the system does not kill the service.</li>
 *   <li>Returns {@link #START_NOT_STICKY} from {@link #onStartCommand}: this service is
 *       tied to an active projection surface, so a system-initiated restart with a
 *       {@code null} intent (no surface) must NOT silently re-acquire the 4-hour wake lock.
 *       On such a null-intent restart the service stops itself immediately.</li>
 * </ul>
 *
 * <h2>What this service does NOT do</h2>
 * <ul>
 *   <li>It does not modify the primary display backlight or brightness.</li>
 *   <li>It does not manage the {@link VirtualDisplayController} directly — that is
 *       owned by {@link AutoXScreen} and tied to the Car App surface lifecycle.</li>
 * </ul>
 *
 * <h2>FGS type: {@code specialUse}</h2>
 * <p>The service is declared as {@code foregroundServiceType=specialUse} rather than
 * {@code connectedDevice}. On Android 14 the {@code connectedDevice} type enforces runtime
 * preconditions (e.g. an active Bluetooth/companion-device association) that this rooted
 * projection tool does not satisfy, so {@code startForeground} with that type would throw.
 * {@code specialUse} carries no such precondition and is the documented escape hatch for
 * legitimate use cases that do not fit a predefined type — which fits a root-only Android
 * Auto projection tool. The subtype is declared via a {@code <property>} in the manifest.
 *
 * <h2>Design notes</h2>
 * <p>This class is a framework-entry point and is excluded from the JaCoCo coverage gate.
 * All testable logic (display config, gesture routing) lives in the {@code autox} package
 * pure-logic classes.
 */
public final class AutoXForegroundService extends Service {

    /** Notification channel id for the AutoX foreground service notification. */
    private static final String CHANNEL_ID = "autox_foreground_channel";

    /** Notification id for the foreground service notification. */
    private static final int NOTIFICATION_ID = 1001;

    /** Wake-lock tag; visible in battery/wakelock logs. */
    private static final String WAKE_LOCK_TAG = "AndroidAutoX:AutoXSession";

    /**
     * Bounded wake-lock timeout backstop. The lock is normally released in
     * {@link #onDestroy()} when the projection ends; this timeout guarantees the lock cannot
     * leak forever (e.g. if the process is killed without {@code onDestroy}). 4 hours
     * comfortably exceeds any realistic single projection session.
     */
    private static final long WAKE_LOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L;

    @Nullable
    private PowerManager.WakeLock wakeLock;

    // ------------------------------------------------------------------
    // Service lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        acquireWakeLock();
        // 3-arg startForeground is mandatory on targetSdk 34 when a FGS type is declared.
        startForeground(NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        AutoXLog.d("FgService", "AutoXForegroundService: started, wake lock acquired");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // The service's lifetime is tied to the projection surface (started from
        // AutoXScreen.createDisplay, stopped from releaseDisplay). A null intent means the
        // system restarted us after a kill with no surface to project — re-acquiring the
        // 4-hour wake lock here would leak it. Stop immediately in that case.
        if (intent == null) {
            AutoXLog.d("FgService", "AutoXForegroundService: null-intent restart (no surface) — self-stopping");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        // START_NOT_STICKY: do not auto-recreate this surface-tied service after a kill.
        return START_NOT_STICKY;
    }

    /**
     * This service is not designed for binding from other components; returns {@code null}.
     */
    @Nullable
    @Override
    public IBinder onBind(@Nullable Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
        AutoXLog.d("FgService", "AutoXForegroundService: destroyed, wake lock released");
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Acquires a CPU PARTIAL_WAKE_LOCK to keep the session alive. */
    private void acquireWakeLock() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null) {
            AutoXLog.w("FgService", "AutoXForegroundService: PowerManager unavailable — wake lock skipped");
            return;
        }
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
        // Bounded acquire: released in onDestroy(), with a timeout backstop so the lock can
        // never leak indefinitely if the process dies without onDestroy running.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
    }

    /** Releases the wake lock if it is currently held. */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    /**
     * Builds the foreground notification displayed while the service is active.
     *
     * <p>Creates the notification channel on first call (idempotent on subsequent calls).
     */
    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.autox_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(
                    getString(R.string.autox_notification_channel_desc));
            nm.createNotificationChannel(channel);
        }

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.autox_notification_title))
                .setContentText(getString(R.string.autox_notification_text))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }
}

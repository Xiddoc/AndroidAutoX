package com.xiddoc.androidautox.autox;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight diagnostic logger for the AutoX subsystem.
 *
 * <h2>Why this exists</h2>
 * <p>The AutoX privileged paths (virtual-display creation, LSPosed-gated trusted-display +
 * cross-display input injection, per-display settings and audio routing) are
 * <em>device-validation pending</em> — they cannot be exercised off-device and are the most
 * likely things to fail the first time AutoX is run on a real head unit. When that happens we
 * want a complete, self-contained trace of what AutoX attempted and exactly where it broke,
 * retrievable without a USB cable.
 *
 * <p>{@code AutoXLog} mirrors every entry to two sinks:
 * <ol>
 *   <li><b>logcat</b> (tag {@value #TAG}) — so {@code adb logcat -s AutoX} shows the live
 *       trace during a tethered debug session;</li>
 *   <li>a bounded in-process <b>ring buffer</b> — so the trace can be dumped into the phone UI
 *       ("Copy Logs") and shared even when no computer is attached. Because the AutoX car
 *       services run in the app's default process (no {@code android:process} in the manifest),
 *       this static buffer is shared between {@code AutoXScreen} and {@code MainActivity}.</li>
 * </ol>
 *
 * <p>Each entry is prefixed with a millisecond timestamp (relative to first use), a one-char
 * level, and the caller-supplied {@code area} tag (e.g. {@code "Screen"}, {@code "VDisplay"},
 * {@code "Inject"}) so a single dump reads as a chronological story of the projection session.
 * Throwables are captured with their full stack trace into the buffer (not just the message),
 * which is the whole point — the stack trace is what pinpoints an unverified hidden-API guess.
 *
 * <p>This class is pure JVM logic (the only Android touch-point is the {@link Log} mirror, which
 * is a no-op under unit tests) and is therefore NOT excluded from the coverage gate.
 */
public final class AutoXLog {

    /** logcat tag for every AutoX diagnostic line. Filter with {@code adb logcat -s AutoX}. */
    public static final String TAG = "AutoX";

    /**
     * Maximum number of entries retained in the ring buffer. A projection session emits on the
     * order of a few hundred lines (one per gesture step can add up), so 2000 comfortably holds a
     * whole session while bounding memory. Oldest entries are evicted first.
     */
    static final int MAX_ENTRIES = 2000;

    /** The bounded ring buffer. Guarded by the class monitor (all access is {@code synchronized}). */
    private static final ArrayDeque<String> RING = new ArrayDeque<>();

    /** Reference instant (nanos) used to render relative millisecond timestamps. */
    private static final long START_NANOS = System.nanoTime();

    private AutoXLog() {
    }

    /** Records a DEBUG-level entry (verbose lifecycle / geometry tracing). */
    public static void d(@NonNull String area, @NonNull String msg) {
        log('D', area, msg, null);
    }

    /** Records an INFO-level entry (a milestone: applied / reverted / decision made). */
    public static void i(@NonNull String area, @NonNull String msg) {
        log('I', area, msg, null);
    }

    /** Records a WARN-level entry (a recoverable / best-effort failure that was swallowed). */
    public static void w(@NonNull String area, @NonNull String msg) {
        log('W', area, msg, null);
    }

    /** Records a WARN-level entry with the captured throwable's full stack trace. */
    public static void w(@NonNull String area, @NonNull String msg, @Nullable Throwable t) {
        log('W', area, msg, t);
    }

    /** Records an ERROR-level entry (a hard failure that blocked an AutoX step). */
    public static void e(@NonNull String area, @NonNull String msg) {
        log('E', area, msg, null);
    }

    /** Records an ERROR-level entry with the captured throwable's full stack trace. */
    public static void e(@NonNull String area, @NonNull String msg, @Nullable Throwable t) {
        log('E', area, msg, t);
    }

    /**
     * Core sink: formats the entry, appends it to the ring buffer (evicting the oldest when over
     * {@link #MAX_ENTRIES}), then mirrors it to logcat. Synchronized so concurrent callers (the
     * car surface thread, the foreground service, a gesture dispatch) never corrupt the deque.
     */
    static synchronized void log(char level, @NonNull String area, @NonNull String msg,
                                 @Nullable Throwable t) {
        String entry = format(level, area, msg, t);
        RING.addLast(entry);
        while (RING.size() > MAX_ENTRIES) {
            RING.removeFirst();
        }
        mirror(level, area, msg, t);
    }

    /** Builds the timestamped, leveled, area-tagged line (plus stack trace when {@code t != null}). */
    private static String format(char level, String area, String msg, @Nullable Throwable t) {
        long ms = (System.nanoTime() - START_NANOS) / 1_000_000L;
        String base = String.format(Locale.US, "%8d %c/%s: %s", ms, level, area, msg);
        if (t != null) {
            return base + "\n" + stackTraceOf(t);
        }
        return base;
    }

    /** Renders a throwable's full stack trace to a string for in-buffer capture. */
    private static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /** Mirrors the entry to {@link Log} at the matching level. No-op under unit tests. */
    private static void mirror(char level, String area, String msg, @Nullable Throwable t) {
        String tagged = "[" + area + "] " + msg;
        if (level == 'E') {
            if (t != null) {
                Log.e(TAG, tagged, t);
            } else {
                Log.e(TAG, tagged);
            }
        } else if (level == 'W') {
            if (t != null) {
                Log.w(TAG, tagged, t);
            } else {
                Log.w(TAG, tagged);
            }
        } else if (level == 'I') {
            Log.i(TAG, tagged);
        } else {
            Log.d(TAG, tagged);
        }
    }

    /**
     * Returns the whole ring buffer as a single newline-joined block, prefixed with a header
     * carrying the entry count. Suitable for copying to the clipboard or attaching to a bug report.
     */
    @NonNull
    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("==== AutoX log (").append(RING.size()).append(" entries) ====\n");
        for (String entry : RING) {
            sb.append(entry).append('\n');
        }
        return sb.toString();
    }

    /** Returns an immutable snapshot copy of the current entries (oldest first). */
    @NonNull
    public static synchronized List<String> snapshot() {
        return new ArrayList<>(RING);
    }

    /** Current number of buffered entries (primarily for tests / diagnostics about diagnostics). */
    public static synchronized int size() {
        return RING.size();
    }

    /** Clears the ring buffer (does not affect logcat). */
    public static synchronized void clear() {
        RING.clear();
    }
}

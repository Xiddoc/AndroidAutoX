package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Tests for the pure ring-buffer logic of {@link AutoXLog}. The {@code android.util.Log} mirror is
 * a no-op under the JVM unit-test runtime ({@code returnDefaultValues=true}); these tests exercise
 * every level, the throwable-capture path, and the bounded-eviction policy so the class hits 100%.
 *
 * <p>No Robolectric runner: like {@code RootDbTest}, this keeps the JaCoCo on-the-fly probes
 * registered (Robolectric's sandbox classloader would otherwise drop them).
 */
public class AutoXLogTest {

    @Before
    public void reset() {
        AutoXLog.clear();
    }

    @Test
    public void clear_emptiesBuffer() {
        AutoXLog.i("Area", "something");
        assertTrue(AutoXLog.size() > 0);
        AutoXLog.clear();
        assertEquals(0, AutoXLog.size());
    }

    @Test
    public void eachLevel_recordsOneEntry_andTagsTheMessage() {
        AutoXLog.d("Screen", "debug-line");
        AutoXLog.i("Screen", "info-line");
        AutoXLog.w("Screen", "warn-line");
        AutoXLog.e("Screen", "error-line");
        assertEquals(4, AutoXLog.size());

        List<String> entries = AutoXLog.snapshot();
        assertTrue(entries.get(0).contains("D/Screen: debug-line"));
        assertTrue(entries.get(1).contains("I/Screen: info-line"));
        assertTrue(entries.get(2).contains("W/Screen: warn-line"));
        assertTrue(entries.get(3).contains("E/Screen: error-line"));
    }

    @Test
    public void warnWithThrowable_capturesStackTraceIntoBuffer() {
        AutoXLog.w("Inject", "boom", new IllegalStateException("bad-frame"));
        String only = AutoXLog.snapshot().get(0);
        assertTrue(only.contains("W/Inject: boom"));
        // The full stack trace (exception type + message) is captured into the buffer, not just
        // the message — that is the whole point for debugging device-verify failures.
        assertTrue(only.contains("IllegalStateException"));
        assertTrue(only.contains("bad-frame"));
    }

    @Test
    public void errorWithThrowable_capturesStackTraceIntoBuffer() {
        AutoXLog.e("VDisplay", "create failed", new RuntimeException("no-trusted-flag"));
        String only = AutoXLog.snapshot().get(0);
        assertTrue(only.contains("E/VDisplay: create failed"));
        assertTrue(only.contains("RuntimeException"));
        assertTrue(only.contains("no-trusted-flag"));
    }

    @Test
    public void dump_onEmptyBuffer_reportsZeroEntries() {
        String dump = AutoXLog.dump();
        assertTrue(dump.contains("AutoX log (0 entries)"));
    }

    @Test
    public void dump_includesHeaderCountAndEveryEntry() {
        AutoXLog.i("A", "first");
        AutoXLog.i("B", "second");
        String dump = AutoXLog.dump();
        assertTrue(dump.contains("AutoX log (2 entries)"));
        assertTrue(dump.contains("first"));
        assertTrue(dump.contains("second"));
    }

    @Test
    public void ringBuffer_evictsOldestBeyondMaxEntries() {
        int overflow = AutoXLog.MAX_ENTRIES + 25;
        for (int n = 0; n < overflow; n++) {
            AutoXLog.i("Loop", "entry-" + n);
        }
        // Size is capped at MAX_ENTRIES...
        assertEquals(AutoXLog.MAX_ENTRIES, AutoXLog.size());
        List<String> entries = AutoXLog.snapshot();
        // ...the oldest 25 were evicted (entry-0 is gone) and the newest is retained.
        assertFalse(entries.get(0).contains("entry-0 "));
        assertTrue(entries.get(entries.size() - 1).contains("entry-" + (overflow - 1)));
    }

    @Test
    public void snapshot_isACopy_notTheBackingBuffer() {
        AutoXLog.i("A", "x");
        List<String> snap = AutoXLog.snapshot();
        AutoXLog.i("A", "y");
        // The earlier snapshot must not see the later entry.
        assertEquals(1, snap.size());
        assertEquals(2, AutoXLog.size());
    }

    @Test
    public void privateConstructor_isInvocableForCoverage() throws Exception {
        Constructor<AutoXLog> c = AutoXLog.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(c.getModifiers()));
        c.setAccessible(true);
        c.newInstance();
    }
}

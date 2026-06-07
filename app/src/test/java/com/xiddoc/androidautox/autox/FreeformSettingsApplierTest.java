package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.xiddoc.androidautox.autox.provider.SettingsResult;
import com.xiddoc.androidautox.autox.provider.SystemSettingsProvider;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain-JUnit tests for {@link FreeformSettingsApplier} — 100% line + branch coverage.
 *
 * <p>All framework interaction is faked via {@link FakeProvider}, a simple
 * in-memory {@link SystemSettingsProvider} with configurable failure injection.
 * No Android runtime is needed.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link FreeformSettingsApplier#apply} — all entries succeed; first entry fails
 *       (stops early); second entry fails.</li>
 *   <li>{@link FreeformSettingsApplier#revert} — all entries succeed; first entry fails.</li>
 *   <li>{@link FreeformSettingsApplier.ApplyResult} value object (ok, failed, toString).</li>
 *   <li>{@code apply}/{@code revert} null-argument validation.</li>
 *   <li>End-to-end: apply then revert restores prior values.</li>
 * </ul>
 */
public class FreeformSettingsApplierTest {

    // -----------------------------------------------------------------------
    // Fake SystemSettingsProvider
    // -----------------------------------------------------------------------

    /**
     * In-memory fake {@link SystemSettingsProvider}. Supports:
     * <ul>
     *   <li>Writing and reading integer values (per-key map).</li>
     *   <li>Injecting a {@link SettingsResult#denied()} or
     *       {@link SettingsResult#notFound()} on a specific key write.</li>
     * </ul>
     * Reads always return OK if the key exists, NOT_FOUND otherwise.
     * Writes succeed unless {@code failOnPut} has been set for that key.
     */
    private static final class FakeProvider implements SystemSettingsProvider {

        private final Map<String, Integer> globalStore = new HashMap<>();
        private final Map<String, SettingsResult> putFailures = new HashMap<>();

        void setGlobal(String key, int value) {
            globalStore.put(key, value);
        }

        /** Inject a write failure for the given key. */
        void failPutFor(String key, SettingsResult result) {
            putFailures.put(key, result);
        }

        @Override
        public SettingsResult putGlobalInt(String key, int value) {
            if (putFailures.containsKey(key)) {
                return putFailures.get(key);
            }
            globalStore.put(key, value);
            return SettingsResult.ok();
        }

        @Override
        public SettingsResult getGlobalInt(String key) {
            if (globalStore.containsKey(key)) {
                return SettingsResult.ok(globalStore.get(key));
            }
            return SettingsResult.notFound();
        }

        @Override
        public SettingsResult putSecureInt(String key, int value) {
            return SettingsResult.denied(); // not used in WS3
        }

        @Override
        public SettingsResult getSecureInt(String key) {
            return SettingsResult.notFound(); // not used in WS3
        }

        Integer get(String key) {
            return globalStore.get(key);
        }
    }

    // -----------------------------------------------------------------------
    // ApplyResult value object
    // -----------------------------------------------------------------------

    @Test
    public void applyResult_ok_hasSuccessTrue() {
        FreeformSettingsApplier.ApplyResult r = FreeformSettingsApplier.ApplyResult.ok();
        assertTrue(r.success);
        assertNull(r.failedKey);
        assertNull(r.failedResult);
        assertTrue(r.toString().contains("true"));
    }

    @Test
    public void applyResult_failed_hasSuccessFalseAndKeyAndResult() {
        SettingsResult denied = SettingsResult.denied();
        FreeformSettingsApplier.ApplyResult r =
                FreeformSettingsApplier.ApplyResult.failed("my_key", denied);
        assertFalse(r.success);
        assertEquals("my_key", r.failedKey);
        assertEquals(denied, r.failedResult);
        String s = r.toString();
        assertTrue(s.contains("false"));
        assertTrue(s.contains("my_key"));
    }

    // -----------------------------------------------------------------------
    // apply — all succeed
    // -----------------------------------------------------------------------

    @Test
    public void apply_allSucceed_returnsOk() {
        FakeProvider provider = new FakeProvider();
        List<SecureSettingsSpec.Entry> entries = SecureSettingsSpec.applyList(null, null);

        FreeformSettingsApplier.ApplyResult result =
                FreeformSettingsApplier.apply(entries, provider);

        assertTrue(result.success);
        // Verify values were written
        assertEquals((Integer) SecureSettingsSpec.ENABLED_VALUE,
                provider.get(SecureSettingsSpec.KEY_FORCE_RESIZABLE));
        assertEquals((Integer) SecureSettingsSpec.ENABLED_VALUE,
                provider.get(SecureSettingsSpec.KEY_ENABLE_FREEFORM));
    }

    // -----------------------------------------------------------------------
    // apply — first entry fails (early exit)
    // -----------------------------------------------------------------------

    @Test
    public void apply_firstEntryFails_stopsEarlyAndReturnsFailed() {
        FakeProvider provider = new FakeProvider();
        provider.failPutFor(SecureSettingsSpec.KEY_FORCE_RESIZABLE, SettingsResult.denied());

        List<SecureSettingsSpec.Entry> entries = SecureSettingsSpec.applyList(null, null);
        FreeformSettingsApplier.ApplyResult result =
                FreeformSettingsApplier.apply(entries, provider);

        assertFalse(result.success);
        assertEquals(SecureSettingsSpec.KEY_FORCE_RESIZABLE, result.failedKey);
        assertEquals(SettingsResult.Status.DENIED, result.failedResult.status);
        // Second key must NOT have been written (early exit)
        assertNull(provider.get(SecureSettingsSpec.KEY_ENABLE_FREEFORM));
    }

    // -----------------------------------------------------------------------
    // apply — second entry fails
    // -----------------------------------------------------------------------

    @Test
    public void apply_secondEntryFails_firstWrittenReturnsFailed() {
        FakeProvider provider = new FakeProvider();
        provider.failPutFor(SecureSettingsSpec.KEY_ENABLE_FREEFORM, SettingsResult.denied());

        List<SecureSettingsSpec.Entry> entries = SecureSettingsSpec.applyList(null, null);
        FreeformSettingsApplier.ApplyResult result =
                FreeformSettingsApplier.apply(entries, provider);

        assertFalse(result.success);
        assertEquals(SecureSettingsSpec.KEY_ENABLE_FREEFORM, result.failedKey);
        // First key was written successfully
        assertEquals((Integer) SecureSettingsSpec.ENABLED_VALUE,
                provider.get(SecureSettingsSpec.KEY_FORCE_RESIZABLE));
    }

    // -----------------------------------------------------------------------
    // apply — empty list succeeds
    // -----------------------------------------------------------------------

    @Test
    public void apply_emptyList_returnsOk() {
        FakeProvider provider = new FakeProvider();
        FreeformSettingsApplier.ApplyResult result =
                FreeformSettingsApplier.apply(Collections.emptyList(), provider);
        assertTrue(result.success);
    }

    // -----------------------------------------------------------------------
    // revert — all succeed
    // -----------------------------------------------------------------------

    @Test
    public void revert_allSucceed_restoresPriorValues() {
        FakeProvider provider = new FakeProvider();
        // Simulate: keys had prior values 0 and 0; now revert them.
        List<SecureSettingsSpec.Entry> revertEntries =
                SecureSettingsSpec.revertList(5, 3);

        FreeformSettingsApplier.ApplyResult result =
                FreeformSettingsApplier.revert(revertEntries, provider);

        assertTrue(result.success);
        // Revert order: freeform first (prior=3), then force-resizable (prior=5)
        assertEquals((Integer) 3, provider.get(SecureSettingsSpec.KEY_ENABLE_FREEFORM));
        assertEquals((Integer) 5, provider.get(SecureSettingsSpec.KEY_FORCE_RESIZABLE));
    }

    @Test
    public void revert_absentKeys_writesDefaultZero() {
        FakeProvider provider = new FakeProvider();
        List<SecureSettingsSpec.Entry> revertEntries =
                SecureSettingsSpec.revertList(null, null);

        FreeformSettingsApplier.ApplyResult result =
                FreeformSettingsApplier.revert(revertEntries, provider);

        assertTrue(result.success);
        assertEquals((Integer) SecureSettingsSpec.DEFAULT_REVERT_VALUE,
                provider.get(SecureSettingsSpec.KEY_ENABLE_FREEFORM));
        assertEquals((Integer) SecureSettingsSpec.DEFAULT_REVERT_VALUE,
                provider.get(SecureSettingsSpec.KEY_FORCE_RESIZABLE));
    }

    // -----------------------------------------------------------------------
    // revert — first entry fails
    // -----------------------------------------------------------------------

    @Test
    public void revert_firstEntryFails_stopsAndReturnsFailed() {
        FakeProvider provider = new FakeProvider();
        // Revert order: freeform first
        provider.failPutFor(SecureSettingsSpec.KEY_ENABLE_FREEFORM, SettingsResult.denied());

        List<SecureSettingsSpec.Entry> revertEntries =
                SecureSettingsSpec.revertList(5, 3);
        FreeformSettingsApplier.ApplyResult result =
                FreeformSettingsApplier.revert(revertEntries, provider);

        assertFalse(result.success);
        assertEquals(SecureSettingsSpec.KEY_ENABLE_FREEFORM, result.failedKey);
        // force-resizable must not have been written (early exit)
        assertNull(provider.get(SecureSettingsSpec.KEY_FORCE_RESIZABLE));
    }

    // -----------------------------------------------------------------------
    // End-to-end: apply then revert restores original values
    // -----------------------------------------------------------------------

    @Test
    public void endToEnd_applyThenRevert_restoresOriginalValues() {
        FakeProvider provider = new FakeProvider();
        // Seed original values
        provider.setGlobal(SecureSettingsSpec.KEY_FORCE_RESIZABLE, 0);
        provider.setGlobal(SecureSettingsSpec.KEY_ENABLE_FREEFORM, 0);

        // Read priors
        Integer priorForce = provider.getGlobalInt(SecureSettingsSpec.KEY_FORCE_RESIZABLE).value;
        Integer priorFreeform = provider.getGlobalInt(SecureSettingsSpec.KEY_ENABLE_FREEFORM).value;

        // Apply
        List<SecureSettingsSpec.Entry> applyEntries =
                SecureSettingsSpec.applyList(priorForce, priorFreeform);
        FreeformSettingsApplier.ApplyResult applyResult =
                FreeformSettingsApplier.apply(applyEntries, provider);
        assertTrue(applyResult.success);
        assertEquals((Integer) 1, provider.get(SecureSettingsSpec.KEY_FORCE_RESIZABLE));
        assertEquals((Integer) 1, provider.get(SecureSettingsSpec.KEY_ENABLE_FREEFORM));

        // Revert
        List<SecureSettingsSpec.Entry> revertEntries =
                SecureSettingsSpec.revertList(priorForce, priorFreeform);
        FreeformSettingsApplier.ApplyResult revertResult =
                FreeformSettingsApplier.revert(revertEntries, provider);
        assertTrue(revertResult.success);
        assertEquals((Integer) 0, provider.get(SecureSettingsSpec.KEY_FORCE_RESIZABLE));
        assertEquals((Integer) 0, provider.get(SecureSettingsSpec.KEY_ENABLE_FREEFORM));
    }

    @Test
    public void endToEnd_applyWithAbsentKeys_revertWritesZero() {
        FakeProvider provider = new FakeProvider();
        // Keys do not exist initially

        // Apply (both absent)
        List<SecureSettingsSpec.Entry> applyEntries =
                SecureSettingsSpec.applyList(null, null);
        FreeformSettingsApplier.apply(applyEntries, provider);

        // Revert (both absent → write 0)
        List<SecureSettingsSpec.Entry> revertEntries =
                SecureSettingsSpec.revertList(null, null);
        FreeformSettingsApplier.ApplyResult revertResult =
                FreeformSettingsApplier.revert(revertEntries, provider);
        assertTrue(revertResult.success);
        assertEquals((Integer) 0, provider.get(SecureSettingsSpec.KEY_FORCE_RESIZABLE));
        assertEquals((Integer) 0, provider.get(SecureSettingsSpec.KEY_ENABLE_FREEFORM));
    }

    // -----------------------------------------------------------------------
    // Null-argument validation
    // -----------------------------------------------------------------------

    @Test
    public void apply_nullEntries_throws() {
        FakeProvider provider = new FakeProvider();
        try {
            FreeformSettingsApplier.apply(null, provider);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("entries"));
        }
    }

    @Test
    public void apply_nullProvider_throws() {
        List<SecureSettingsSpec.Entry> entries = SecureSettingsSpec.applyList(null, null);
        try {
            FreeformSettingsApplier.apply(entries, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("provider"));
        }
    }

    @Test
    public void revert_nullEntries_throws() {
        FakeProvider provider = new FakeProvider();
        try {
            FreeformSettingsApplier.revert(null, provider);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("entries"));
        }
    }

    @Test
    public void revert_nullProvider_throws() {
        List<SecureSettingsSpec.Entry> entries = SecureSettingsSpec.revertList(null, null);
        try {
            FreeformSettingsApplier.revert(entries, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("provider"));
        }
    }
}

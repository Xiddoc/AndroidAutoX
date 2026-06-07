package com.xiddoc.androidautox.autox.ime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.xiddoc.androidautox.autox.provider.SettingsResult;
import com.xiddoc.androidautox.autox.provider.SystemSettingsProvider;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Plain-JUnit tests for {@link ImeDisplaySettingsApplier}.
 *
 * <p>A {@link FakeSystemSettingsProvider} is used so no Android framework is required.
 * Covers: null-spec guard, null-provider guard, apply with all three prior states
 * (unset / disabled / enabled), revert with all three prior states (unset → writes
 * DISABLED; disabled → restores 0; enabled → restores 1), partial failure (one write
 * denied → successCount &lt; totalCount), and the toString of ApplyResult.
 */
public class ImeDisplaySettingsApplierTest {

    // -------------------------------------------------------------------------
    // Fake SystemSettingsProvider
    // -------------------------------------------------------------------------

    /**
     * In-memory fake SystemSettingsProvider for testing.
     * Supports an optional set of keys that will return DENIED.
     */
    private static final class FakeSystemSettingsProvider implements SystemSettingsProvider {

        /** Stored Secure integer values keyed by setting key. */
        final Map<String, Integer> secureStore = new HashMap<>();

        /** Keys that will cause a DENIED result on put. */
        final java.util.Set<String> deniedPutKeys = new java.util.HashSet<>();

        /** Keys that will cause a DENIED result on get. */
        final java.util.Set<String> deniedGetKeys = new java.util.HashSet<>();

        @Override
        public SettingsResult putGlobalInt(String key, int value) {
            secureStore.put(key, value);
            return SettingsResult.ok();
        }

        @Override
        public SettingsResult getGlobalInt(String key) {
            Integer val = secureStore.get(key);
            return val != null ? SettingsResult.ok(val) : SettingsResult.notFound();
        }

        @Override
        public SettingsResult putSecureInt(String key, int value) {
            if (deniedPutKeys.contains(key)) {
                return SettingsResult.denied();
            }
            secureStore.put(key, value);
            return SettingsResult.ok();
        }

        @Override
        public SettingsResult getSecureInt(String key) {
            if (deniedGetKeys.contains(key)) {
                return SettingsResult.denied();
            }
            Integer val = secureStore.get(key);
            return val != null ? SettingsResult.ok(val) : SettingsResult.notFound();
        }
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private static final int DISPLAY_ID = 5;
    private FakeSystemSettingsProvider fake;
    private ImeDisplaySettingsApplier applier;

    @Before
    public void setUp() {
        fake = new FakeSystemSettingsProvider();
        applier = new ImeDisplaySettingsApplier(fake);
    }

    // -------------------------------------------------------------------------
    // Constructor guard
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullProvider_throws() {
        new ImeDisplaySettingsApplier(null);
    }

    // -------------------------------------------------------------------------
    // readPriorAndApply — null guard
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void readPriorAndApply_nullSpec_throws() {
        applier.readPriorAndApply(null);
    }

    // -------------------------------------------------------------------------
    // readPriorAndApply — both keys absent (priors = UNSET)
    // -------------------------------------------------------------------------

    @Test
    public void readPriorAndApply_keysAbsent_writesEnabledForBoth() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        ImeDisplaySettingsApplier.ApplyResult result = applier.readPriorAndApply(spec);

        assertTrue(result.allSucceeded);
        assertEquals(2, result.successCount);
        assertEquals(2, result.totalCount);

        // Both keys must now be VALUE_ENABLED in the store.
        Integer decorVal = fake.secureStore.get(spec.decorKey());
        Integer imeVal = fake.secureStore.get(spec.imeKey());
        assertNotNull(decorVal);
        assertNotNull(imeVal);
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED, (int) decorVal);
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED, (int) imeVal);
    }

    @Test
    public void readPriorAndApply_keysAbsent_resultSpecPriorsAreUnset() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        ImeDisplaySettingsApplier.ApplyResult result = applier.readPriorAndApply(spec);

        ImeDisplaySettingsSpec resultSpec = result.resultSpec;
        assertTrue(resultSpec.getApplyList().get(0).wasUnset()); // decor was unset
        assertTrue(resultSpec.getApplyList().get(1).wasUnset()); // ime was unset
    }

    // -------------------------------------------------------------------------
    // readPriorAndApply — both keys present and disabled (priors = DISABLED)
    // -------------------------------------------------------------------------

    @Test
    public void readPriorAndApply_keysDisabled_writesEnabledAndRecordsDisabled() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        // Pre-populate both keys as disabled.
        fake.secureStore.put(spec.decorKey(), ImeDisplaySettingsSpec.VALUE_DISABLED);
        fake.secureStore.put(spec.imeKey(), ImeDisplaySettingsSpec.VALUE_DISABLED);

        ImeDisplaySettingsApplier.ApplyResult result = applier.readPriorAndApply(spec);

        assertTrue(result.allSucceeded);
        // Priors should be DISABLED.
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                result.resultSpec.getApplyList().get(0).priorValue);
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                result.resultSpec.getApplyList().get(1).priorValue);
        // Store should now have ENABLED.
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED,
                (int) fake.secureStore.get(spec.decorKey()));
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED,
                (int) fake.secureStore.get(spec.imeKey()));
    }

    // -------------------------------------------------------------------------
    // readPriorAndApply — both keys already enabled (priors = ENABLED)
    // -------------------------------------------------------------------------

    @Test
    public void readPriorAndApply_keysAlreadyEnabled_recordsEnabledPrior() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        fake.secureStore.put(spec.decorKey(), ImeDisplaySettingsSpec.VALUE_ENABLED);
        fake.secureStore.put(spec.imeKey(), ImeDisplaySettingsSpec.VALUE_ENABLED);

        ImeDisplaySettingsApplier.ApplyResult result = applier.readPriorAndApply(spec);

        assertTrue(result.allSucceeded);
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED,
                result.resultSpec.getApplyList().get(0).priorValue);
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED,
                result.resultSpec.getApplyList().get(1).priorValue);
    }

    // -------------------------------------------------------------------------
    // readPriorAndApply — denied read → treats prior as DISABLED
    // -------------------------------------------------------------------------

    @Test
    public void readPriorAndApply_readDenied_treatedAsDisabledPrior() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        // Make reads denied.
        fake.deniedGetKeys.add(spec.decorKey());
        fake.deniedGetKeys.add(spec.imeKey());

        ImeDisplaySettingsApplier.ApplyResult result = applier.readPriorAndApply(spec);

        // Both priors should default to DISABLED when read is denied.
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                result.resultSpec.getApplyList().get(0).priorValue);
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                result.resultSpec.getApplyList().get(1).priorValue);
        // Write should still have succeeded (read-deny doesn't block write).
        assertTrue(result.allSucceeded);
    }

    // -------------------------------------------------------------------------
    // readPriorAndApply — write denied → partial failure
    // -------------------------------------------------------------------------

    @Test
    public void readPriorAndApply_oneWriteDenied_partialSuccess() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        // Deny write for decor key only.
        fake.deniedPutKeys.add(spec.decorKey());

        ImeDisplaySettingsApplier.ApplyResult result = applier.readPriorAndApply(spec);

        assertFalse(result.allSucceeded);
        assertEquals(1, result.successCount);
        assertEquals(2, result.totalCount);
    }

    @Test
    public void readPriorAndApply_bothWritesDenied_zeroSuccess() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        fake.deniedPutKeys.add(spec.decorKey());
        fake.deniedPutKeys.add(spec.imeKey());

        ImeDisplaySettingsApplier.ApplyResult result = applier.readPriorAndApply(spec);

        assertFalse(result.allSucceeded);
        assertEquals(0, result.successCount);
        assertEquals(2, result.totalCount);
    }

    // -------------------------------------------------------------------------
    // revert — null guard
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void revert_nullSpec_throws() {
        applier.revert(null);
    }

    // -------------------------------------------------------------------------
    // revert — prior was UNSET → writes DISABLED
    // -------------------------------------------------------------------------

    @Test
    public void revert_priorsUnset_writesDisabledForBoth() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        // Apply first (priors = UNSET).
        ImeDisplaySettingsApplier.ApplyResult applyResult = applier.readPriorAndApply(spec);
        ImeDisplaySettingsSpec specWithPriors = applyResult.resultSpec;

        // Now revert.
        ImeDisplaySettingsApplier.ApplyResult revertResult = applier.revert(specWithPriors);

        assertTrue(revertResult.allSucceeded);
        assertEquals(2, revertResult.successCount);
        // Both keys should be VALUE_DISABLED after revert (was-unset → writes 0).
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                (int) fake.secureStore.get(spec.decorKey()));
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                (int) fake.secureStore.get(spec.imeKey()));
    }

    // -------------------------------------------------------------------------
    // revert — prior was DISABLED → restores 0
    // -------------------------------------------------------------------------

    @Test
    public void revert_priorsDisabled_restoresZero() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        fake.secureStore.put(spec.decorKey(), ImeDisplaySettingsSpec.VALUE_DISABLED);
        fake.secureStore.put(spec.imeKey(), ImeDisplaySettingsSpec.VALUE_DISABLED);

        ImeDisplaySettingsApplier.ApplyResult applyResult = applier.readPriorAndApply(spec);
        ImeDisplaySettingsApplier.ApplyResult revertResult =
                applier.revert(applyResult.resultSpec);

        assertTrue(revertResult.allSucceeded);
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                (int) fake.secureStore.get(spec.decorKey()));
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                (int) fake.secureStore.get(spec.imeKey()));
    }

    // -------------------------------------------------------------------------
    // revert — prior was ENABLED → restores 1
    // -------------------------------------------------------------------------

    @Test
    public void revert_priorsEnabled_restoresOne() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        fake.secureStore.put(spec.decorKey(), ImeDisplaySettingsSpec.VALUE_ENABLED);
        fake.secureStore.put(spec.imeKey(), ImeDisplaySettingsSpec.VALUE_ENABLED);

        ImeDisplaySettingsApplier.ApplyResult applyResult = applier.readPriorAndApply(spec);
        ImeDisplaySettingsApplier.ApplyResult revertResult =
                applier.revert(applyResult.resultSpec);

        assertTrue(revertResult.allSucceeded);
        // Should be restored to VALUE_ENABLED (1).
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED,
                (int) fake.secureStore.get(spec.decorKey()));
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED,
                (int) fake.secureStore.get(spec.imeKey()));
    }

    // -------------------------------------------------------------------------
    // revert — prior mixed (decor disabled, ime unset)
    // -------------------------------------------------------------------------

    @Test
    public void revert_mixed_priorsPreservedCorrectly() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        // decor was disabled, ime was absent (unset).
        fake.secureStore.put(spec.decorKey(), ImeDisplaySettingsSpec.VALUE_DISABLED);
        // ime key is absent → readPrior returns UNSET.

        ImeDisplaySettingsApplier.ApplyResult applyResult = applier.readPriorAndApply(spec);
        // Confirm priors captured correctly.
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                applyResult.resultSpec.getApplyList().get(0).priorValue);
        assertTrue(applyResult.resultSpec.getApplyList().get(1).wasUnset());

        // Revert.
        ImeDisplaySettingsApplier.ApplyResult revertResult =
                applier.revert(applyResult.resultSpec);

        assertTrue(revertResult.allSucceeded);
        // Decor should be restored to VALUE_DISABLED.
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                (int) fake.secureStore.get(spec.decorKey()));
        // IME was-unset → should be VALUE_DISABLED (0).
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                (int) fake.secureStore.get(spec.imeKey()));
    }

    // -------------------------------------------------------------------------
    // revert — write denied → partial failure
    // -------------------------------------------------------------------------

    @Test
    public void revert_oneWriteDenied_partialFailure() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        ImeDisplaySettingsApplier.ApplyResult applyResult = applier.readPriorAndApply(spec);
        // Now deny the IME revert write.
        fake.deniedPutKeys.add(spec.imeKey());

        ImeDisplaySettingsApplier.ApplyResult revertResult =
                applier.revert(applyResult.resultSpec);

        assertFalse(revertResult.allSucceeded);
        assertEquals(1, revertResult.successCount);
        assertEquals(2, revertResult.totalCount);
    }

    // -------------------------------------------------------------------------
    // ApplyResult.toString
    // -------------------------------------------------------------------------

    @Test
    public void applyResult_toString_containsCountsAndFlag() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        ImeDisplaySettingsApplier.ApplyResult result = applier.readPriorAndApply(spec);
        String s = result.toString();
        assertNotNull(s);
        assertTrue(s.contains("success="));
        assertTrue(s.contains("allSucceeded=true"));
    }

    // -------------------------------------------------------------------------
    // Apply ordering: decors written before IME (apply index 0 = decor)
    // -------------------------------------------------------------------------

    @Test
    public void readPriorAndApply_writeOrderIsDecorBeforeIme() {
        // We use a recording fake that tracks write order.
        final java.util.List<String> writeOrder = new java.util.ArrayList<>();
        SystemSettingsProvider recording = new SystemSettingsProvider() {
            @Override
            public SettingsResult putGlobalInt(String key, int value) {
                return SettingsResult.ok();
            }

            @Override
            public SettingsResult getGlobalInt(String key) {
                return SettingsResult.notFound();
            }

            @Override
            public SettingsResult putSecureInt(String key, int value) {
                writeOrder.add(key);
                return SettingsResult.ok();
            }

            @Override
            public SettingsResult getSecureInt(String key) {
                return SettingsResult.notFound();
            }
        };

        ImeDisplaySettingsApplier orderedApplier = new ImeDisplaySettingsApplier(recording);
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        orderedApplier.readPriorAndApply(spec);

        assertEquals(2, writeOrder.size());
        assertTrue(writeOrder.get(0).contains("system_decors"));
        assertTrue(writeOrder.get(1).contains("should_show_ime"));
    }

    // -------------------------------------------------------------------------
    // Revert ordering: IME before decor (reverse of apply)
    // -------------------------------------------------------------------------

    @Test
    public void revert_writeOrderIsImeBeforeDecor() {
        // Apply first.
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(DISPLAY_ID);
        ImeDisplaySettingsApplier.ApplyResult applyResult = applier.readPriorAndApply(spec);

        // Now use a recording fake for the revert.
        final java.util.List<String> writeOrder = new java.util.ArrayList<>();
        SystemSettingsProvider recording = new SystemSettingsProvider() {
            @Override
            public SettingsResult putGlobalInt(String key, int value) {
                return SettingsResult.ok();
            }

            @Override
            public SettingsResult getGlobalInt(String key) {
                return SettingsResult.notFound();
            }

            @Override
            public SettingsResult putSecureInt(String key, int value) {
                writeOrder.add(key);
                return SettingsResult.ok();
            }

            @Override
            public SettingsResult getSecureInt(String key) {
                return SettingsResult.notFound();
            }
        };

        ImeDisplaySettingsApplier revertApplier = new ImeDisplaySettingsApplier(recording);
        revertApplier.revert(applyResult.resultSpec);

        assertEquals(2, writeOrder.size());
        assertTrue(writeOrder.get(0).contains("should_show_ime")); // IME first
        assertTrue(writeOrder.get(1).contains("system_decors"));    // decor second
    }
}

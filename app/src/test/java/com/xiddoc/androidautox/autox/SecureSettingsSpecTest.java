package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.List;

/**
 * Plain-JUnit tests for {@link SecureSettingsSpec} — 100% line + branch coverage.
 *
 * <p>Covers:
 * <ul>
 *   <li>Public constants (keys, enabled value, default revert value).</li>
 *   <li>{@link SecureSettingsSpec.Entry#forAbsentKey} — valid + validation failures
 *       (null key, blank key, negative enabledValue).</li>
 *   <li>{@link SecureSettingsSpec.Entry#forExistingKey} — valid + validation failures.</li>
 *   <li>{@link SecureSettingsSpec.Entry#revertValue()} for both
 *       {@link SecureSettingsSpec.RevertStrategy#WRITE_DEFAULT} and
 *       {@link SecureSettingsSpec.RevertStrategy#RESTORE_PRIOR} branches.</li>
 *   <li>{@link SecureSettingsSpec#applyList} — both keys absent, both present, mixed.</li>
 *   <li>{@link SecureSettingsSpec#revertList} — ordering, absent vs present.</li>
 *   <li>{@link SecureSettingsSpec#managedKeys()} — content and order.</li>
 *   <li>{@link SecureSettingsSpec.RevertStrategy} enum coverage.</li>
 * </ul>
 */
public class SecureSettingsSpecTest {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    @Test
    public void constants_haveExpectedValues() {
        assertEquals("force_resizable_activities", SecureSettingsSpec.KEY_FORCE_RESIZABLE);
        assertEquals("enable_freeform_support", SecureSettingsSpec.KEY_ENABLE_FREEFORM);
        assertEquals(1, SecureSettingsSpec.ENABLED_VALUE);
        assertEquals(0, SecureSettingsSpec.DEFAULT_REVERT_VALUE);
    }

    // -----------------------------------------------------------------------
    // RevertStrategy enum
    // -----------------------------------------------------------------------

    @Test
    public void revertStrategyEnum_values() {
        assertEquals(2, SecureSettingsSpec.RevertStrategy.values().length);
        assertNotNull(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR);
        assertNotNull(SecureSettingsSpec.RevertStrategy.WRITE_DEFAULT);
        assertEquals(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR,
                SecureSettingsSpec.RevertStrategy.valueOf("RESTORE_PRIOR"));
        assertEquals(SecureSettingsSpec.RevertStrategy.WRITE_DEFAULT,
                SecureSettingsSpec.RevertStrategy.valueOf("WRITE_DEFAULT"));
    }

    // -----------------------------------------------------------------------
    // Entry.forAbsentKey — happy path
    // -----------------------------------------------------------------------

    @Test
    public void forAbsentKey_happyPath_returnsCorrectEntry() {
        SecureSettingsSpec.Entry e = SecureSettingsSpec.Entry.forAbsentKey("my_key", 1);
        assertEquals("my_key", e.key());
        assertEquals(1, e.enabledValue());
        assertEquals(SecureSettingsSpec.RevertStrategy.WRITE_DEFAULT, e.revertStrategy());
        assertEquals(0, e.priorValue()); // stored as 0 for absent-key entries
        assertEquals(SecureSettingsSpec.DEFAULT_REVERT_VALUE, e.revertValue());
    }

    @Test
    public void forAbsentKey_zeroEnabledValue_isAllowed() {
        // enabledValue >= 0 is the contract; 0 is a valid "disabled" sentinel write.
        SecureSettingsSpec.Entry e = SecureSettingsSpec.Entry.forAbsentKey("k", 0);
        assertEquals(0, e.enabledValue());
        assertEquals(SecureSettingsSpec.RevertStrategy.WRITE_DEFAULT, e.revertStrategy());
    }

    // -----------------------------------------------------------------------
    // Entry.forAbsentKey — validation failures
    // -----------------------------------------------------------------------

    @Test
    public void forAbsentKey_nullKey_throws() {
        try {
            SecureSettingsSpec.Entry.forAbsentKey(null, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void forAbsentKey_blankKey_throws() {
        try {
            SecureSettingsSpec.Entry.forAbsentKey("   ", 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void forAbsentKey_emptyKey_throws() {
        try {
            SecureSettingsSpec.Entry.forAbsentKey("", 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void forAbsentKey_negativeEnabledValue_throws() {
        try {
            SecureSettingsSpec.Entry.forAbsentKey("k", -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // Entry.forExistingKey — happy path
    // -----------------------------------------------------------------------

    @Test
    public void forExistingKey_happyPath_returnsCorrectEntry() {
        SecureSettingsSpec.Entry e = SecureSettingsSpec.Entry.forExistingKey("my_key", 1, 42);
        assertEquals("my_key", e.key());
        assertEquals(1, e.enabledValue());
        assertEquals(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR, e.revertStrategy());
        assertEquals(42, e.priorValue());
        assertEquals(42, e.revertValue());  // RESTORE_PRIOR branch: returns priorValue
    }

    @Test
    public void forExistingKey_priorValueZero_revertValueIsZero() {
        SecureSettingsSpec.Entry e = SecureSettingsSpec.Entry.forExistingKey("k", 1, 0);
        assertEquals(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR, e.revertStrategy());
        assertEquals(0, e.revertValue());
    }

    @Test
    public void forExistingKey_negativePriorValue_isAllowed() {
        // Prior value can be any int (settings can store negatives).
        SecureSettingsSpec.Entry e = SecureSettingsSpec.Entry.forExistingKey("k", 1, -5);
        assertEquals(-5, e.priorValue());
        assertEquals(-5, e.revertValue());
    }

    @Test
    public void forExistingKey_priorValueNonZero_revertRestoresPrior() {
        SecureSettingsSpec.Entry e = SecureSettingsSpec.Entry.forExistingKey("k", 1, 99);
        assertEquals(99, e.revertValue());
    }

    // -----------------------------------------------------------------------
    // Entry.forExistingKey — validation failures
    // -----------------------------------------------------------------------

    @Test
    public void forExistingKey_nullKey_throws() {
        try {
            SecureSettingsSpec.Entry.forExistingKey(null, 1, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void forExistingKey_blankKey_throws() {
        try {
            SecureSettingsSpec.Entry.forExistingKey("  ", 1, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void forExistingKey_negativeEnabledValue_throws() {
        try {
            SecureSettingsSpec.Entry.forExistingKey("k", -1, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // revertValue() branch coverage
    // -----------------------------------------------------------------------

    @Test
    public void revertValue_writesDefaultWhenAbsent() {
        // WRITE_DEFAULT branch
        SecureSettingsSpec.Entry e = SecureSettingsSpec.Entry.forAbsentKey("k", 1);
        assertEquals(SecureSettingsSpec.DEFAULT_REVERT_VALUE, e.revertValue());
    }

    @Test
    public void revertValue_restoresPriorWhenPresent() {
        // RESTORE_PRIOR branch
        SecureSettingsSpec.Entry e = SecureSettingsSpec.Entry.forExistingKey("k", 1, 7);
        assertEquals(7, e.revertValue());
    }

    // -----------------------------------------------------------------------
    // applyList — ordering and entry construction
    // -----------------------------------------------------------------------

    @Test
    public void applyList_bothAbsent_returnsWriteDefaultEntries() {
        List<SecureSettingsSpec.Entry> list = SecureSettingsSpec.applyList(null, null);
        assertEquals(2, list.size());

        // First entry: KEY_FORCE_RESIZABLE
        SecureSettingsSpec.Entry e0 = list.get(0);
        assertEquals(SecureSettingsSpec.KEY_FORCE_RESIZABLE, e0.key());
        assertEquals(SecureSettingsSpec.ENABLED_VALUE, e0.enabledValue());
        assertEquals(SecureSettingsSpec.RevertStrategy.WRITE_DEFAULT, e0.revertStrategy());

        // Second entry: KEY_ENABLE_FREEFORM
        SecureSettingsSpec.Entry e1 = list.get(1);
        assertEquals(SecureSettingsSpec.KEY_ENABLE_FREEFORM, e1.key());
        assertEquals(SecureSettingsSpec.ENABLED_VALUE, e1.enabledValue());
        assertEquals(SecureSettingsSpec.RevertStrategy.WRITE_DEFAULT, e1.revertStrategy());
    }

    @Test
    public void applyList_bothPresent_returnsRestorePriorEntries() {
        List<SecureSettingsSpec.Entry> list = SecureSettingsSpec.applyList(0, 1);
        assertEquals(2, list.size());

        SecureSettingsSpec.Entry e0 = list.get(0);
        assertEquals(SecureSettingsSpec.KEY_FORCE_RESIZABLE, e0.key());
        assertEquals(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR, e0.revertStrategy());
        assertEquals(0, e0.priorValue());

        SecureSettingsSpec.Entry e1 = list.get(1);
        assertEquals(SecureSettingsSpec.KEY_ENABLE_FREEFORM, e1.key());
        assertEquals(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR, e1.revertStrategy());
        assertEquals(1, e1.priorValue());
    }

    @Test
    public void applyList_mixed_firstAbsentSecondPresent() {
        // priorForceResizable = null (absent), priorEnableFreeform = 3 (present)
        List<SecureSettingsSpec.Entry> list = SecureSettingsSpec.applyList(null, 3);
        assertEquals(2, list.size());
        assertEquals(SecureSettingsSpec.RevertStrategy.WRITE_DEFAULT, list.get(0).revertStrategy());
        assertEquals(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR, list.get(1).revertStrategy());
        assertEquals(3, list.get(1).priorValue());
    }

    @Test
    public void applyList_mixed_firstPresentSecondAbsent() {
        List<SecureSettingsSpec.Entry> list = SecureSettingsSpec.applyList(5, null);
        assertEquals(2, list.size());
        assertEquals(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR, list.get(0).revertStrategy());
        assertEquals(5, list.get(0).priorValue());
        assertEquals(SecureSettingsSpec.RevertStrategy.WRITE_DEFAULT, list.get(1).revertStrategy());
    }

    @Test
    public void applyList_isUnmodifiable() {
        List<SecureSettingsSpec.Entry> list = SecureSettingsSpec.applyList(null, null);
        try {
            list.add(SecureSettingsSpec.Entry.forAbsentKey("x", 1));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // revertList — ordering (reverse of apply)
    // -----------------------------------------------------------------------

    @Test
    public void revertList_bothAbsent_reversedOrder() {
        List<SecureSettingsSpec.Entry> list = SecureSettingsSpec.revertList(null, null);
        assertEquals(2, list.size());
        // Revert order: freeform first, then force-resizable
        assertEquals(SecureSettingsSpec.KEY_ENABLE_FREEFORM, list.get(0).key());
        assertEquals(SecureSettingsSpec.KEY_FORCE_RESIZABLE, list.get(1).key());
    }

    @Test
    public void revertList_bothPresent_restoresPriorInReverseOrder() {
        List<SecureSettingsSpec.Entry> list = SecureSettingsSpec.revertList(10, 20);
        assertEquals(2, list.size());

        // First in revert list: KEY_ENABLE_FREEFORM (prior = 20)
        assertEquals(SecureSettingsSpec.KEY_ENABLE_FREEFORM, list.get(0).key());
        assertEquals(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR, list.get(0).revertStrategy());
        assertEquals(20, list.get(0).revertValue());

        // Second in revert list: KEY_FORCE_RESIZABLE (prior = 10)
        assertEquals(SecureSettingsSpec.KEY_FORCE_RESIZABLE, list.get(1).key());
        assertEquals(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR, list.get(1).revertStrategy());
        assertEquals(10, list.get(1).revertValue());
    }

    @Test
    public void revertList_mixed() {
        // priorForce = 7 (present), priorFreeform = null (absent)
        List<SecureSettingsSpec.Entry> list = SecureSettingsSpec.revertList(7, null);
        assertEquals(SecureSettingsSpec.KEY_ENABLE_FREEFORM, list.get(0).key());
        assertEquals(SecureSettingsSpec.RevertStrategy.WRITE_DEFAULT, list.get(0).revertStrategy());
        assertEquals(SecureSettingsSpec.KEY_FORCE_RESIZABLE, list.get(1).key());
        assertEquals(SecureSettingsSpec.RevertStrategy.RESTORE_PRIOR, list.get(1).revertStrategy());
        assertEquals(7, list.get(1).revertValue());
    }

    @Test
    public void revertList_isUnmodifiable() {
        List<SecureSettingsSpec.Entry> list = SecureSettingsSpec.revertList(null, null);
        try {
            list.add(SecureSettingsSpec.Entry.forAbsentKey("x", 1));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // managedKeys
    // -----------------------------------------------------------------------

    @Test
    public void managedKeys_containsBothKeysInOrder() {
        List<String> keys = SecureSettingsSpec.managedKeys();
        assertEquals(2, keys.size());
        assertEquals(SecureSettingsSpec.KEY_FORCE_RESIZABLE, keys.get(0));
        assertEquals(SecureSettingsSpec.KEY_ENABLE_FREEFORM, keys.get(1));
    }

    @Test
    public void managedKeys_isUnmodifiable() {
        List<String> keys = SecureSettingsSpec.managedKeys();
        try {
            keys.add("extra");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}

package com.xiddoc.androidautox.autox.ime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Plain-JUnit tests for {@link ImeDisplaySettingsSpec} and its inner {@link ImeDisplaySettingsSpec.Entry}.
 *
 * <p>Covers: constants, key template formatting, Entry.of validation (null/blank key,
 * invalid/valid priorValues), Entry.wasUnset both branches, Entry.equals/hashCode/toString,
 * forDisplay validation (zero/negative/positive), applyList and revertList contents and ordering,
 * withPriorValues round-trip (all three priorValue combinations for each key), isValid
 * (true/false cases), spec-level equals/hashCode/toString.
 */
public class ImeDisplaySettingsSpecTest {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    @Test
    public void valueEnabled_isOne() {
        assertEquals(1, ImeDisplaySettingsSpec.VALUE_ENABLED);
    }

    @Test
    public void valueDisabled_isZero() {
        assertEquals(0, ImeDisplaySettingsSpec.VALUE_DISABLED);
    }

    @Test
    public void valueUnset_isMinusOne() {
        assertEquals(-1, ImeDisplaySettingsSpec.VALUE_UNSET);
    }

    @Test
    public void keyTemplateIme_containsPlaceholder() {
        assertTrue(ImeDisplaySettingsSpec.KEY_TEMPLATE_SHOULD_SHOW_IME.contains("%d"));
        assertTrue(ImeDisplaySettingsSpec.KEY_TEMPLATE_SHOULD_SHOW_IME
                .contains("display_should_show_ime"));
    }

    @Test
    public void keyTemplateDecor_containsPlaceholder() {
        assertTrue(ImeDisplaySettingsSpec.KEY_TEMPLATE_SHOULD_SHOW_SYSTEM_DECORS.contains("%d"));
        assertTrue(ImeDisplaySettingsSpec.KEY_TEMPLATE_SHOULD_SHOW_SYSTEM_DECORS
                .contains("display_should_show_system_decors"));
    }

    // -------------------------------------------------------------------------
    // Entry.of — key validation
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void entry_of_nullKey_throws() {
        ImeDisplaySettingsSpec.Entry.of(null, ImeDisplaySettingsSpec.VALUE_UNSET);
    }

    @Test(expected = IllegalArgumentException.class)
    public void entry_of_blankKey_throws() {
        ImeDisplaySettingsSpec.Entry.of("   ", ImeDisplaySettingsSpec.VALUE_UNSET);
    }

    @Test(expected = IllegalArgumentException.class)
    public void entry_of_emptyKey_throws() {
        ImeDisplaySettingsSpec.Entry.of("", ImeDisplaySettingsSpec.VALUE_UNSET);
    }

    // -------------------------------------------------------------------------
    // Entry.of — priorValue validation
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void entry_of_invalidPriorValue_positive2_throws() {
        ImeDisplaySettingsSpec.Entry.of("some_key", 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void entry_of_invalidPriorValue_negative2_throws() {
        ImeDisplaySettingsSpec.Entry.of("some_key", -2);
    }

    @Test
    public void entry_of_priorEnabled_valid() {
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "key", ImeDisplaySettingsSpec.VALUE_ENABLED);
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED, e.priorValue);
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED, e.applyValue);
        assertEquals("key", e.key);
    }

    @Test
    public void entry_of_priorDisabled_valid() {
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "key", ImeDisplaySettingsSpec.VALUE_DISABLED);
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED, e.priorValue);
    }

    @Test
    public void entry_of_priorUnset_valid() {
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "key", ImeDisplaySettingsSpec.VALUE_UNSET);
        assertEquals(ImeDisplaySettingsSpec.VALUE_UNSET, e.priorValue);
    }

    // -------------------------------------------------------------------------
    // Entry.wasUnset
    // -------------------------------------------------------------------------

    @Test
    public void entry_wasUnset_true_whenPriorIsUnset() {
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_UNSET);
        assertTrue(e.wasUnset());
    }

    @Test
    public void entry_wasUnset_false_whenPriorIsEnabled() {
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_ENABLED);
        assertFalse(e.wasUnset());
    }

    @Test
    public void entry_wasUnset_false_whenPriorIsDisabled() {
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_DISABLED);
        assertFalse(e.wasUnset());
    }

    // -------------------------------------------------------------------------
    // Entry equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Test
    public void entry_equals_reflexive() {
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_UNSET);
        assertEquals(e, e);
    }

    @Test
    public void entry_equals_symmetric() {
        ImeDisplaySettingsSpec.Entry a = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_DISABLED);
        ImeDisplaySettingsSpec.Entry b = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_DISABLED);
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    public void entry_equals_null_returnsFalse() {
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_UNSET);
        assertFalse(e.equals(null));
    }

    @Test
    public void entry_equals_wrongType_returnsFalse() {
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_UNSET);
        assertFalse(e.equals("k"));
    }

    @Test
    public void entry_equals_differentKey_notEqual() {
        ImeDisplaySettingsSpec.Entry a = ImeDisplaySettingsSpec.Entry.of(
                "k1", ImeDisplaySettingsSpec.VALUE_DISABLED);
        ImeDisplaySettingsSpec.Entry b = ImeDisplaySettingsSpec.Entry.of(
                "k2", ImeDisplaySettingsSpec.VALUE_DISABLED);
        assertNotEquals(a, b);
    }

    @Test
    public void entry_equals_differentPrior_notEqual() {
        ImeDisplaySettingsSpec.Entry a = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_DISABLED);
        ImeDisplaySettingsSpec.Entry b = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_UNSET);
        assertNotEquals(a, b);
    }

    @Test
    public void entry_hashCode_equalObjects_sameHash() {
        ImeDisplaySettingsSpec.Entry a = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_ENABLED);
        ImeDisplaySettingsSpec.Entry b = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_ENABLED);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void entry_toString_containsKeyAndValues() {
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "my_key", ImeDisplaySettingsSpec.VALUE_DISABLED);
        String s = e.toString();
        assertTrue(s.contains("my_key"));
        assertTrue(s.contains("apply=1"));
        assertTrue(s.contains("prior=0"));
    }

    // -------------------------------------------------------------------------
    // forDisplay — displayId validation
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void forDisplay_zero_throws() {
        ImeDisplaySettingsSpec.forDisplay(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void forDisplay_negative_throws() {
        ImeDisplaySettingsSpec.forDisplay(-1);
    }

    @Test
    public void forDisplay_positiveId_succeeds() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(3);
        assertNotNull(spec);
        assertEquals(3, spec.getDisplayId());
    }

    // -------------------------------------------------------------------------
    // forDisplay — key format
    // -------------------------------------------------------------------------

    @Test
    public void forDisplay_imeKey_formattedCorrectly() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(7);
        assertEquals("display_should_show_ime_7", spec.imeKey());
    }

    @Test
    public void forDisplay_decorKey_formattedCorrectly() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(7);
        assertEquals("display_should_show_system_decors_7", spec.decorKey());
    }

    // -------------------------------------------------------------------------
    // forDisplay — applyList ordering and defaults
    // -------------------------------------------------------------------------

    @Test
    public void forDisplay_applyList_hasTwoEntries() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(2);
        assertEquals(2, spec.getApplyList().size());
    }

    @Test
    public void forDisplay_applyList_firstEntry_isDecor() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(2);
        ImeDisplaySettingsSpec.Entry first = spec.getApplyList().get(0);
        assertTrue(first.key.contains("system_decors"));
    }

    @Test
    public void forDisplay_applyList_secondEntry_isIme() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(2);
        ImeDisplaySettingsSpec.Entry second = spec.getApplyList().get(1);
        assertTrue(second.key.contains("should_show_ime"));
    }

    @Test
    public void forDisplay_applyList_allPriorsAreUnset() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(4);
        for (ImeDisplaySettingsSpec.Entry e : spec.getApplyList()) {
            assertEquals(ImeDisplaySettingsSpec.VALUE_UNSET, e.priorValue);
            assertTrue(e.wasUnset());
        }
    }

    @Test
    public void forDisplay_applyList_allApplyValuesAreEnabled() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(4);
        for (ImeDisplaySettingsSpec.Entry e : spec.getApplyList()) {
            assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED, e.applyValue);
        }
    }

    // -------------------------------------------------------------------------
    // forDisplay — revertList ordering (reverse of apply)
    // -------------------------------------------------------------------------

    @Test
    public void forDisplay_revertList_hasTwoEntries() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(2);
        assertEquals(2, spec.getRevertList().size());
    }

    @Test
    public void forDisplay_revertList_firstEntry_isIme() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(2);
        ImeDisplaySettingsSpec.Entry first = spec.getRevertList().get(0);
        // Revert order is reverse of apply: IME first.
        assertTrue(first.key.contains("should_show_ime"));
    }

    @Test
    public void forDisplay_revertList_secondEntry_isDecor() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(2);
        ImeDisplaySettingsSpec.Entry second = spec.getRevertList().get(1);
        assertTrue(second.key.contains("system_decors"));
    }

    // -------------------------------------------------------------------------
    // withPriorValues — all prior combinations
    // -------------------------------------------------------------------------

    @Test
    public void withPriorValues_bothEnabled_recorded() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(5)
                .withPriorValues(
                        ImeDisplaySettingsSpec.VALUE_ENABLED,
                        ImeDisplaySettingsSpec.VALUE_ENABLED);
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED,
                spec.getApplyList().get(0).priorValue);
        assertEquals(ImeDisplaySettingsSpec.VALUE_ENABLED,
                spec.getApplyList().get(1).priorValue);
    }

    @Test
    public void withPriorValues_bothDisabled_recorded() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(5)
                .withPriorValues(
                        ImeDisplaySettingsSpec.VALUE_DISABLED,
                        ImeDisplaySettingsSpec.VALUE_DISABLED);
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                spec.getApplyList().get(0).priorValue);
        assertEquals(ImeDisplaySettingsSpec.VALUE_DISABLED,
                spec.getApplyList().get(1).priorValue);
    }

    @Test
    public void withPriorValues_bothUnset_recorded() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(5)
                .withPriorValues(
                        ImeDisplaySettingsSpec.VALUE_UNSET,
                        ImeDisplaySettingsSpec.VALUE_UNSET);
        assertTrue(spec.getApplyList().get(0).wasUnset());
        assertTrue(spec.getApplyList().get(1).wasUnset());
    }

    @Test
    public void withPriorValues_mixed_decorDisabledImeUnset() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(6)
                .withPriorValues(
                        ImeDisplaySettingsSpec.VALUE_DISABLED,
                        ImeDisplaySettingsSpec.VALUE_UNSET);
        assertFalse(spec.getApplyList().get(0).wasUnset());  // decor was disabled
        assertTrue(spec.getApplyList().get(1).wasUnset());   // ime was unset
    }

    @Test
    public void withPriorValues_displayIdPreserved() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(99)
                .withPriorValues(
                        ImeDisplaySettingsSpec.VALUE_DISABLED,
                        ImeDisplaySettingsSpec.VALUE_DISABLED);
        assertEquals(99, spec.getDisplayId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void withPriorValues_invalidDecorPrior_throws() {
        ImeDisplaySettingsSpec.forDisplay(5).withPriorValues(42, ImeDisplaySettingsSpec.VALUE_UNSET);
    }

    @Test(expected = IllegalArgumentException.class)
    public void withPriorValues_invalidImePrior_throws() {
        ImeDisplaySettingsSpec.forDisplay(5).withPriorValues(ImeDisplaySettingsSpec.VALUE_UNSET, 42);
    }

    // -------------------------------------------------------------------------
    // revertList on withPriorValues — reversal is still correct
    // -------------------------------------------------------------------------

    @Test
    public void withPriorValues_revertList_remainsReversed() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(8)
                .withPriorValues(
                        ImeDisplaySettingsSpec.VALUE_ENABLED,
                        ImeDisplaySettingsSpec.VALUE_DISABLED);
        // Revert first = IME (apply index 1), revert second = decor (apply index 0).
        assertTrue(spec.getRevertList().get(0).key.contains("should_show_ime"));
        assertTrue(spec.getRevertList().get(1).key.contains("system_decors"));
    }

    // -------------------------------------------------------------------------
    // isValid
    // -------------------------------------------------------------------------

    @Test
    public void isValid_freshSpec_returnsTrue() {
        assertTrue(ImeDisplaySettingsSpec.forDisplay(1).isValid());
    }

    @Test
    public void isValid_withPriorValues_returnsTrue() {
        assertTrue(ImeDisplaySettingsSpec.forDisplay(1)
                .withPriorValues(
                        ImeDisplaySettingsSpec.VALUE_DISABLED,
                        ImeDisplaySettingsSpec.VALUE_UNSET)
                .isValid());
    }

    @Test
    public void isValid_largeDisplayId_returnsTrue() {
        assertTrue(ImeDisplaySettingsSpec.forDisplay(1234).isValid());
    }

    // -------------------------------------------------------------------------
    // Spec equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Test
    public void spec_equals_reflexive() {
        ImeDisplaySettingsSpec s = ImeDisplaySettingsSpec.forDisplay(3);
        assertEquals(s, s);
    }

    @Test
    public void spec_equals_sameDisplayId_equal() {
        ImeDisplaySettingsSpec a = ImeDisplaySettingsSpec.forDisplay(3);
        ImeDisplaySettingsSpec b = ImeDisplaySettingsSpec.forDisplay(3);
        assertEquals(a, b);
    }

    @Test
    public void spec_equals_differentDisplayId_notEqual() {
        ImeDisplaySettingsSpec a = ImeDisplaySettingsSpec.forDisplay(3);
        ImeDisplaySettingsSpec b = ImeDisplaySettingsSpec.forDisplay(4);
        assertNotEquals(a, b);
    }

    @Test
    public void spec_equals_null_returnsFalse() {
        assertFalse(ImeDisplaySettingsSpec.forDisplay(3).equals(null));
    }

    @Test
    public void spec_equals_wrongType_returnsFalse() {
        assertFalse(ImeDisplaySettingsSpec.forDisplay(3).equals("not a spec"));
    }

    @Test
    public void spec_equals_differentPriors_notEqual() {
        ImeDisplaySettingsSpec a = ImeDisplaySettingsSpec.forDisplay(5)
                .withPriorValues(ImeDisplaySettingsSpec.VALUE_DISABLED, ImeDisplaySettingsSpec.VALUE_UNSET);
        ImeDisplaySettingsSpec b = ImeDisplaySettingsSpec.forDisplay(5)
                .withPriorValues(ImeDisplaySettingsSpec.VALUE_ENABLED, ImeDisplaySettingsSpec.VALUE_UNSET);
        assertNotEquals(a, b);
    }

    @Test
    public void spec_hashCode_equalObjects_sameHash() {
        ImeDisplaySettingsSpec a = ImeDisplaySettingsSpec.forDisplay(3);
        ImeDisplaySettingsSpec b = ImeDisplaySettingsSpec.forDisplay(3);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void spec_toString_containsDisplayId() {
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(42);
        String s = spec.toString();
        assertTrue(s.contains("42"));
    }

    @Test
    public void spec_toString_notNull() {
        assertNotNull(ImeDisplaySettingsSpec.forDisplay(1).toString());
    }

    // -------------------------------------------------------------------------
    // Immutability — returned lists are unmodifiable
    // -------------------------------------------------------------------------

    @Test(expected = UnsupportedOperationException.class)
    public void getApplyList_isUnmodifiable() {
        List<ImeDisplaySettingsSpec.Entry> list =
                ImeDisplaySettingsSpec.forDisplay(1).getApplyList();
        list.clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getRevertList_isUnmodifiable() {
        List<ImeDisplaySettingsSpec.Entry> list =
                ImeDisplaySettingsSpec.forDisplay(1).getRevertList();
        list.clear();
    }

    // -------------------------------------------------------------------------
    // Entry.equals — applyValue distinguishing branch
    // (applyValue is always 1 via Entry.of, so we use the package-private factory
    // to create an entry with a different applyValue and exercise that branch)
    // -------------------------------------------------------------------------

    @Test
    public void entry_equals_differentApplyValue_notEqual() {
        // Create two entries with the same key and priorValue but different applyValues.
        ImeDisplaySettingsSpec.Entry a = ImeDisplaySettingsSpec.Entry.of(
                "k", ImeDisplaySettingsSpec.VALUE_DISABLED);
        // Use the package-private test factory to create an entry with applyValue = 0.
        ImeDisplaySettingsSpec.Entry b = ImeDisplaySettingsSpec.Entry.ofWithApplyValue(
                "k", ImeDisplaySettingsSpec.VALUE_DISABLED, ImeDisplaySettingsSpec.VALUE_DISABLED);
        assertNotEquals(a, b);
    }

    // -------------------------------------------------------------------------
    // isValid — false branches
    // (use package-private withEntries to construct invariant-violating specs)
    // -------------------------------------------------------------------------

    @Test
    public void isValid_wrongNumberOfEntries_returnsFalse() {
        // Build a spec with 1 entry instead of 2 to exercise the size != 2 branch.
        ImeDisplaySettingsSpec.Entry e = ImeDisplaySettingsSpec.Entry.of(
                "display_should_show_ime_9", ImeDisplaySettingsSpec.VALUE_UNSET);
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.withEntries(
                9, java.util.Collections.singletonList(e));
        assertFalse(spec.isValid());
    }

    @Test
    public void isValid_entryApplyValueNotEnabled_returnsFalse() {
        // Build a spec where one entry has applyValue != VALUE_ENABLED.
        ImeDisplaySettingsSpec.Entry decor = ImeDisplaySettingsSpec.Entry.ofWithApplyValue(
                "display_should_show_system_decors_9", 0, ImeDisplaySettingsSpec.VALUE_UNSET);
        ImeDisplaySettingsSpec.Entry ime = ImeDisplaySettingsSpec.Entry.of(
                "display_should_show_ime_9", ImeDisplaySettingsSpec.VALUE_UNSET);
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.withEntries(
                9, java.util.Arrays.asList(decor, ime));
        assertFalse(spec.isValid());
    }

    @Test
    public void isValid_keyDoesNotEndWithDisplayId_returnsFalse() {
        // Build a spec where a key does not end with the displayId.
        ImeDisplaySettingsSpec.Entry wrongKey = ImeDisplaySettingsSpec.Entry.of(
                "display_should_show_system_decors_99", ImeDisplaySettingsSpec.VALUE_UNSET);
        ImeDisplaySettingsSpec.Entry imeEntry = ImeDisplaySettingsSpec.Entry.of(
                "display_should_show_ime_99", ImeDisplaySettingsSpec.VALUE_UNSET);
        // Use displayId=1 but entries have keys ending in _99 → key does not end with "1".
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.withEntries(
                1, java.util.Arrays.asList(wrongKey, imeEntry));
        assertFalse(spec.isValid());
    }
}

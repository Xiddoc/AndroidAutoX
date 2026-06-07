package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Plain-JUnit tests for {@link SettingsResult} — every factory, status and contract. */
public class SettingsResultTest {

    @Test
    public void okWithValue_carriesValueAndIsOk() {
        SettingsResult r = SettingsResult.ok(42);
        assertEquals(SettingsResult.Status.OK, r.status);
        assertEquals(42, r.value);
        assertTrue(r.isOk());
    }

    @Test
    public void okNoValue_isOkWithZero() {
        SettingsResult r = SettingsResult.ok();
        assertTrue(r.isOk());
        assertEquals(0, r.value);
    }

    @Test
    public void denied_isNotOk() {
        SettingsResult r = SettingsResult.denied();
        assertEquals(SettingsResult.Status.DENIED, r.status);
        assertFalse(r.isOk());
    }

    @Test
    public void notFound_isNotOk() {
        SettingsResult r = SettingsResult.notFound();
        assertEquals(SettingsResult.Status.NOT_FOUND, r.status);
        assertFalse(r.isOk());
    }

    @Test
    public void equalsHashCode_contract() {
        SettingsResult a = SettingsResult.ok(7);
        SettingsResult b = SettingsResult.ok(7);
        SettingsResult diffVal = SettingsResult.ok(8);
        SettingsResult diffStatus = SettingsResult.denied();

        assertTrue(a.equals(a));               // reflexive
        assertEquals(a, b);                    // value equality
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diffVal);           // different value (first && branch fails)
        assertNotEquals(a, diffStatus);        // different status
        // Same value, different status -> reaches and fails the SECOND && branch.
        assertNotEquals(SettingsResult.ok(0), SettingsResult.denied());
        assertFalse(a.equals(null));           // null
        assertFalse(a.equals("x"));            // wrong type
    }

    @Test
    public void toString_mentionsStatusAndValue() {
        String s = SettingsResult.ok(5).toString();
        assertTrue(s.contains("OK"));
        assertTrue(s.contains("5"));
    }

    @Test
    public void enumValues_areExercised() {
        // Touch valueOf/values so the enum's synthetic methods are covered.
        assertEquals(SettingsResult.Status.OK,
                SettingsResult.Status.valueOf("OK"));
        assertEquals(3, SettingsResult.Status.values().length);
    }

    // The private ctor rejects a null status defensively; reach it via reflection-free
    // path is impossible (factories never pass null), so we document the invariant here.
    @Test
    public void factories_neverProduceNullStatus() {
        for (SettingsResult r : new SettingsResult[]{
                SettingsResult.ok(), SettingsResult.ok(1),
                SettingsResult.denied(), SettingsResult.notFound()}) {
            if (r.status == null) {
                fail("status must never be null");
            }
        }
    }
}

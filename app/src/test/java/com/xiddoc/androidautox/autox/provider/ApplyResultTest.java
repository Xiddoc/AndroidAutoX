package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Tests for the pure shared {@link ApplyResult}. */
public class ApplyResultTest {

    @Test
    public void allSucceeded_whenNoFailures() {
        ApplyResult r = new ApplyResult(2, 2, Collections.<String>emptyList());
        assertTrue(r.allSucceeded());
        assertEquals(2, r.succeeded);
        assertEquals(2, r.total);
        assertTrue(r.failedKeys().isEmpty());
    }

    @Test
    public void notAllSucceeded_recordsFailedKeys() {
        ApplyResult r = new ApplyResult(1, 2, Collections.singletonList("k2"));
        assertFalse(r.allSucceeded());
        assertEquals(Collections.singletonList("k2"), r.failedKeys());
    }

    @Test
    public void emptyResult_isVacuouslyAllSucceeded() {
        ApplyResult r = new ApplyResult(0, 0, Collections.<String>emptyList());
        assertTrue(r.allSucceeded());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void failedKeys_isUnmodifiable() {
        ApplyResult r = new ApplyResult(0, 1, Collections.singletonList("a"));
        r.failedKeys().add("b");
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeTotal_throws() {
        new ApplyResult(0, -1, Collections.<String>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void succeededExceedsTotal_throws() {
        new ApplyResult(3, 2, Collections.<String>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeSucceeded_throws() {
        new ApplyResult(-1, 0, Collections.<String>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullFailedKeys_throws() {
        new ApplyResult(0, 0, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void failedKeysSizeMismatch_throws() {
        // total-succeeded = 1 but two failed keys supplied.
        new ApplyResult(1, 2, Arrays.asList("a", "b"));
    }

    @Test
    public void equalsHashCodeToString() {
        List<String> f = Collections.singletonList("k");
        ApplyResult a = new ApplyResult(1, 2, f);
        ApplyResult b = new ApplyResult(1, 2, Collections.singletonList("k"));
        ApplyResult diffSucc = new ApplyResult(0, 2, Arrays.asList("k", "j"));
        ApplyResult diffTotal = new ApplyResult(1, 3, Arrays.asList("x", "y"));
        ApplyResult diffKeys = new ApplyResult(1, 2, Collections.singletonList("z"));

        assertEquals(a, a);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diffSucc);
        assertNotEquals(a, diffTotal);
        assertNotEquals(a, diffKeys);
        assertNotEquals(a, null);
        assertNotEquals(a, "nope");
        assertTrue(a.toString().contains("succeeded=1/2"));
        assertTrue(a.toString().contains("failedKeys=[k]"));
    }
}

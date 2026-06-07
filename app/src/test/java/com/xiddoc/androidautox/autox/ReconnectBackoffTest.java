package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link ReconnectBackoff} — no Android runtime required.
 *
 * <p>Covers:
 * <ul>
 *   <li>Default factory: correct initial/base/max; delay sequence; cap at max.</li>
 *   <li>{@code delayMs(n)} — attempt 0, attempt 1, attempt 2, large attempt (cap),
 *       negative attempt treated as 0.</li>
 *   <li>Cap arithmetic — ensures the delay never exceeds {@code maxDelayMs}.</li>
 *   <li>Constructor validation — negative initial, base &lt; 1, max &lt; initial.</li>
 *   <li>Edge cases: base=1 (no growth), initialDelay=0, maxDelay==initialDelay,
 *       initialDelay==maxDelay with base&gt;1 (always returns max).</li>
 *   <li>Overflow safety — large base / large attempt stays capped.</li>
 *   <li>Getters — initialDelayMs, base, maxDelayMs.</li>
 * </ul>
 */
public class ReconnectBackoffTest {

    // ------------------------------------------------------------------
    // Default factory
    // ------------------------------------------------------------------

    @Test
    public void createDefault_initialDelay_isOneSecond() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(ReconnectBackoff.DEFAULT_INITIAL_DELAY_MS, b.getInitialDelayMs());
    }

    @Test
    public void createDefault_base_isTwo() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(ReconnectBackoff.DEFAULT_BASE, b.getBase());
    }

    @Test
    public void createDefault_maxDelay_isThirtySeconds() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(ReconnectBackoff.DEFAULT_MAX_DELAY_MS, b.getMaxDelayMs());
    }

    @Test
    public void createDefault_attempt0_returnsInitialDelay() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(1_000L, b.delayMs(0));
    }

    @Test
    public void createDefault_attempt1_returnsTwoSeconds() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(2_000L, b.delayMs(1));
    }

    @Test
    public void createDefault_attempt2_returnsFourSeconds() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(4_000L, b.delayMs(2));
    }

    @Test
    public void createDefault_attempt3_returnsEightSeconds() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(8_000L, b.delayMs(3));
    }

    @Test
    public void createDefault_attempt4_returnsSixteenSeconds() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(16_000L, b.delayMs(4));
    }

    @Test
    public void createDefault_attempt5_cappedAtThirtySeconds() {
        // 1000 × 2^5 = 32000 > 30000 → capped
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(30_000L, b.delayMs(5));
    }

    @Test
    public void createDefault_largeAttempt_cappedAtMax() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(30_000L, b.delayMs(100));
    }

    // ------------------------------------------------------------------
    // Negative / zero attempt
    // ------------------------------------------------------------------

    @Test
    public void delayMs_negativeAttempt_treatedAsZero() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(b.delayMs(0), b.delayMs(-1));
    }

    @Test
    public void delayMs_negativeLarge_treatedAsZero() {
        ReconnectBackoff b = ReconnectBackoff.createDefault();
        assertEquals(b.delayMs(0), b.delayMs(Integer.MIN_VALUE));
    }

    // ------------------------------------------------------------------
    // Never exceeds maxDelayMs
    // ------------------------------------------------------------------

    @Test
    public void delayMs_neverExceedsMax() {
        ReconnectBackoff b = new ReconnectBackoff(500L, 3, 5_000L);
        for (int i = 0; i <= 20; i++) {
            assertTrue("attempt " + i + " exceeded max",
                    b.delayMs(i) <= 5_000L);
        }
    }

    @Test
    public void delayMs_alwaysNonNegative() {
        ReconnectBackoff b = new ReconnectBackoff(0L, 2, 10_000L);
        for (int i = 0; i <= 10; i++) {
            assertTrue("attempt " + i + " was negative", b.delayMs(i) >= 0L);
        }
    }

    // ------------------------------------------------------------------
    // Edge case: base = 1 (flat delay)
    // ------------------------------------------------------------------

    @Test
    public void base1_attempt0_returnsInitial() {
        ReconnectBackoff b = new ReconnectBackoff(2_000L, 1, 10_000L);
        assertEquals(2_000L, b.delayMs(0));
    }

    @Test
    public void base1_attemptN_alwaysReturnsInitial() {
        ReconnectBackoff b = new ReconnectBackoff(2_000L, 1, 10_000L);
        for (int i = 0; i <= 5; i++) {
            assertEquals("flat: attempt " + i, 2_000L, b.delayMs(i));
        }
    }

    // ------------------------------------------------------------------
    // Edge case: initialDelay = 0
    // ------------------------------------------------------------------

    @Test
    public void initialDelayZero_attempt0_returnsZero() {
        ReconnectBackoff b = new ReconnectBackoff(0L, 2, 10_000L);
        assertEquals(0L, b.delayMs(0));
    }

    @Test
    public void initialDelayZero_attemptN_returnsZero() {
        ReconnectBackoff b = new ReconnectBackoff(0L, 2, 10_000L);
        for (int i = 0; i <= 5; i++) {
            assertEquals("zero initial: attempt " + i, 0L, b.delayMs(i));
        }
    }

    // ------------------------------------------------------------------
    // Edge case: initialDelay == maxDelay
    // ------------------------------------------------------------------

    @Test
    public void initialEqualsMax_attempt0_returnsMax() {
        ReconnectBackoff b = new ReconnectBackoff(5_000L, 2, 5_000L);
        assertEquals(5_000L, b.delayMs(0));
    }

    @Test
    public void initialEqualsMax_attemptN_returnsMax() {
        ReconnectBackoff b = new ReconnectBackoff(5_000L, 2, 5_000L);
        for (int i = 1; i <= 5; i++) {
            assertEquals("capped: attempt " + i, 5_000L, b.delayMs(i));
        }
    }

    // ------------------------------------------------------------------
    // Overflow safety
    // ------------------------------------------------------------------

    @Test
    public void overflowSafe_largeBaseAndAttempt_returnsCap() {
        ReconnectBackoff b = new ReconnectBackoff(1_000L, Integer.MAX_VALUE, 30_000L);
        assertEquals(30_000L, b.delayMs(5));
    }

    @Test
    public void overflowSafe_largeInitialAndBase_returnsCap() {
        ReconnectBackoff b = new ReconnectBackoff(1_000L, 1_000_000, 30_000L);
        assertEquals(30_000L, b.delayMs(1));
    }

    // ------------------------------------------------------------------
    // Constructor validation
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void constructor_negativeInitial_throws() {
        new ReconnectBackoff(-1L, 2, 10_000L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_baseLessThanOne_throws() {
        new ReconnectBackoff(1_000L, 0, 10_000L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_baseNegative_throws() {
        new ReconnectBackoff(1_000L, -5, 10_000L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_maxLessThanInitial_throws() {
        new ReconnectBackoff(5_000L, 2, 2_000L);
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    @Test
    public void getters_returnConstructorValues() {
        ReconnectBackoff b = new ReconnectBackoff(500L, 3, 8_000L);
        assertEquals(500L, b.getInitialDelayMs());
        assertEquals(3, b.getBase());
        assertEquals(8_000L, b.getMaxDelayMs());
    }

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    @Test
    public void defaultConstants_haveExpectedValues() {
        assertEquals(1_000L, ReconnectBackoff.DEFAULT_INITIAL_DELAY_MS);
        assertEquals(2, ReconnectBackoff.DEFAULT_BASE);
        assertEquals(30_000L, ReconnectBackoff.DEFAULT_MAX_DELAY_MS);
    }
}

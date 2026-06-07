package com.xiddoc.androidautox.autox;

/**
 * Pure (no Android imports) bounded exponential-backoff calculator for AutoX host
 * reconnect attempts.
 *
 * <h2>Purpose</h2>
 * <p>When the Android Auto host disconnects unexpectedly, a naive immediately-retry
 * loop would hammer the system.  This class computes a bounded exponential delay
 * sequence:
 * <pre>
 *   delay(n) = min(initialDelayMs × base^n, maxDelayMs)
 * </pre>
 * where {@code n} is the zero-based attempt index.  The result is clamped to
 * {@code [0, maxDelayMs]} and never overflows {@code long}.
 *
 * <h2>Default parameters</h2>
 * <ul>
 *   <li>Initial delay: {@value #DEFAULT_INITIAL_DELAY_MS} ms</li>
 *   <li>Multiplier base: {@value #DEFAULT_BASE}</li>
 *   <li>Maximum delay: {@value #DEFAULT_MAX_DELAY_MS} ms (30 s)</li>
 * </ul>
 * The sequence is: 1 s, 2 s, 4 s, 8 s, 16 s, 30 s, 30 s, … (capped).
 *
 * <h2>Design</h2>
 * <p>This class is pure Java with <b>no Android imports</b>.  The multiplier is
 * computed in {@code long} arithmetic to avoid floating-point loss; the result is
 * capped before it can overflow.  Construction validates that
 * {@code initialDelayMs ≥ 0}, {@code base ≥ 1}, and
 * {@code maxDelayMs ≥ initialDelayMs}.
 *
 * <p>This class must remain at 100% line + branch coverage.
 */
public final class ReconnectBackoff {

    /** Default initial delay before the first reconnect attempt (1 second). */
    public static final long DEFAULT_INITIAL_DELAY_MS = 1_000L;

    /** Default exponential base (doubles each attempt). */
    public static final int DEFAULT_BASE = 2;

    /** Default maximum delay cap (30 seconds). */
    public static final long DEFAULT_MAX_DELAY_MS = 30_000L;

    private final long initialDelayMs;
    private final int base;
    private final long maxDelayMs;

    // ------------------------------------------------------------------
    // Factory
    // ------------------------------------------------------------------

    /**
     * Returns a {@code ReconnectBackoff} with the default parameters:
     * initial=1 s, base=2, max=30 s.
     *
     * @return a new default {@code ReconnectBackoff} instance
     */
    public static ReconnectBackoff createDefault() {
        return new ReconnectBackoff(
                DEFAULT_INITIAL_DELAY_MS, DEFAULT_BASE, DEFAULT_MAX_DELAY_MS);
    }

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    /**
     * Constructs a {@code ReconnectBackoff} with the given parameters.
     *
     * @param initialDelayMs non-negative initial delay in milliseconds
     * @param base           exponential multiplier, must be ≥ 1
     * @param maxDelayMs     maximum delay cap in milliseconds; must be ≥ initialDelayMs
     * @throws IllegalArgumentException if any parameter violates the constraints
     */
    public ReconnectBackoff(long initialDelayMs, int base, long maxDelayMs) {
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "initialDelayMs must be >= 0, got " + initialDelayMs);
        }
        if (base < 1) {
            throw new IllegalArgumentException(
                    "base must be >= 1, got " + base);
        }
        if (maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException(
                    "maxDelayMs (" + maxDelayMs + ") must be >= initialDelayMs ("
                            + initialDelayMs + ")");
        }
        this.initialDelayMs = initialDelayMs;
        this.base = base;
        this.maxDelayMs = maxDelayMs;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Returns the delay in milliseconds before the n-th reconnect attempt (0-based).
     *
     * <p>Computes {@code min(initialDelayMs × base^attempt, maxDelayMs)}, clamped to
     * {@code [0, maxDelayMs]} and safe against {@code long} overflow.
     *
     * @param attempt zero-based reconnect attempt index (0 = first retry); negative
     *                values are treated as 0
     * @return the delay in milliseconds; always in {@code [0, maxDelayMs]}
     */
    public long delayMs(int attempt) {
        if (attempt <= 0) {
            return Math.min(initialDelayMs, maxDelayMs);
        }
        // Compute initialDelayMs × base^attempt in long arithmetic, capping early to
        // avoid integer overflow.  The overflow guard: if the current delay is already
        // >= maxDelayMs/base, then delay*base >= maxDelayMs so we cap before the multiply.
        long delay = initialDelayMs;
        for (int i = 0; i < attempt; i++) {
            if (delay >= maxDelayMs / base) {
                return maxDelayMs;
            }
            delay *= base;
        }
        return Math.min(delay, maxDelayMs);
    }

    /**
     * Returns the configured initial delay in milliseconds.
     *
     * @return initial delay in ms
     */
    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    /**
     * Returns the configured exponential base.
     *
     * @return base multiplier (≥ 1)
     */
    public int getBase() {
        return base;
    }

    /**
     * Returns the configured maximum delay cap in milliseconds.
     *
     * @return maximum delay in ms
     */
    public long getMaxDelayMs() {
        return maxDelayMs;
    }
}

package com.xiddoc.androidautox.autox.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure, immutable outcome of applying (or reverting) a list of {@link SettingsEntry} values
 * through a {@link SettingsApplier}.
 *
 * <h2>Failure policy: CONTINUE-AND-REPORT</h2>
 * <p>The applier attempts <em>every</em> entry rather than stopping at the first failure, so
 * a single denied key does not prevent the remaining keys from being written. This result
 * records how many of the {@link #total} entries {@link #succeeded}, and the ordered list of
 * {@link #failedKeys} that did not.
 *
 * <p>No Android imports — fully unit-testable and not excluded from the coverage gate.
 */
public final class ApplyResult {

    /** Number of entries that were written successfully. */
    public final int succeeded;
    /** Total number of entries that were attempted. */
    public final int total;

    private final List<String> failedKeys;

    /**
     * @param succeeded  number of successful writes; {@code 0 <= succeeded <= total}
     * @param total      total entries attempted; must be &ge; 0
     * @param failedKeys ordered keys that failed; must be non-null with size
     *                   {@code total - succeeded}
     * @throws IllegalArgumentException if the counts are inconsistent or {@code failedKeys}
     *                                  is null
     */
    public ApplyResult(int succeeded, int total, List<String> failedKeys) {
        if (total < 0 || succeeded < 0 || succeeded > total) {
            throw new IllegalArgumentException(
                    "invalid counts: succeeded=" + succeeded + ", total=" + total);
        }
        if (failedKeys == null) {
            throw new IllegalArgumentException("failedKeys must not be null");
        }
        if (failedKeys.size() != total - succeeded) {
            throw new IllegalArgumentException(
                    "failedKeys size (" + failedKeys.size() + ") must equal total-succeeded ("
                            + (total - succeeded) + ")");
        }
        this.succeeded = succeeded;
        this.total = total;
        this.failedKeys = Collections.unmodifiableList(new ArrayList<>(failedKeys));
    }

    /** @return an unmodifiable, ordered list of the keys whose write failed. */
    public List<String> failedKeys() {
        return failedKeys;
    }

    /** @return {@code true} iff every attempted entry succeeded. */
    public boolean allSucceeded() {
        return succeeded == total;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplyResult)) return false;
        ApplyResult that = (ApplyResult) o;
        return succeeded == that.succeeded
                && total == that.total
                && failedKeys.equals(that.failedKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(succeeded, total, failedKeys);
    }

    @Override
    public String toString() {
        return "ApplyResult{succeeded=" + succeeded + "/" + total
                + ", allSucceeded=" + allSucceeded()
                + ", failedKeys=" + failedKeys + '}';
    }
}

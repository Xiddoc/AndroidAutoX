package com.xiddoc.androidautox.autox.provider;

/**
 * Pure result/capability value returned by {@link SystemSettingsProvider} read/write
 * operations. Models the three relevant outcomes of a privileged settings access
 * without throwing — callers branch on {@link #status} and (for reads) inspect
 * {@link #value} only when {@link #isOk()}.
 *
 * <p>This is a framework-free value object (no Android imports) so it can be unit
 * tested on the plain JVM and shared across the LSPosed and root-reflection
 * provider implementations.
 */
public final class SettingsResult {

    /** Outcome of a {@link SystemSettingsProvider} call. */
    public enum Status {
        /** The read/write succeeded; for reads, {@link #value} carries the result. */
        OK,
        /** The provider lacked the privilege to perform the operation. */
        DENIED,
        /** The key was absent (reads) or the operation otherwise found nothing. */
        NOT_FOUND
    }

    /** Outcome of the operation. Never null. */
    public final Status status;

    /**
     * The integer value read, meaningful only when {@link #status} is {@link Status#OK}
     * for a read operation. For writes (and non-OK reads) it is {@code 0} and should be
     * ignored — branch on {@link #isOk()} first.
     */
    public final int value;

    private SettingsResult(Status status, int value) {
        // status is always supplied non-null by the factory methods below.
        this.status = status;
        this.value = value;
    }

    /** Successful read carrying {@code value}. */
    public static SettingsResult ok(int value) {
        return new SettingsResult(Status.OK, value);
    }

    /** Successful write (no value). */
    public static SettingsResult ok() {
        return new SettingsResult(Status.OK, 0);
    }

    /** The operation was denied for lack of privilege. */
    public static SettingsResult denied() {
        return new SettingsResult(Status.DENIED, 0);
    }

    /** The requested key was not present. */
    public static SettingsResult notFound() {
        return new SettingsResult(Status.NOT_FOUND, 0);
    }

    /** @return {@code true} iff {@link #status} is {@link Status#OK}. */
    public boolean isOk() {
        return status == Status.OK;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SettingsResult)) return false;
        SettingsResult other = (SettingsResult) o;
        return value == other.value && status == other.status;
    }

    @Override
    public int hashCode() {
        return 31 * status.hashCode() + value;
    }

    @Override
    public String toString() {
        return "SettingsResult{status=" + status + ", value=" + value + '}';
    }
}

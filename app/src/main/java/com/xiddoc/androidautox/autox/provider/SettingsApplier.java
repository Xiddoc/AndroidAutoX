package com.xiddoc.androidautox.autox.provider;

import java.util.ArrayList;
import java.util.List;

/**
 * Single instance applier that writes {@link SettingsEntry} lists through a
 * {@link SystemSettingsProvider}, parameterized by the target {@link Namespace}
 * ({@code Settings.Global} vs {@code Settings.Secure}).
 *
 * <p>Replaces the per-feature {@code FreeformSettingsApplier} / {@code ImeDisplaySettingsApplier}
 * with one shared implementation. The only difference between the two callers — which
 * namespace to write — is captured by {@link Namespace}; everything else (iteration order,
 * the {@link ApplyResult} contract) is identical.
 *
 * <h2>Failure policy: CONTINUE-AND-REPORT</h2>
 * <p>Both {@link #apply} and {@link #revert} attempt every entry and aggregate the outcome
 * into an {@link ApplyResult}; a single failed write never aborts the rest.
 *
 * <p>No Android imports — the {@link SystemSettingsProvider} seam is framework-free, so this
 * class is fully unit-testable and is not excluded from the coverage gate.
 */
public final class SettingsApplier {

    /** The settings namespace a {@link SettingsApplier} writes to. */
    public enum Namespace {
        /** {@code Settings.Global}. */
        GLOBAL,
        /** {@code Settings.Secure}. */
        SECURE
    }

    private final SystemSettingsProvider provider;
    private final Namespace namespace;

    /**
     * @param provider  the settings provider to write through; must not be null
     * @param namespace the target namespace; must not be null
     * @throws IllegalArgumentException if either argument is null
     */
    public SettingsApplier(SystemSettingsProvider provider, Namespace namespace) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (namespace == null) {
            throw new IllegalArgumentException("namespace must not be null");
        }
        this.provider = provider;
        this.namespace = namespace;
    }

    /** @return the namespace this applier targets. */
    public Namespace namespace() {
        return namespace;
    }

    /**
     * Writes each entry's {@link SettingsEntry#applyValue} in list order, continuing past
     * failures and aggregating the outcome.
     *
     * @param entries the ordered entries to apply; must not be null
     * @return the {@link ApplyResult} for the pass
     * @throws IllegalArgumentException if {@code entries} is null
     */
    public ApplyResult apply(List<SettingsEntry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("entries must not be null");
        }
        int succeeded = 0;
        List<String> failed = new ArrayList<>();
        for (SettingsEntry entry : entries) {
            if (write(entry.key, entry.applyValue)) {
                succeeded++;
            } else {
                failed.add(entry.key);
            }
        }
        return new ApplyResult(succeeded, entries.size(), failed);
    }

    /**
     * Writes each entry's {@link SettingsEntry#revertValue()} in list order, continuing past
     * failures and aggregating the outcome.
     *
     * @param entries the ordered entries to revert; must not be null
     * @return the {@link ApplyResult} for the pass
     * @throws IllegalArgumentException if {@code entries} is null
     */
    public ApplyResult revert(List<SettingsEntry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("entries must not be null");
        }
        int succeeded = 0;
        List<String> failed = new ArrayList<>();
        for (SettingsEntry entry : entries) {
            if (write(entry.key, entry.revertValue())) {
                succeeded++;
            } else {
                failed.add(entry.key);
            }
        }
        return new ApplyResult(succeeded, entries.size(), failed);
    }

    /** Writes one key/value into the configured namespace, returning success. */
    private boolean write(String key, int value) {
        SettingsResult result = (namespace == Namespace.GLOBAL)
                ? provider.putGlobalInt(key, value)
                : provider.putSecureInt(key, value);
        return result.isOk();
    }
}

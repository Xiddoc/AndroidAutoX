package com.xiddoc.androidautox.autox.ime;

import com.xiddoc.androidautox.autox.provider.SettingsResult;
import com.xiddoc.androidautox.autox.provider.SystemSettingsProvider;

/**
 * Pure helper that reads the current per-display IME / system-decor prior values from a
 * {@link SystemSettingsProvider} and returns a populated {@link ImeDisplaySettingsSpec}.
 *
 * <p>This is the only IME-specific logic that touches the provider; the actual writing of the
 * spec's {@link com.xiddoc.androidautox.autox.provider.SettingsEntry} lists is done by the
 * shared {@link com.xiddoc.androidautox.autox.provider.SettingsApplier} (no more per-feature
 * applier/result types). No Android imports — fully unit-testable.
 *
 * <h2>Prior-value mapping</h2>
 * <ul>
 *   <li>provider returns OK(v) → prior value {@code v} (0 or 1)</li>
 *   <li>provider returns NOT_FOUND → {@link ImeDisplaySettingsSpec#VALUE_UNSET}</li>
 *   <li>provider returns DENIED → {@link ImeDisplaySettingsSpec#VALUE_DISABLED} (unknown
 *       prior; revert to disabled is the safe choice)</li>
 * </ul>
 */
public final class ImeSettingsReader {

    private final SystemSettingsProvider provider;

    /**
     * @param provider the settings provider to read through; must not be null
     * @throws IllegalArgumentException if {@code provider} is null
     */
    public ImeSettingsReader(SystemSettingsProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        this.provider = provider;
    }

    /**
     * Reads the current Secure values for {@code spec}'s decor + IME keys and returns a new
     * spec carrying those captured prior values (for a later revert).
     *
     * @param spec the target spec (typically from {@link ImeDisplaySettingsSpec#forDisplay});
     *             must not be null
     * @return a new spec with prior values filled in
     * @throws IllegalArgumentException if {@code spec} is null
     */
    public ImeDisplaySettingsSpec readPriors(ImeDisplaySettingsSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        int priorDecor = readPrior(spec.decorKey());
        int priorIme = readPrior(spec.imeKey());
        return spec.withPriorValues(priorDecor, priorIme);
    }

    private int readPrior(String key) {
        SettingsResult r = provider.getSecureInt(key);
        if (r.isOk()) {
            return r.value;
        }
        if (r.status == SettingsResult.Status.NOT_FOUND) {
            return ImeDisplaySettingsSpec.VALUE_UNSET;
        }
        // DENIED — treat as disabled (unknown prior; restore to disabled on revert).
        return ImeDisplaySettingsSpec.VALUE_DISABLED;
    }
}

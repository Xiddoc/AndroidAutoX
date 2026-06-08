package com.xiddoc.androidautox.autox.ime;

/**
 * Pure converter between the {@link ImeDisplaySettingsSpec} sentinel-int prior encoding
 * ({@link ImeDisplaySettingsSpec#VALUE_UNSET} for absent, otherwise the concrete 0/1 value)
 * and the boxed {@code Integer} encoding (null for absent) used by
 * {@link com.xiddoc.androidautox.autox.AutoXSettingsStore} prior-value setters.
 *
 * <h2>Why this exists (and is pure)</h2>
 * <p>{@code AutoXSettingsStore} already provides the reverse direction
 * ({@code getPriorShouldShow...OrUnset} → sentinel int). This is its symmetric counterpart: the
 * WS5 apply-time call-site reads the spec's prior ints (from
 * {@link com.xiddoc.androidautox.autox.ime.ImeSettingsReader}) and must persist them as boxed
 * {@code Integer}s so {@code null} faithfully means "the key was absent". Encapsulating the
 * {@code VALUE_UNSET → null} branch here (framework-free, 100% tested) keeps the
 * {@code AutoXScreen} glue free of mapping decisions.
 */
public final class ImePriorCodec {

    private ImePriorCodec() {
        // Static utility class; prevent instantiation.
    }

    /**
     * Converts a spec sentinel prior int into a boxed {@code Integer}.
     *
     * @param sentinelPrior {@link ImeDisplaySettingsSpec#VALUE_UNSET} for an absent key, or the
     *                      concrete prior value (e.g. {@link ImeDisplaySettingsSpec#VALUE_ENABLED}
     *                      / {@link ImeDisplaySettingsSpec#VALUE_DISABLED})
     * @return {@code null} when the prior was {@link ImeDisplaySettingsSpec#VALUE_UNSET},
     *         otherwise the boxed value
     */
    public static Integer toBoxedPrior(int sentinelPrior) {
        return sentinelPrior == ImeDisplaySettingsSpec.VALUE_UNSET
                ? null : Integer.valueOf(sentinelPrior);
    }
}

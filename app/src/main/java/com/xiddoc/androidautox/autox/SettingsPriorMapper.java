package com.xiddoc.androidautox.autox;

import com.xiddoc.androidautox.autox.provider.SettingsResult;

/**
 * Pure helper that maps a {@link SettingsResult} (the outcome of reading a current settings
 * value through the privileged provider seam) onto a boxed {@code Integer} <em>prior</em>
 * value suitable for feeding the shared {@link com.xiddoc.androidautox.autox.provider.SettingsEntry}
 * model (and the WS3 {@link FreeformGlobalSettingsSpec#revertList} / {@code applyList} APIs).
 *
 * <h2>Why this exists (and is pure)</h2>
 * <p>The WS3 freeform/global revert distinguishes three states for each key:
 * <ul>
 *   <li><b>present</b> — the key had a concrete integer value; revert must
 *       {@code RESTORE_PRIOR} that value → mapped to a boxed {@code Integer}.</li>
 *   <li><b>absent</b> — the key was never set; revert must {@code WRITE_DEFAULT} (0) → mapped
 *       to {@code null}.</li>
 *   <li><b>unreadable / denied</b> — the provider could not read the key (e.g. privilege
 *       momentarily denied). The conservative, fail-safe choice is to treat it as
 *       <em>absent</em> ({@code null}) so revert writes the feature-off default rather than
 *       inventing a value to "restore". This mirrors {@link com.xiddoc.androidautox.autox.ime.ImeSettingsReader}'s
 *       DENIED→disabled handling.</li>
 * </ul>
 *
 * <p>This three-way branch is the only non-trivial mapping in the WS3 glue, so per the AutoX
 * "no decisions in excluded glue" rule it lives here, framework-free (no Android imports) and
 * 100% unit-tested, instead of being inlined into {@code AutoXScreen}.
 */
public final class SettingsPriorMapper {

    private SettingsPriorMapper() {
        // Static utility class; prevent instantiation.
    }

    /**
     * Maps a settings-read result onto a boxed prior value.
     *
     * @param result the {@link SettingsResult} returned by a {@code getGlobalInt}/{@code getSecureInt}
     *               read; may be {@code null}
     * @return the present integer value (for {@link SettingsResult.Status#OK}), or {@code null}
     *         when the key was absent ({@link SettingsResult.Status#NOT_FOUND}), unreadable
     *         ({@link SettingsResult.Status#DENIED}), or the result itself was {@code null}
     */
    public static Integer toPrior(SettingsResult result) {
        if (result == null) {
            return null;
        }
        if (result.isOk()) {
            return result.value;
        }
        // NOT_FOUND or DENIED: treat as absent → revert writes the feature-off default.
        return null;
    }
}

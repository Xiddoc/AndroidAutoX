package com.xiddoc.androidautox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Canonical list of the settings actions and which of them are developer-only.
 *
 * <p>Consumed by {@code MainActivity}, which wires each constant to its button, label and
 * handler. The on-screen layout itself is static XML ({@code scrollview_settings.xml}); this
 * enum does not own that layout's order or visibility — it records the set of actions and
 * their dev-only gating. Developer-only entries are gated on developer mode being enabled
 * (see {@link DevModeStore}); {@link #DEV_MODE_TOGGLE} is always shown (it is what turns
 * developer mode on/off).
 *
 * <p>This enum is deliberately pure: it references no Android {@code R.*} resource, so it
 * stays decoupled and plain-JUnit testable (and so JaCoCo-visible).
 */
public enum SettingAction {
    RESET_TWEAKS(false),
    AUTO_REAPPLY(false),
    AA_SETTINGS(false),
    NONDESTRUCTIVE_PATCH(false),
    AUTO_BACKUP_DBS(false),
    PHIXIT_APPLY_TEST(true),   // developer-only
    PHIXIT_DUMP_ALL(true),     // developer-only
    DEV_MODE_TOGGLE(false);    // always shown, conceptually last

    private final boolean devOnly;

    SettingAction(boolean devOnly) {
        this.devOnly = devOnly;
    }

    /** @return whether this entry is only visible when developer mode is enabled. */
    public boolean isDevOnly() {
        return devOnly;
    }

    /**
     * The ordered list of actions visible for the given developer-mode state. When
     * {@code devMode} is {@code false}, developer-only entries are excluded; the relative
     * order of the remaining entries is preserved.
     */
    public static List<SettingAction> visibleActions(boolean devMode) {
        List<SettingAction> out = new ArrayList<>();
        for (SettingAction action : values()) {
            if (devMode || !action.devOnly) {
                out.add(action);
            }
        }
        return Collections.unmodifiableList(out);
    }
}

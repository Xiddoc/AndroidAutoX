package com.xiddoc.androidautox.autox;

import com.xiddoc.androidautox.autox.provider.SettingsEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure, immutable specification of every {@code Settings.Global} key that AutoX must write
 * to enable arbitrary-app launch on its virtual display, and how to revert each key when
 * AutoX is disabled.
 *
 * <p>(Formerly {@code SecureSettingsSpec} — renamed because these keys live in the
 * {@code Settings.Global} namespace, not {@code Settings.Secure}.)
 *
 * <h2>Keys managed</h2>
 * <ul>
 *   <li>{@code force_resizable_activities} = 1 — tells the activity manager to treat every
 *       activity as resizable regardless of its manifest declaration. Without this flag,
 *       many non-game apps declare {@code resizeableActivity="false"} and the system refuses
 *       to launch them onto a secondary virtual display.
 *   <li>{@code enable_freeform_support} = 1 — enables the freeform windowing mode, a
 *       prerequisite for Android to honor
 *       {@code ActivityOptions.setLaunchBounds}/{@code setLaunchDisplayId} on devices that
 *       ship without freeform enabled by default (i.e. most non-desktop Android builds).
 * </ul>
 *
 * <h2>Shared entry model</h2>
 * <p>Each managed key is modeled as a shared {@link SettingsEntry}; the lists produced here
 * are written by the shared {@link com.xiddoc.androidautox.autox.provider.SettingsApplier}
 * (constructed with {@code Namespace.GLOBAL}). Revert strategy comes from whether the key
 * had a prior value: an absent key reverts to {@link SettingsEntry#DEFAULT_REVERT_VALUE},
 * an existing key restores its captured prior value.
 *
 * <h2>No Android imports</h2>
 * <p>This class is framework-free and is <em>not</em> in {@code jacocoExclusions}; it must
 * remain at 100% line + branch coverage.
 */
public final class FreeformGlobalSettingsSpec {

    /**
     * {@code Settings.Global} key that forces every activity to be treated as resizable.
     */
    public static final String KEY_FORCE_RESIZABLE = "force_resizable_activities";

    /**
     * {@code Settings.Global} key that enables freeform windowing mode.
     */
    public static final String KEY_ENABLE_FREEFORM = "enable_freeform_support";

    /** The value written to each key when AutoX is enabled: {@code 1} (feature on). */
    public static final int ENABLED_VALUE = 1;

    /**
     * Builds the ordered list of entries that must be applied to enable AutoX's
     * resizable/freeform launch support, given the current (pre-enable) values.
     *
     * <p>Apply order: {@code force_resizable_activities} first, then
     * {@code enable_freeform_support}.
     *
     * @param priorForceResizable current value of {@code force_resizable_activities},
     *                            or {@code null} if absent
     * @param priorEnableFreeform current value of {@code enable_freeform_support},
     *                            or {@code null} if absent
     * @return an unmodifiable ordered list of {@link SettingsEntry} to apply
     */
    public static List<SettingsEntry> applyList(Integer priorForceResizable,
                                                Integer priorEnableFreeform) {
        List<SettingsEntry> list = new ArrayList<>(2);
        list.add(SettingsEntry.of(KEY_FORCE_RESIZABLE, ENABLED_VALUE, priorForceResizable));
        list.add(SettingsEntry.of(KEY_ENABLE_FREEFORM, ENABLED_VALUE, priorEnableFreeform));
        return Collections.unmodifiableList(list);
    }

    /**
     * Builds the ordered list of entries to revert when AutoX is disabled.
     *
     * <p>Revert order is the reverse of apply order: {@code enable_freeform_support} first,
     * then {@code force_resizable_activities}.
     *
     * @param priorForceResizable prior value of {@code force_resizable_activities}, or null
     * @param priorEnableFreeform prior value of {@code enable_freeform_support}, or null
     * @return an unmodifiable ordered list of {@link SettingsEntry} to revert
     */
    public static List<SettingsEntry> revertList(Integer priorForceResizable,
                                                 Integer priorEnableFreeform) {
        List<SettingsEntry> list = new ArrayList<>(2);
        list.add(SettingsEntry.of(KEY_ENABLE_FREEFORM, ENABLED_VALUE, priorEnableFreeform));
        list.add(SettingsEntry.of(KEY_FORCE_RESIZABLE, ENABLED_VALUE, priorForceResizable));
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns the ordered list of {@code Settings.Global} keys AutoX manages for
     * resizable/freeform launch: [{@link #KEY_FORCE_RESIZABLE}, {@link #KEY_ENABLE_FREEFORM}].
     */
    public static List<String> managedKeys() {
        List<String> keys = new ArrayList<>(2);
        keys.add(KEY_FORCE_RESIZABLE);
        keys.add(KEY_ENABLE_FREEFORM);
        return Collections.unmodifiableList(keys);
    }

    private FreeformGlobalSettingsSpec() {
        // Static utility class; prevent instantiation.
    }
}

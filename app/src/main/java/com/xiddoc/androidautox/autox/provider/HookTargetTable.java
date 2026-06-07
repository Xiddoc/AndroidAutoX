package com.xiddoc.androidautox.autox.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-SDK table of {@link HookDescriptor}s the LSPosed module installs, with a pure
 * {@link #resolveFor(int)} resolver.
 *
 * <p>No Android imports — fully unit testable, including the unknown-SDK branch. The
 * Xposed glue (excluded) calls {@link #resolveFor(int)} with {@code Build.VERSION.SDK_INT}
 * and version-guards off the returned {@link HookTargetSet#resolved} flag, never throwing
 * if the SDK is unknown.
 *
 * <p>Covered SDKs: 31 (Android 12), 32 (12L), 33 (13), 34 (14). The class/method names
 * below are the {@code system_server} internals each AndroidAutoX privileged hook
 * targets; they are intentionally stable across these four releases, so the same
 * descriptor set is reused — but the table is keyed per-SDK so a future release that
 * renames a method can diverge without touching the resolver.
 */
public final class HookTargetTable {

    private static final String DISPLAY_MANAGER_SERVICE =
            "com.android.server.display.DisplayManagerService";
    private static final String INPUT_MANAGER_SERVICE =
            "com.android.server.input.InputManagerService";
    private static final String DISPLAY_WINDOW_SETTINGS =
            "com.android.server.wm.DisplayWindowSettings";
    private static final String ACTIVITY_TASK_MANAGER_SERVICE =
            "com.android.server.wm.ActivityTaskManagerService";

    /** Lowest SDK this table knows about (Android 12). */
    public static final int MIN_SDK = 31;
    /** Highest SDK this table knows about (Android 14). */
    public static final int MAX_SDK = 34;

    /** Immutable SDK_INT -> descriptor-list map, built once at class load. */
    private static final Map<Integer, List<HookDescriptor>> TABLE;

    static {
        Map<Integer, List<HookDescriptor>> table = new TreeMap<Integer, List<HookDescriptor>>();
        for (int sdk = MIN_SDK; sdk <= MAX_SDK; sdk++) {
            table.put(sdk, baseDescriptors());
        }
        TABLE = Collections.unmodifiableMap(table);
    }

    private HookTargetTable() {
    }

    /** The descriptor set shared by SDK 31-34. */
    private static List<HookDescriptor> baseDescriptors() {
        List<HookDescriptor> list = new ArrayList<HookDescriptor>();
        list.add(new HookDescriptor(HookDescriptor.Target.DISPLAY_TRUSTED_FLAG,
                DISPLAY_MANAGER_SERVICE, "createVirtualDisplayInternal"));
        list.add(new HookDescriptor(HookDescriptor.Target.INPUT_INJECT_DISPLAY,
                INPUT_MANAGER_SERVICE, "injectInputEvent"));
        list.add(new HookDescriptor(HookDescriptor.Target.DISPLAY_SHOULD_SHOW_IME,
                DISPLAY_WINDOW_SETTINGS, "shouldShowImeLocked"));
        list.add(new HookDescriptor(HookDescriptor.Target.DISPLAY_SHOULD_SHOW_SYSTEM_DECORS,
                DISPLAY_WINDOW_SETTINGS, "shouldShowSystemDecorsLocked"));
        list.add(new HookDescriptor(HookDescriptor.Target.LAUNCH_ON_DISPLAY,
                ACTIVITY_TASK_MANAGER_SERVICE, "isCallerAllowedToLaunchOnDisplay"));
        return Collections.unmodifiableList(list);
    }

    /**
     * Resolves the hook-target set for the given SDK level.
     *
     * @param sdkInt the live {@code Build.VERSION.SDK_INT}
     * @return a {@link HookTargetSet} with {@link HookTargetSet#resolved} {@code true} and
     *         the descriptors for a known SDK, or an empty unresolved set
     *         ({@code resolved == false}) for any SDK outside {@link #MIN_SDK}..{@link #MAX_SDK}
     */
    public static HookTargetSet resolveFor(int sdkInt) {
        List<HookDescriptor> descriptors = TABLE.get(sdkInt);
        if (descriptors == null) {
            return HookTargetSet.unresolved(sdkInt);
        }
        return new HookTargetSet(sdkInt, true, descriptors);
    }

    /** @return the sorted set of SDK levels this table covers. */
    public static List<Integer> supportedSdks() {
        return Collections.unmodifiableList(new ArrayList<Integer>(TABLE.keySet()));
    }

    /** @return {@code true} iff {@code sdkInt} is within the supported range. */
    public static boolean isSupported(int sdkInt) {
        return TABLE.containsKey(sdkInt);
    }
}

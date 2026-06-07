package com.xiddoc.androidautox.autox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of default suggested target apps that can be streamed onto an AutoX
 * virtual display (the isolated head-unit projection surface).
 *
 * <p>The list mirrors the pattern of {@code TweakRegistry}: a small, curated,
 * statically-declared set of well-known entries that serves both as a sensible
 * default for the UI picker and as the source of truth for {@link #isKnown} and
 * {@link #byPackage} look-ups.
 *
 * <p>All methods are {@code static}; this class cannot be instantiated.
 */
public final class AutoXAppRegistry {

    /** Instagram — social / media streaming app. */
    public static final AutoXTargetApp INSTAGRAM =
            new AutoXTargetApp("com.instagram.android", "Instagram");

    /** YouTube — video streaming app. */
    public static final AutoXTargetApp YOUTUBE =
            new AutoXTargetApp("com.google.android.youtube", "YouTube");

    /** Google Maps — navigation / mapping app. */
    public static final AutoXTargetApp GOOGLE_MAPS =
            new AutoXTargetApp("com.google.android.apps.maps", "Google Maps");

    /**
     * Claude (Anthropic) — AI assistant app; placeholder package name used
     * until the app is publicly released on Android.
     */
    public static final AutoXTargetApp CLAUDE =
            new AutoXTargetApp("com.anthropic.claude", "Claude");

    /** Immutable default list returned by {@link #defaults()}. */
    private static final List<AutoXTargetApp> DEFAULTS;

    static {
        List<AutoXTargetApp> list = new ArrayList<AutoXTargetApp>();
        list.add(INSTAGRAM);
        list.add(YOUTUBE);
        list.add(GOOGLE_MAPS);
        list.add(CLAUDE);
        DEFAULTS = Collections.unmodifiableList(list);
    }

    private AutoXAppRegistry() {
    }

    /**
     * Returns an unmodifiable view of the curated default target-app list.
     *
     * <p>The returned list is stable (same size and order across calls) and
     * backed by the module-level constant — callers must not attempt to mutate it.
     *
     * @return unmodifiable, non-null list of default {@link AutoXTargetApp} entries.
     */
    public static List<AutoXTargetApp> defaults() {
        return DEFAULTS;
    }

    /**
     * Returns {@code true} if {@code packageName} matches one of the default registry
     * entries (case-sensitive, exact match).
     *
     * @param packageName the Android package name to look up; {@code null} returns false.
     * @return {@code true} iff the package name is in the default registry.
     */
    public static boolean isKnown(String packageName) {
        if (packageName == null) return false;
        for (AutoXTargetApp app : DEFAULTS) {
            if (app.packageName.equals(packageName)) return true;
        }
        return false;
    }

    /**
     * Finds an {@link AutoXTargetApp} by its exact package name.
     *
     * @param packageName the Android package name to search for; {@code null} returns
     *                    {@code null}.
     * @return the matching entry, or {@code null} if not found.
     */
    public static AutoXTargetApp byPackage(String packageName) {
        if (packageName == null) return null;
        for (AutoXTargetApp app : DEFAULTS) {
            if (app.packageName.equals(packageName)) return app;
        }
        return null;
    }
}

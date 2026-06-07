package com.xiddoc.androidautox;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure, side-effect-free classifier that decides, per package, <em>how</em> (or
 * whether) an app can run on Android Auto.
 *
 * <p>Three categories, in strict precedence order:
 * <ol>
 *   <li>{@link Category#NATIVE_AUTO} — the app declares the
 *       {@code com.google.android.gms.car.application} metadata marker, so Google
 *       built it for Auto and the head unit can draw it natively.</li>
 *   <li>{@link Category#MIRROR_SHIM} — one of the known screen-mirroring /
 *       streaming shims ({@link #MIRROR_APPS}); it can appear on Auto only by
 *       mirroring the phone screen, not natively.</li>
 *   <li>{@link Category#NEEDS_BRIDGE} — everything else; whitelisting it does not
 *       give the head unit anything renderable yet.</li>
 * </ol>
 */
public final class AppCompatibilityClassifier {

    private AppCompatibilityClassifier() { }

    /** How an app can (or cannot) appear on Android Auto. */
    public enum Category {
        NATIVE_AUTO,
        MIRROR_SHIM,
        NEEDS_BRIDGE
    }

    /**
     * Known screen-mirroring / streaming shim packages. These are not natively
     * Auto-renderable, but the ecosystem uses them to project the phone screen.
     */
    public static final Set<String> MIRROR_APPS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "ru.inceptive.screentwoauto",
                    "com.garage.aastream",
                    "me.aap.fermata.auto",
                    "com.github.slashmax.aamirror",
                    "com.github.slashmax.aamirror_plus",
                    "org.openauto.aautolauncher",
                    "com.mqbcoding.stats",
                    "com.google.android.kk",
                    "com.google.android.kk2")));

    /**
     * Whether a raw {@code com.google.android.gms.car.application} metadata value
     * indicates a declared Auto app. A real Auto app points the marker at an XML
     * resource id (a non-zero int); a missing key reads back as {@code 0}.
     */
    public static boolean hasCarMetadata(int markerValue) {
        return markerValue != 0;
    }

    /**
     * Classifies a package given whether it declares the Auto car-app metadata.
     * Precedence: declared metadata wins, then the known mirror list, else the app
     * needs a bridge.
     */
    public static Category classify(String pkg, boolean declaresCarMetadata) {
        if (declaresCarMetadata) {
            return Category.NATIVE_AUTO;
        }
        if (MIRROR_APPS.contains(pkg)) {
            return Category.MIRROR_SHIM;
        }
        return Category.NEEDS_BRIDGE;
    }

    /** Whether the package is one of the known mirror/streaming AA shims. */
    public static boolean isKnownAa(String pkg) {
        return MIRROR_APPS.contains(pkg);
    }
}

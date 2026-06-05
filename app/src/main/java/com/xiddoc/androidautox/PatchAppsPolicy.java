package com.xiddoc.androidautox;

import java.util.regex.Pattern;

/**
 * Pure, Android-free decision logic for the "patch apps for Android Auto" flow
 * ({@link MainActivity#patchforapps()}).
 *
 * <p>Keeping this here (no Android imports) makes it unit-testable with plain JUnit. The
 * Android glue in {@code MainActivity} (PackageManager lookups, root shell calls, UI) stays
 * thin and delegates the actual decisions to the static methods below.
 *
 * <p>See {@code docs/patch-apps-installer-analysis.md} for the full rationale: why the
 * destructive uninstall/reinstall loop is still the default, what the experimental
 * non-destructive ({@code pm set-installer}) mode does, and the installing-vs-initiating
 * package caveat that makes set-installer a strictly weaker spoof.
 */
public final class PatchAppsPolicy {

    private PatchAppsPolicy() {
    }

    /**
     * Android package names are restricted to letters, digits, dots and underscores. This
     * is intentionally stricter than the platform's full grammar (we also accept names that
     * are technically malformed, e.g. a leading dot) because the only job here is to refuse
     * anything that could break out of a shell command: spaces, {@code ;}, {@code &},
     * {@code $()}, backticks, quotes, newlines, etc. Every selected package is validated with
     * this before it is interpolated into any root command.
     */
    private static final Pattern PACKAGE_NAME = Pattern.compile("^[A-Za-z0-9._]+$");

    /**
     * True iff {@code pkg} is a safe, shell-injection-free package name.
     *
     * @param pkg candidate package name (may be {@code null})
     * @return {@code true} only for non-null, non-empty names matching {@code [A-Za-z0-9._]+}
     */
    public static boolean isValidPackageName(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        return PACKAGE_NAME.matcher(pkg).matches();
    }

    /** Which patching strategy {@link MainActivity#patchforapps()} should run per app. */
    public enum Mode {
        /**
         * Default: move the APK aside, {@code pm uninstall}, then {@code pm install} with
         * {@code -i "com.android.vending"} so both the installing and initiating package are
         * re-stamped to the Play Store. Destructive (the app is briefly uninstalled).
         */
        DESTRUCTIVE_REINSTALL,
        /**
         * Experimental, opt-in: leave the app installed and only run
         * {@code pm set-installer <pkg> com.android.vending}. This changes
         * {@code getInstallingPackageName()} but NOT the immutable
         * {@code getInitiatingPackageName()}, so it is a strictly weaker spoof — safe only if
         * Gearhead reads the installing field. See the docs file.
         */
        SET_INSTALLER
    }

    /**
     * Maps the experimental opt-in flag to a patching {@link Mode}. Off (default) keeps the
     * historical destructive behaviour; on selects the non-destructive set-installer path.
     *
     * @param experimentalNonDestructive value of the {@code experimental_nondestructive_patch}
     *                                    preference (default {@code false})
     */
    public static Mode modeFor(boolean experimentalNonDestructive) {
        return experimentalNonDestructive ? Mode.SET_INSTALLER : Mode.DESTRUCTIVE_REINSTALL;
    }

    /**
     * True if an app is a split APK (has one or more split source dirs in addition to the
     * base). The destructive reinstall only moves/installs the single base APK, so a split
     * app would be corrupted (left uninstalled, or reinstalled missing its splits). Callers
     * use this to fail such an entry gracefully instead.
     *
     * @param splitSourceDirs {@code ApplicationInfo.splitSourceDirs} (may be {@code null})
     * @return {@code true} when at least one split source dir is present
     */
    public static boolean isSplitApk(String[] splitSourceDirs) {
        return splitSourceDirs != null && splitSourceDirs.length > 0;
    }
}

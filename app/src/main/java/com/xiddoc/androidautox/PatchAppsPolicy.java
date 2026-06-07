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
 * <p>The app is patched non-destructively: {@code pm set-installer <pkg>}
 * {@link #PLAY_STORE_PKG} re-stamps the installing package without ever uninstalling the app,
 * so no user data is lost. The old destructive uninstall/reinstall path was removed in
 * favour of this safer method; see
 * {@code docs/patch-apps-installer-analysis.md} for the rationale and the
 * installing-vs-initiating-package caveat.
 */
public final class PatchAppsPolicy {

    private PatchAppsPolicy() {
    }

    /**
     * The Play Store package the set-installer path re-stamps as the installing package.
     * Centralised so the patch path, the revert path, and the docs all reference one constant.
     */
    public static final String PLAY_STORE_PKG = "com.android.vending";

    // --- pm output parsing (pure) ---------------------------------------------

    /**
     * Decides whether a {@code pm} command (set-installer) actually succeeded by SCANNING ITS
     * OUTPUT, not its exit code. {@code pm} is notorious for printing
     * {@code Failure [...]} to stdout while still exiting 0, so an exit-code check would treat a
     * failed command as success.
     *
     * <p>Success requires at least one output line whose trimmed value is exactly
     * {@code Success} (case-sensitive, the literal {@code pm} prints). {@code null}, empty,
     * whitespace-only, and any {@code Failure}/error output are all treated as failure.
     *
     * @param outputLines the combined stdout (and/or stderr) lines of the {@code pm} command
     * @return {@code true} only when an exact {@code Success} token is present
     */
    public static boolean pmSucceeded(java.util.List<String> outputLines) {
        if (outputLines == null) {
            return false;
        }
        for (String line : outputLines) {
            if (line != null && "Success".equals(line.trim())) {
                return true;
            }
        }
        return false;
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
}

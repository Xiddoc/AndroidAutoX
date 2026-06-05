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
     * The Play Store package both patch paths re-stamp as the installer (and, in the
     * destructive path, the initiating package). Centralised so the destructive reinstall,
     * the set-installer path, and the enum doc all reference one constant.
     */
    public static final String PLAY_STORE_PKG = "com.android.vending";

    /**
     * Path of the temporary copy of {@code pkg}'s base APK used by the destructive reinstall.
     * After {@code pm uninstall} this is the ONLY surviving copy of the app, so callers must
     * confirm the package is reinstalled before deleting it.
     */
    public static String tmpApkPath(String pkg) {
        return "/data/local/tmp/tmpapk" + pkg + ".apk";
    }

    // --- pm output parsing (pure) ---------------------------------------------

    /**
     * Decides whether a {@code pm} command (install / uninstall / set-installer) actually
     * succeeded by SCANNING ITS OUTPUT, not its exit code. {@code pm} is notorious for
     * printing {@code Failure [INSTALL_FAILED_X]} to stdout while still exiting 0, so an
     * exit-code check would treat a failed uninstall/install as success and could delete the
     * only surviving APK copy.
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

    // --- shell argument quoting (pure) ----------------------------------------

    /**
     * Wraps {@code arg} in single quotes for safe shell interpolation, escaping any embedded
     * single quote with the classic {@code '\''} idiom (close-quote, escaped quote, re-open).
     * Inside single quotes the shell treats {@code $}, backticks, spaces, etc. literally, so
     * only the single quote itself needs special handling.
     *
     * <p>This is only meant for OS-controlled values (e.g. an APK path PackageManager handed
     * us); user-selected package names are validated by {@link #isValidPackageName(String)}
     * before interpolation, but quoting them too is cheap defence-in-depth.
     */
    public static String quoteShellArg(String arg) {
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    // --- destructive reinstall sequencing (pure) ------------------------------

    /** The ordered steps of the destructive uninstall/reinstall, in the order they run. */
    public enum Step {
        /** {@code cp} the base APK aside to {@link #tmpApkPath(String)}. */
        COPY,
        /** {@code pm uninstall} the package (the temp APK becomes the only copy). */
        UNINSTALL,
        /** {@code pm install} the temp APK, re-stamping the Play Store. */
        INSTALL,
        /** Best-effort {@code pm install} of the temp APK to undo a failed {@link #INSTALL}. */
        ROLLBACK
    }

    /**
     * What {@link MainActivity#patchAppDestructive} should do after a {@link Step} reports a
     * given success/failure. This makes the riskiest branching (where the only APK copy may be
     * deleted) a pure, unit-tested decision instead of inline conditionals.
     */
    public enum NextAction {
        /** Run the next step in sequence ({@code COPY ok -> UNINSTALL}, {@code UNINSTALL ok -> INSTALL}). */
        PROCEED,
        /** Copy failed: nothing was changed; abort and leave the app untouched (no temp APK to keep). */
        ABORT_APP_UNTOUCHED,
        /** Uninstall failed: app is still installed; abort and delete the (now-redundant) temp APK. */
        ABORT_DELETE_TMP,
        /** Install failed: attempt the {@link Step#ROLLBACK} reinstall before deciding. */
        ATTEMPT_ROLLBACK,
        /** Done successfully (install ok, or rollback ok): delete the temp APK once presence is confirmed. */
        DONE_DELETE_TMP,
        /** Rollback also failed: KEEP the temp APK so the user can recover the only surviving copy. */
        FAILED_KEEP_TMP
    }

    /**
     * Pure transition for the destructive reinstall: given the step that just ran and whether
     * it succeeded (already decided by {@link #pmSucceeded} for the {@code pm} steps), returns
     * the next action. Encodes the safety rules:
     * <ul>
     *   <li>copy fails -&gt; app untouched, no temp to keep;</li>
     *   <li>uninstall fails -&gt; app still installed, drop the temp;</li>
     *   <li>install fails -&gt; try a rollback;</li>
     *   <li>install ok / rollback ok -&gt; delete the temp (after confirming the package is back);</li>
     *   <li>rollback fails -&gt; KEEP the temp (only surviving copy).</li>
     * </ul>
     *
     * <p>The {@code DONE_DELETE_TMP} action is advisory: the caller must still positively
     * confirm the package is installed (via PackageManager) before any delete.
     */
    public static NextAction nextAction(Step step, boolean success) {
        switch (step) {
            case COPY:
                return success ? NextAction.PROCEED : NextAction.ABORT_APP_UNTOUCHED;
            case UNINSTALL:
                return success ? NextAction.PROCEED : NextAction.ABORT_DELETE_TMP;
            case INSTALL:
                return success ? NextAction.DONE_DELETE_TMP : NextAction.ATTEMPT_ROLLBACK;
            case ROLLBACK:
            default:
                // ROLLBACK is the only remaining Step (closed enum); default == ROLLBACK.
                return success ? NextAction.DONE_DELETE_TMP : NextAction.FAILED_KEEP_TMP;
        }
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
         * {@code -i} {@link #PLAY_STORE_PKG} so both the installing and initiating package are
         * re-stamped to the Play Store. Destructive (the app is briefly uninstalled).
         */
        DESTRUCTIVE_REINSTALL,
        /**
         * Experimental, opt-in: leave the app installed and only run
         * {@code pm set-installer <pkg>} {@link #PLAY_STORE_PKG}. This changes
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

package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JUnit tests for the pure {@link PatchAppsPolicy} decision logic (no Android deps):
 * package-name validation (the shell-injection guard), the destructive-vs-set-installer mode
 * selection, and the split-APK predicate.
 */
public class PatchAppsPolicyTest {

    // --- isValidPackageName: accepts well-formed names ---------------------

    @Test
    public void validPackageNames_accepted() {
        assertTrue(PatchAppsPolicy.isValidPackageName("com.android.vending"));
        assertTrue(PatchAppsPolicy.isValidPackageName("com.google.android.projection.gearhead"));
        assertTrue(PatchAppsPolicy.isValidPackageName("a"));
        assertTrue(PatchAppsPolicy.isValidPackageName("com.example.App_2"));
        assertTrue(PatchAppsPolicy.isValidPackageName("123numericish"));
    }

    // --- isValidPackageName: rejects injection / malformed ----------------

    @Test
    public void nullAndEmpty_rejected() {
        assertFalse(PatchAppsPolicy.isValidPackageName(null));
        assertFalse(PatchAppsPolicy.isValidPackageName(""));
    }

    @Test
    public void spaces_rejected() {
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example app"));
        assertFalse(PatchAppsPolicy.isValidPackageName(" com.example"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example "));
    }

    @Test
    public void shellMetacharacters_rejected() {
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example;rm -rf /"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example && reboot"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example|cat"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example$(id)"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example`id`"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example>out"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example'quote"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example\"quote"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example\nnewline"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example/slash"));
        assertFalse(PatchAppsPolicy.isValidPackageName("com.example-dash"));
    }

    // --- modeFor: off -> destructive, on -> set-installer -----------------

    @Test
    public void modeOff_isDestructive() {
        assertEquals(PatchAppsPolicy.Mode.DESTRUCTIVE_REINSTALL,
                PatchAppsPolicy.modeFor(false));
    }

    @Test
    public void modeOn_isSetInstaller() {
        assertEquals(PatchAppsPolicy.Mode.SET_INSTALLER,
                PatchAppsPolicy.modeFor(true));
    }

    // --- isSplitApk -------------------------------------------------------

    @Test
    public void nullOrEmptySplits_isNotSplit() {
        assertFalse(PatchAppsPolicy.isSplitApk(null));
        assertFalse(PatchAppsPolicy.isSplitApk(new String[0]));
    }

    @Test
    public void presentSplits_isSplit() {
        assertTrue(PatchAppsPolicy.isSplitApk(new String[]{"/data/app/base/split_config.en.apk"}));
        assertTrue(PatchAppsPolicy.isSplitApk(new String[]{"a", "b"}));
    }

    // --- pmSucceeded: judge pm by output, not exit code -------------------

    @Test
    public void pmSucceeded_exactSuccessToken() {
        assertTrue(PatchAppsPolicy.pmSucceeded(java.util.Collections.singletonList("Success")));
        // pm sometimes pads with whitespace; the trimmed line must equal "Success".
        assertTrue(PatchAppsPolicy.pmSucceeded(java.util.Collections.singletonList("  Success  ")));
    }

    @Test
    public void pmSucceeded_failureIsFailure() {
        assertFalse(PatchAppsPolicy.pmSucceeded(
                java.util.Collections.singletonList("Failure [INSTALL_FAILED_VERSION_DOWNGRADE]")));
        assertFalse(PatchAppsPolicy.pmSucceeded(
                java.util.Collections.singletonList("Failure [DELETE_FAILED_INTERNAL_ERROR]")));
    }

    @Test
    public void pmSucceeded_mixedLines_successWins() {
        assertTrue(PatchAppsPolicy.pmSucceeded(
                java.util.Arrays.asList("Performing Streamed Install", "Success")));
    }

    @Test
    public void pmSucceeded_mixedLines_noSuccessTokenIsFailure() {
        // "Success" embedded in a larger line is NOT an exact token -> failure.
        assertFalse(PatchAppsPolicy.pmSucceeded(
                java.util.Arrays.asList("Install Successful", "done")));
    }

    @Test
    public void pmSucceeded_emptyNullWhitespace() {
        assertFalse(PatchAppsPolicy.pmSucceeded(null));
        assertFalse(PatchAppsPolicy.pmSucceeded(java.util.Collections.<String>emptyList()));
        assertFalse(PatchAppsPolicy.pmSucceeded(java.util.Arrays.asList("", "   ", "\t")));
        assertFalse(PatchAppsPolicy.pmSucceeded(java.util.Arrays.asList((String) null)));
    }

    // --- quoteShellArg: single-quote wrapping + escaping ------------------

    @Test
    public void quoteShellArg_plain() {
        assertEquals("'/data/app/base.apk'",
                PatchAppsPolicy.quoteShellArg("/data/app/base.apk"));
    }

    @Test
    public void quoteShellArg_space() {
        assertEquals("'/data/app/My App.apk'",
                PatchAppsPolicy.quoteShellArg("/data/app/My App.apk"));
    }

    @Test
    public void quoteShellArg_embeddedSingleQuote() {
        // a'b -> 'a'\''b'  (close, escaped quote, reopen)
        assertEquals("'a'\\''b'", PatchAppsPolicy.quoteShellArg("a'b"));
    }

    @Test
    public void quoteShellArg_dollarAndBacktickAreLiteralInsideQuotes() {
        assertEquals("'$(reboot)'", PatchAppsPolicy.quoteShellArg("$(reboot)"));
        assertEquals("'`id`'", PatchAppsPolicy.quoteShellArg("`id`"));
    }

    // --- PLAY_STORE_PKG / tmpApkPath --------------------------------------

    @Test
    public void playStorePkg_isVending() {
        assertEquals("com.android.vending", PatchAppsPolicy.PLAY_STORE_PKG);
    }

    @Test
    public void tmpApkPath_format() {
        assertEquals("/data/local/tmp/tmpapkcom.example.app.apk",
                PatchAppsPolicy.tmpApkPath("com.example.app"));
    }

    // --- nextAction: the rollback sequencing outcome tree -----------------

    @Test
    public void nextAction_copy() {
        assertEquals(PatchAppsPolicy.NextAction.PROCEED,
                PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.COPY, true));
        assertEquals(PatchAppsPolicy.NextAction.ABORT_APP_UNTOUCHED,
                PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.COPY, false));
    }

    @Test
    public void nextAction_uninstall() {
        assertEquals(PatchAppsPolicy.NextAction.PROCEED,
                PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.UNINSTALL, true));
        assertEquals(PatchAppsPolicy.NextAction.ABORT_DELETE_TMP,
                PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.UNINSTALL, false));
    }

    @Test
    public void nextAction_install() {
        assertEquals(PatchAppsPolicy.NextAction.DONE_DELETE_TMP,
                PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.INSTALL, true));
        assertEquals(PatchAppsPolicy.NextAction.ATTEMPT_ROLLBACK,
                PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.INSTALL, false));
    }

    @Test
    public void nextAction_rollback() {
        assertEquals(PatchAppsPolicy.NextAction.DONE_DELETE_TMP,
                PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.ROLLBACK, true));
        // Rollback failed -> KEEP the only surviving copy.
        assertEquals(PatchAppsPolicy.NextAction.FAILED_KEEP_TMP,
                PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.ROLLBACK, false));
    }
}

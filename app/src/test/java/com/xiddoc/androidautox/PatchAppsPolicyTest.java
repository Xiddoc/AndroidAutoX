package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JUnit tests for the pure {@link PatchAppsPolicy} decision logic (no Android deps):
 * package-name validation (the shell-injection guard) and the {@code pm} output parser used by
 * the non-destructive set-installer patch path.
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

    // --- PLAY_STORE_PKG ---------------------------------------------------

    @Test
    public void playStorePkg_isVending() {
        assertEquals("com.android.vending", PatchAppsPolicy.PLAY_STORE_PKG);
    }
}

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
}

package com.xiddoc.androidautox;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pure JUnit tests for {@link GmsCommandBuilder} — the shell-command-string builders
 * and pure decisions extracted out of {@code MainActivity}'s tweak handlers. No Android
 * runtime needed.
 *
 * <p>Each assertion pins the EXACT string previously inlined in {@code MainActivity} so a
 * behavioural drift in the command text is caught.
 */
public class GmsCommandBuilderTest {

    private static final String DB =
            "/data/data/com.google.android.gms/databases/phenotype.db";

    // ------------------------------------------------------------------
    // Process / SELinux control
    // ------------------------------------------------------------------

    @Test
    public void killAll_buildsAmKillAll() {
        assertEquals("am kill all com.example", GmsCommandBuilder.killAll("com.example"));
    }

    @Test
    public void forceStop_buildsAmForceStop() {
        assertEquals("am force-stop com.example", GmsCommandBuilder.forceStop("com.example"));
    }

    @Test
    public void killGms_targetsGmsPackage() {
        assertEquals("am kill all com.google.android.gms", GmsCommandBuilder.killGms());
    }

    @Test
    public void forceStopGms_targetsGmsPackage() {
        assertEquals("am force-stop com.google.android.gms", GmsCommandBuilder.forceStopGms());
    }

    @Test
    public void enableGms_buildsPmEnable() {
        assertEquals("pm enable com.google.android.gms", GmsCommandBuilder.enableGms());
    }

    @Test
    public void getEnforce_isGetenforce() {
        assertEquals("getenforce", GmsCommandBuilder.getEnforce());
    }

    @Test
    public void setEnforce_permissive_isZero() {
        assertEquals("setenforce 0", GmsCommandBuilder.setEnforce(false));
    }

    @Test
    public void setEnforce_enforcing_isOne() {
        assertEquals("setenforce 1", GmsCommandBuilder.setEnforce(true));
    }

    // shouldRestoreEnforcing — both branches of the && plus the value branch

    @Test
    public void shouldRestoreEnforcing_permissive_true() {
        assertTrue(GmsCommandBuilder.shouldRestoreEnforcing("Permissive"));
    }

    @Test
    public void shouldRestoreEnforcing_lowercasePermissive_true() {
        assertTrue(GmsCommandBuilder.shouldRestoreEnforcing("permissive"));
    }

    @Test
    public void shouldRestoreEnforcing_surroundingWhitespace_true() {
        assertTrue(GmsCommandBuilder.shouldRestoreEnforcing("  Permissive \n"));
    }

    @Test
    public void shouldRestoreEnforcing_enforcing_false() {
        assertFalse(GmsCommandBuilder.shouldRestoreEnforcing("Enforcing"));
    }

    @Test
    public void shouldRestoreEnforcing_null_false() {
        assertFalse(GmsCommandBuilder.shouldRestoreEnforcing(null));
    }

    @Test
    public void shouldRestoreEnforcing_empty_false() {
        assertFalse(GmsCommandBuilder.shouldRestoreEnforcing(""));
    }

    // ------------------------------------------------------------------
    // Phenotype DB ownership
    // ------------------------------------------------------------------

    @Test
    public void statPhenotypeOwner_matchesInlineCommand() {
        assertEquals("stat -c \"%U\" " + DB, GmsCommandBuilder.statPhenotypeOwner());
    }

    @Test
    public void chownPhenotypeToRoot_matchesInlineCommand() {
        assertEquals("chown root " + DB, GmsCommandBuilder.chownPhenotypeToRoot());
    }

    @Test
    public void chownPhenotypeTo_insertsOwner() {
        assertEquals("chown u0_a123 " + DB, GmsCommandBuilder.chownPhenotypeTo("u0_a123"));
    }

    @Test
    public void removePhenotypeCache_matchesInlineCommand() {
        assertEquals("rm -rf /data/data/com.google.android.gms/files/phenotype",
                GmsCommandBuilder.removePhenotypeCache());
    }

    // ------------------------------------------------------------------
    // App patching
    // ------------------------------------------------------------------

    @Test
    public void pmPath_buildsPmPath() {
        assertEquals("pm path com.foo", GmsCommandBuilder.pmPath("com.foo"));
    }

    @Test
    public void parsePmPathResult_extractsAfterLastColon() {
        // mirrors substring(lastIndexOf(":") + 1) on a label-wrapped pm-path line
        String wrapped = "\tInputStream:\n\t\tpackage:/data/app/com.foo/base.apk";
        assertEquals("/data/app/com.foo/base.apk",
                GmsCommandBuilder.parsePmPathResult(wrapped));
    }

    @Test
    public void parsePmPathResult_noColon_returnsWholeString() {
        // lastIndexOf returns -1 → +1 = 0 → whole string
        assertEquals("nopath", GmsCommandBuilder.parsePmPathResult("nopath"));
    }

    @Test
    public void parsePmPathResult_doesNotTrim() {
        // pinned: extraction is byte-for-byte (no trim) to match the old inline path
        assertEquals(" spaced.apk ", GmsCommandBuilder.parsePmPathResult("x: spaced.apk "));
    }

    @Test
    public void parsePmPathResult_null_returnsEmpty() {
        assertEquals("", GmsCommandBuilder.parsePmPathResult(null));
    }

    @Test
    public void tmpApkPath_buildsTempPath() {
        assertEquals("/data/local/tmp/tmpapkcom.foo.apk",
                GmsCommandBuilder.tmpApkPath("com.foo"));
    }

    @Test
    public void moveApkToTmp_buildsMv() {
        assertEquals("mv /src/base.apk /data/local/tmp/tmpapkcom.foo.apk",
                GmsCommandBuilder.moveApkToTmp("/src/base.apk", "com.foo"));
    }

    @Test
    public void uninstall_buildsPmUninstall() {
        assertEquals("pm uninstall com.foo", GmsCommandBuilder.uninstall("com.foo"));
    }

    @Test
    public void installTmpApk_matchesInlineCommand() {
        // mirrors the old: "pm install -t -i \"com.android.vending\" -r" + " /data/.../tmpapk<pkg>.apk"
        assertEquals(
                "pm install -t -i \"com.android.vending\" -r /data/local/tmp/tmpapkcom.foo.apk",
                GmsCommandBuilder.installTmpApk("com.foo"));
    }

    // ------------------------------------------------------------------
    // Flag export
    // ------------------------------------------------------------------

    @Test
    public void exportFlagDump_matchesInlineCommand() {
        assertEquals(
                "cp /data/x/all_flags.txt /sdcard/Download/androidautox_flags.txt"
                        + " && chmod 644 /sdcard/Download/androidautox_flags.txt",
                GmsCommandBuilder.exportFlagDump("/data/x/all_flags.txt"));
    }
}

package com.xiddoc.androidautox;

/**
 * Pure builders for the {@code su} shell command strings that {@link MainActivity}
 * runs against Google Play Services / the phenotype database, plus the small pure
 * decisions that used to be inlined in the tweak handlers.
 *
 * <p>None of these methods touch the device — they only assemble strings and make
 * value decisions — so they are unit-testable without a root shell or the Activity.
 * {@code MainActivity} delegates to them and feeds the result to
 * {@link MainActivity#runSuWithCmd(String)}.
 */
public final class GmsCommandBuilder {

    /** GMS package whose phenotype DB we edit. */
    public static final String GMS_PACKAGE = "com.google.android.gms";

    /** Absolute path to the GMS phenotype database (see {@link GmsPaths#PHENOTYPE_DB}). */
    public static final String PHENOTYPE_DB = GmsPaths.PHENOTYPE_DB;

    /** Where we stash an exported flag dump that the user can read. */
    public static final String FLAG_DUMP_DEST = "/sdcard/Download/androidautox_flags.txt";

    private GmsCommandBuilder() {
    }

    // ------------------------------------------------------------------
    // Process / SELinux control
    // ------------------------------------------------------------------

    /** {@code am kill all <pkg>} for the supplied package. */
    public static String killAll(String pkg) {
        return "am kill all " + pkg;
    }

    /** {@code am force-stop <pkg>} for the supplied package. */
    public static String forceStop(String pkg) {
        return "am force-stop " + pkg;
    }

    /** {@code am kill all com.google.android.gms}. */
    public static String killGms() {
        return killAll(GMS_PACKAGE);
    }

    /** {@code am force-stop com.google.android.gms}. */
    public static String forceStopGms() {
        return forceStop(GMS_PACKAGE);
    }

    /** Re-enable GMS after a patch run ({@code pm enable ...}). */
    public static String enableGms() {
        return "pm enable " + GMS_PACKAGE;
    }

    /** Read the current SELinux mode ({@code getenforce}). */
    public static String getEnforce() {
        return "getenforce";
    }

    /** Set SELinux to permissive (0) or enforcing (1). */
    public static String setEnforce(boolean enforcing) {
        return "setenforce " + (enforcing ? "1" : "0");
    }

    /**
     * Whether SELinux should be restored to enforcing after a tweak run, given the
     * mode captured before we dropped it. We only flip it back when the original
     * mode was permissive — matching the inline {@code currentPolicy.toLowerCase()
     * .equals("permissive")} check that gated the restore in {@code MainActivity}.
     *
     * <p>Returns false for null so a missing/garbled {@code getenforce} reading never
     * triggers a spurious {@code setenforce 1}.
     */
    public static boolean shouldRestoreEnforcing(String policyBeforeDrop) {
        return policyBeforeDrop != null
                && policyBeforeDrop.trim().toLowerCase().equals("permissive");
    }

    // ------------------------------------------------------------------
    // Phenotype DB ownership
    // ------------------------------------------------------------------

    /** Read the current owner of the phenotype DB ({@code stat -c "%U" ...}). */
    public static String statPhenotypeOwner() {
        return "stat -c \"%U\" " + PHENOTYPE_DB;
    }

    /** Take ownership of the phenotype DB as {@code root}. */
    public static String chownPhenotypeToRoot() {
        return "chown root " + PHENOTYPE_DB;
    }

    /** Restore the phenotype DB to the supplied owner. */
    public static String chownPhenotypeTo(String owner) {
        return "chown " + owner + " " + PHENOTYPE_DB;
    }

    /** Wipe the phenotype file cache so GMS re-reads the DB on next launch. */
    public static String removePhenotypeCache() {
        return "rm -rf /data/data/com.google.android.gms/files/phenotype";
    }

    // ------------------------------------------------------------------
    // App patching (whitelist apps)
    // ------------------------------------------------------------------

    /** {@code pm path <pkg>} — locate an installed package's APK. */
    public static String pmPath(String pkg) {
        return "pm path " + pkg;
    }

    /**
     * Parse the APK path out of a {@code pm path} result line such as
     * {@code package:/data/app/.../base.apk}. Mirrors the inline
     * {@code substring(lastIndexOf(":") + 1)} extraction exactly (no trimming) so
     * the resulting {@code mv} command is byte-for-byte identical to the previous
     * inline behaviour.
     */
    public static String parsePmPathResult(String pmPathOutput) {
        if (pmPathOutput == null) {
            return "";
        }
        return pmPathOutput.substring(pmPathOutput.lastIndexOf(":") + 1);
    }

    /** Temp location an app's APK is moved to while it is reinstalled. */
    public static String tmpApkPath(String pkg) {
        return "/data/local/tmp/tmpapk" + pkg + ".apk";
    }

    /** Move an APK from its install path to the temp location. */
    public static String moveApkToTmp(String actualPath, String pkg) {
        return "mv " + actualPath + " " + tmpApkPath(pkg);
    }

    /** {@code pm uninstall <pkg>}. */
    public static String uninstall(String pkg) {
        return "pm uninstall " + pkg;
    }

    /** Reinstall the temp APK, attributing it to the Play Store installer. */
    public static String installTmpApk(String pkg) {
        return "pm install -t -i \"com.android.vending\" -r " + tmpApkPath(pkg);
    }

    // ------------------------------------------------------------------
    // Flag export
    // ------------------------------------------------------------------

    /** Copy the dumped flag file to Downloads and make it world-readable. */
    public static String exportFlagDump(String srcFile) {
        return "cp " + srcFile + " " + FLAG_DUMP_DEST + " && chmod 644 " + FLAG_DUMP_DEST;
    }
}

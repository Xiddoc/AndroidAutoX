package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for {@link TweakRegistry#patchedAppsSpecs(Context)} — the flag-spec
 * generator for the "patch custom apps" feature. Runs entirely off-device via
 * Robolectric (real {@link SharedPreferences} backing).
 *
 * <p>The method reads the user's selected apps from the {@code "appsListPref"}
 * SharedPreferences (key = packageName, value = label) and emits a fixed set of
 * validation-bypass flags plus dynamic whitelist flags whose value is the
 * comma-joined list of selected package names.
 *
 * <p>Note: {@code appsListPref.getAll()} returns a map with no guaranteed
 * iteration order, so multi-app tests assert on the <em>set</em> of comma-split
 * package names rather than a fixed ordering.
 */
@RunWith(RobolectricTestRunner.class)
public class TweakRegistryPatchedAppsTest {

    private Context ctx;
    private SharedPreferences appsPref;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        appsPref = ctx.getSharedPreferences("appsListPref", Context.MODE_PRIVATE);
        // Start from a clean slate so tests don't leak prefs into each other.
        appsPref.edit().clear().commit();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void selectApp(String packageName, String label) {
        appsPref.edit().putString(packageName, label).commit();
    }

    /** Returns the first spec whose flag name matches, or null. */
    private static FlagSpec specNamed(List<FlagSpec> specs, String name) {
        for (FlagSpec s : specs) {
            if (name.equals(s.name)) return s;
        }
        return null;
    }

    /** Asserts there's exactly one spec with this (pkg, name) and returns it. */
    private static FlagSpec one(List<FlagSpec> specs, String pkg, String name) {
        FlagSpec found = null;
        int count = 0;
        for (FlagSpec s : specs) {
            if (name.equals(s.name)) {
                count++;
                found = s;
            }
        }
        assertEquals("expected exactly one flag named " + name, 1, count);
        assertNotNull(found);
        assertEquals("flag " + name + " has wrong package target", pkg, found.pkg);
        return found;
    }

    private static void assertBool(FlagSpec s, boolean expected) {
        assertFalse("flag " + s.name + " should not be a removal spec", s.remove);
        assertNotNull(s.flag);
        int expectedType = expected
                ? PhixitSnapshot.TYPE_BOOL_TRUE
                : PhixitSnapshot.TYPE_BOOL_FALSE;
        assertEquals("flag " + s.name + " has wrong bool type", expectedType, s.flag.type);
    }

    private static void assertStr(FlagSpec s, String expected) {
        assertFalse("flag " + s.name + " should not be a removal spec", s.remove);
        assertNotNull(s.flag);
        assertEquals("flag " + s.name + " has wrong type", PhixitSnapshot.TYPE_STRING, s.flag.type);
        assertEquals("flag " + s.name + " has wrong string value", expected, s.flag.stringValue);
    }

    private static Set<String> splitWhitelist(String value) {
        Set<String> set = new HashSet<>();
        if (value.isEmpty()) return set;
        set.addAll(Arrays.asList(value.split(",", -1)));
        return set;
    }

    // -----------------------------------------------------------------------
    // Overall shape
    // -----------------------------------------------------------------------

    /** The method always emits exactly 11 flag specs regardless of selection. */
    @Test
    public void emptySelection_producesExactlyElevenSpecs() {
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);
        assertEquals(11, specs.size());
    }

    @Test
    public void multipleApps_stillExactlyElevenSpecs() {
        selectApp("com.example.one", "One");
        selectApp("com.example.two", "Two");
        selectApp("com.example.three", "Three");
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);
        assertEquals(11, specs.size());
    }

    /** No spec is ever a removal spec on the apply path. */
    @Test
    public void noSpecIsRemoval() {
        selectApp("com.example.one", "One");
        for (FlagSpec s : TweakRegistry.patchedAppsSpecs(ctx)) {
            assertFalse("flag " + s.name + " unexpectedly a removal spec", s.remove);
            assertNotNull("flag " + s.name + " has null flag", s.flag);
        }
    }

    // -----------------------------------------------------------------------
    // Static (always-present) flags — package target + value
    // -----------------------------------------------------------------------

    @Test
    public void staticFlags_haveExpectedPackageTargetsAndValues() {
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        // Empty whitelist strings (these two are pinned to "" regardless of selection).
        assertStr(one(specs, FlagSpec.PKG_GEARHEAD, "AppValidation__allowed_package_list"), "");
        assertStr(one(specs, FlagSpec.PKG_GEARHEAD, "AppValidation__blocked_packages_by_installer"), "");

        // Boolean validation-bypass flags.
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, "AppValidation__should_bypass_validation"), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, "AppValidation__play_install_api"), false);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, "AppValidation__swallow_play_api_exception"), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, "AppValidation__swallow_play_api_exception_return_value"), true);
        assertBool(one(specs, FlagSpec.PKG_CAR, "should_bypass_validation"), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD,
                "CarProjectionValidator__filter_disabled_packages_in_ispackageallowed_method"), false);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, "UnknownSources__allow_full_screen_apps"), true);
    }

    /**
     * Pin the full target-keyed view: a map of flag name -> "PKG|type" so any
     * regression in any single flag's package or type is caught at once.
     */
    @Test
    public void fullTargetKeyedMap_isPinned() {
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        Map<String, String> got = new HashMap<>();
        for (FlagSpec s : specs) {
            got.put(s.name, s.pkg + "|" + s.flag.type);
        }

        Map<String, String> expected = new HashMap<>();
        expected.put("app_white_list",
                FlagSpec.PKG_CAR + "|" + PhixitSnapshot.TYPE_STRING);
        expected.put("car_connect_broadcast_whitelist",
                FlagSpec.PKG_CAR + "|" + PhixitSnapshot.TYPE_STRING);
        expected.put("AppValidation__allowed_package_list",
                FlagSpec.PKG_GEARHEAD + "|" + PhixitSnapshot.TYPE_STRING);
        expected.put("AppValidation__blocked_packages_by_installer",
                FlagSpec.PKG_GEARHEAD + "|" + PhixitSnapshot.TYPE_STRING);
        expected.put("AppValidation__should_bypass_validation",
                FlagSpec.PKG_GEARHEAD + "|" + PhixitSnapshot.TYPE_BOOL_TRUE);
        expected.put("AppValidation__play_install_api",
                FlagSpec.PKG_GEARHEAD + "|" + PhixitSnapshot.TYPE_BOOL_FALSE);
        expected.put("AppValidation__swallow_play_api_exception",
                FlagSpec.PKG_GEARHEAD + "|" + PhixitSnapshot.TYPE_BOOL_TRUE);
        expected.put("AppValidation__swallow_play_api_exception_return_value",
                FlagSpec.PKG_GEARHEAD + "|" + PhixitSnapshot.TYPE_BOOL_TRUE);
        expected.put("should_bypass_validation",
                FlagSpec.PKG_CAR + "|" + PhixitSnapshot.TYPE_BOOL_TRUE);
        expected.put("CarProjectionValidator__filter_disabled_packages_in_ispackageallowed_method",
                FlagSpec.PKG_GEARHEAD + "|" + PhixitSnapshot.TYPE_BOOL_FALSE);
        expected.put("UnknownSources__allow_full_screen_apps",
                FlagSpec.PKG_GEARHEAD + "|" + PhixitSnapshot.TYPE_BOOL_TRUE);

        assertEquals(expected, got);
        // Map size also pins that there are no duplicate flag names.
        assertEquals(11, got.size());
    }

    // -----------------------------------------------------------------------
    // Dynamic whitelist flags
    // -----------------------------------------------------------------------

    /** With no apps selected, the two whitelist flags carry an empty string. */
    @Test
    public void emptySelection_whitelistFlagsAreEmptyString() {
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);
        assertStr(one(specs, FlagSpec.PKG_CAR, "app_white_list"), "");
        assertStr(one(specs, FlagSpec.PKG_CAR, "car_connect_broadcast_whitelist"), "");
    }

    /** A single selected app puts exactly its package name in both whitelist flags. */
    @Test
    public void singleApp_whitelistFlagsContainPackageName() {
        selectApp("com.example.solo", "Solo App");
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        assertStr(one(specs, FlagSpec.PKG_CAR, "app_white_list"), "com.example.solo");
        assertStr(one(specs, FlagSpec.PKG_CAR, "car_connect_broadcast_whitelist"), "com.example.solo");
    }

    /**
     * The whitelist value uses the package NAME (the pref key), never the label
     * (the pref value).
     */
    @Test
    public void whitelist_usesPackageKeyNotLabel() {
        selectApp("com.example.pkg", "Some Friendly Label");
        FlagSpec wl = one(TweakRegistry.patchedAppsSpecs(ctx), FlagSpec.PKG_CAR, "app_white_list");
        assertEquals("com.example.pkg", wl.flag.stringValue);
        assertFalse(wl.flag.stringValue.contains("Friendly"));
    }

    /**
     * Multiple apps: comma-joined, no leading/trailing comma, all selected
     * package names present. Order is map-iteration-dependent, so assert on the
     * set of split parts.
     */
    @Test
    public void multipleApps_whitelistIsCommaJoinedWithAllPackages() {
        selectApp("com.example.alpha", "Alpha");
        selectApp("com.example.beta", "Beta");
        selectApp("com.example.gamma", "Gamma");

        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        String appWhiteList = specNamed(specs, "app_white_list").flag.stringValue;
        String carWhiteList = specNamed(specs, "car_connect_broadcast_whitelist").flag.stringValue;

        // Both whitelist flags carry the identical joined string.
        assertEquals(appWhiteList, carWhiteList);

        // No leading/trailing/doubled delimiters.
        assertFalse("unexpected leading comma", appWhiteList.startsWith(","));
        assertFalse("unexpected trailing comma", appWhiteList.endsWith(","));
        assertFalse("unexpected empty segment", appWhiteList.contains(",,"));

        Set<String> parts = splitWhitelist(appWhiteList);
        Set<String> expected = new HashSet<>(Arrays.asList(
                "com.example.alpha", "com.example.beta", "com.example.gamma"));
        assertEquals(expected, parts);
        // Exactly three comma-separated entries (no extras, no dups in output).
        assertEquals(3, appWhiteList.split(",", -1).length);
    }

    /**
     * The two empty-string whitelist flags (allowed_package_list,
     * blocked_packages_by_installer) stay empty even when apps are selected — they
     * are intentionally not driven by the user's selection.
     */
    @Test
    public void selectionDoesNotLeakIntoAllowedOrBlockedLists() {
        selectApp("com.example.alpha", "Alpha");
        selectApp("com.example.beta", "Beta");
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        assertStr(one(specs, FlagSpec.PKG_GEARHEAD, "AppValidation__allowed_package_list"), "");
        assertStr(one(specs, FlagSpec.PKG_GEARHEAD, "AppValidation__blocked_packages_by_installer"), "");
    }

    /** Static boolean flags are unaffected by how many apps are selected. */
    @Test
    public void staticFlagsUnchangedBySelectionSize() {
        selectApp("a.b.c", "C");
        selectApp("d.e.f", "F");
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, "AppValidation__should_bypass_validation"), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, "AppValidation__play_install_api"), false);
        assertBool(one(specs, FlagSpec.PKG_CAR, "should_bypass_validation"), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, "UnknownSources__allow_full_screen_apps"), true);
    }
}

package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
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
 * <p>The method reads the user's selected apps from the {@link #APPS_PREF}
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

    // -----------------------------------------------------------------------
    // Pinned identifiers (test-local mirrors of production string literals)
    //
    // These constants intentionally re-declare the flag names and pref key that
    // TweakRegistry uses as inline literals. Production has no single source of
    // truth for them yet (they are duplicated across TweakRegistry, MainActivity,
    // AppsList, MyAdapter); promoting them to shared constants is a separate
    // production refactor. Until then, these test-local copies are the pin: if a
    // production literal changes, the corresponding assertion fails here.
    // -----------------------------------------------------------------------

    /** SharedPreferences name holding the user's selected apps (key=pkg, value=label). */
    private static final String APPS_PREF = "appsListPref";

    // Dynamic whitelist flags (value = comma-joined selected package names).
    private static final String F_APP_WHITE_LIST = "app_white_list";
    private static final String F_CAR_CONNECT_BROADCAST_WHITELIST = "car_connect_broadcast_whitelist";

    // Static string flags (pinned to "").
    private static final String F_ALLOWED_PACKAGE_LIST = "AppValidation__allowed_package_list";
    private static final String F_BLOCKED_PACKAGES_BY_INSTALLER = "AppValidation__blocked_packages_by_installer";

    // Static boolean flags.
    private static final String F_SHOULD_BYPASS_VALIDATION_GH = "AppValidation__should_bypass_validation";
    private static final String F_PLAY_INSTALL_API = "AppValidation__play_install_api";
    private static final String F_SWALLOW_PLAY_API_EXCEPTION = "AppValidation__swallow_play_api_exception";
    private static final String F_SWALLOW_PLAY_API_EXCEPTION_RETURN_VALUE =
            "AppValidation__swallow_play_api_exception_return_value";
    private static final String F_SHOULD_BYPASS_VALIDATION_CAR = "should_bypass_validation";
    private static final String F_FILTER_DISABLED_PACKAGES =
            "CarProjectionValidator__filter_disabled_packages_in_ispackageallowed_method";
    private static final String F_ALLOW_FULL_SCREEN_APPS = "UnknownSources__allow_full_screen_apps";
    private static final String F_UNKNOWN_SOURCES_ENABLED = "UnknownSources__enabled";
    private static final String F_LITE_UNKNOWN_SOURCES_ALLOWED =
            "UnknownSources__lite_unknown_sources_allowed";
    private static final String F_GEARHEAD_DEVELOPER_ENABLED = "GearheadDeveloper__enabled";
    private static final String F_GEARHEAD_DEVELOPER_SETTINGS_ENABLED =
            "GearheadDeveloper__settings_enabled";

    /**
     * Total spec count: 2 dynamic whitelist string flags + 2 static empty-string
     * flags + 11 static boolean/bypass flags = 15.
     */
    private static final int EXPECTED_SPEC_COUNT = 15;

    private Context ctx;
    private SharedPreferences appsPref;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        appsPref = ctx.getSharedPreferences(APPS_PREF, Context.MODE_PRIVATE);
        // Start from a clean slate so tests don't leak prefs into each other.
        appsPref.edit().clear().commit();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Selects an app with an explicit (key, label) pair. Use when key-vs-label matters. */
    private void selectApp(String packageName, String label) {
        appsPref.edit().putString(packageName, label).commit();
    }

    /**
     * Selects several apps by package name, giving each an arbitrary label. Use in
     * tests that only care about package keys / counts, not the label distinction.
     */
    private void selectApps(String... packages) {
        SharedPreferences.Editor e = appsPref.edit();
        for (String pkg : packages) {
            e.putString(pkg, "label-for-" + pkg);
        }
        e.commit();
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

    /** The method always emits exactly EXPECTED_SPEC_COUNT flag specs regardless of selection. */
    @Test
    public void emptySelection_producesExactlyFifteenSpecs() {
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);
        assertEquals(EXPECTED_SPEC_COUNT, specs.size());
    }

    @Test
    public void multipleApps_stillExactlyFifteenSpecs() {
        selectApps("com.example.one", "com.example.two", "com.example.three");
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);
        assertEquals(EXPECTED_SPEC_COUNT, specs.size());
    }

    /** No spec is ever a removal spec on the apply path. */
    @Test
    public void noSpecIsRemoval() {
        selectApps("com.example.one");
        for (FlagSpec s : TweakRegistry.patchedAppsSpecs(ctx)) {
            assertFalse("flag " + s.name + " unexpectedly a removal spec", s.remove);
            assertNotNull("flag " + s.name + " has null flag", s.flag);
        }
    }

    // -----------------------------------------------------------------------
    // Full pinned view: package + type + value for every flag
    // -----------------------------------------------------------------------

    /**
     * Strong oracle that pins, per flag name, the tuple "PKG|type|value" so a
     * regression in any single flag's package, type, OR value is caught — and the
     * failure message names the offending flag instead of dumping two 15-entry
     * maps. The value component is the bool true/false, the string literal, etc.
     *
     * <p>This is the single source of truth for the static flags' package/type/value;
     * the dynamic whitelist flags are pinned to their empty-selection value here and
     * exercised under load by the dynamic-whitelist tests below.
     */
    @Test
    public void fullTargetKeyedMap_isPinned() {
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        Map<String, String> got = new HashMap<>();
        for (FlagSpec s : specs) {
            got.put(s.name, descriptor(s));
        }

        Map<String, String> expected = new HashMap<>();
        // Dynamic whitelist flags carry "" with no apps selected.
        expected.put(F_APP_WHITE_LIST, strDescriptor(FlagSpec.PKG_CAR, ""));
        expected.put(F_CAR_CONNECT_BROADCAST_WHITELIST, strDescriptor(FlagSpec.PKG_CAR, ""));
        // Static empty-string flags.
        expected.put(F_ALLOWED_PACKAGE_LIST, strDescriptor(FlagSpec.PKG_GEARHEAD, ""));
        expected.put(F_BLOCKED_PACKAGES_BY_INSTALLER, strDescriptor(FlagSpec.PKG_GEARHEAD, ""));
        // Static boolean flags.
        expected.put(F_SHOULD_BYPASS_VALIDATION_GH, boolDescriptor(FlagSpec.PKG_GEARHEAD, true));
        expected.put(F_PLAY_INSTALL_API, boolDescriptor(FlagSpec.PKG_GEARHEAD, false));
        expected.put(F_SWALLOW_PLAY_API_EXCEPTION, boolDescriptor(FlagSpec.PKG_GEARHEAD, true));
        expected.put(F_SWALLOW_PLAY_API_EXCEPTION_RETURN_VALUE, boolDescriptor(FlagSpec.PKG_GEARHEAD, true));
        expected.put(F_SHOULD_BYPASS_VALIDATION_CAR, boolDescriptor(FlagSpec.PKG_CAR, true));
        expected.put(F_FILTER_DISABLED_PACKAGES, boolDescriptor(FlagSpec.PKG_GEARHEAD, false));
        expected.put(F_ALLOW_FULL_SCREEN_APPS, boolDescriptor(FlagSpec.PKG_GEARHEAD, true));
        expected.put(F_UNKNOWN_SOURCES_ENABLED, boolDescriptor(FlagSpec.PKG_GEARHEAD, true));
        expected.put(F_LITE_UNKNOWN_SOURCES_ALLOWED, boolDescriptor(FlagSpec.PKG_GEARHEAD, true));
        expected.put(F_GEARHEAD_DEVELOPER_ENABLED, boolDescriptor(FlagSpec.PKG_GEARHEAD, true));
        expected.put(F_GEARHEAD_DEVELOPER_SETTINGS_ENABLED, boolDescriptor(FlagSpec.PKG_GEARHEAD, true));

        // Per-entry assertion so a failure names the offending flag.
        for (Map.Entry<String, String> e : expected.entrySet()) {
            assertEquals("flag " + e.getKey(), e.getValue(), got.get(e.getKey()));
        }
        // Key sets must match exactly: no missing, no extra, no duplicate names
        // (a duplicate would collapse in the map and drop a key from `got`).
        assertEquals("flag name set mismatch", expected.keySet(), got.keySet());
        assertEquals(EXPECTED_SPEC_COUNT, got.size());
        assertEquals(EXPECTED_SPEC_COUNT, specs.size());
    }

    /** Encodes a spec as "PKG|type|value" for the pinned-map oracle. */
    private static String descriptor(FlagSpec s) {
        assertFalse("flag " + s.name + " should not be a removal spec", s.remove);
        assertNotNull("flag " + s.name + " has null flag", s.flag);
        String value;
        switch (s.flag.type) {
            case PhixitSnapshot.TYPE_BOOL_FALSE:
            case PhixitSnapshot.TYPE_BOOL_TRUE:
                value = Boolean.toString(s.flag.boolValue());
                break;
            case PhixitSnapshot.TYPE_STRING:
                value = "\"" + s.flag.stringValue + "\"";
                break;
            default:
                value = "<type " + s.flag.type + ">";
                break;
        }
        return s.pkg + "|" + s.flag.type + "|" + value;
    }

    private static String boolDescriptor(String pkg, boolean value) {
        int type = value ? PhixitSnapshot.TYPE_BOOL_TRUE : PhixitSnapshot.TYPE_BOOL_FALSE;
        return pkg + "|" + type + "|" + value;
    }

    private static String strDescriptor(String pkg, String value) {
        return pkg + "|" + PhixitSnapshot.TYPE_STRING + "|\"" + value + "\"";
    }

    // -----------------------------------------------------------------------
    // Dynamic whitelist flags
    // -----------------------------------------------------------------------

    /** With no apps selected, the two whitelist flags carry an empty string. */
    @Test
    public void emptySelection_whitelistFlagsAreEmptyString() {
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);
        assertStr(one(specs, FlagSpec.PKG_CAR, F_APP_WHITE_LIST), "");
        assertStr(one(specs, FlagSpec.PKG_CAR, F_CAR_CONNECT_BROADCAST_WHITELIST), "");
    }

    /** A single selected app puts exactly its package name in both whitelist flags. */
    @Test
    public void singleApp_whitelistFlagsContainPackageName() {
        selectApps("com.example.solo");
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        assertStr(one(specs, FlagSpec.PKG_CAR, F_APP_WHITE_LIST), "com.example.solo");
        assertStr(one(specs, FlagSpec.PKG_CAR, F_CAR_CONNECT_BROADCAST_WHITELIST), "com.example.solo");
    }

    /**
     * The whitelist value uses the package NAME (the pref key), never the label
     * (the pref value). The label is deliberately a string that shares no substring
     * with the key, so the exact-equals on the package key is genuinely meaningful;
     * we additionally assert the value is not the label.
     */
    @Test
    public void whitelist_usesPackageKeyNotLabel() {
        String pkg = "com.example.pkg";
        String label = "Totally Different Display Name";
        selectApp(pkg, label);

        FlagSpec wl = one(TweakRegistry.patchedAppsSpecs(ctx), FlagSpec.PKG_CAR, F_APP_WHITE_LIST);
        assertEquals(pkg, wl.flag.stringValue);
        assertNotEquals(label, wl.flag.stringValue);
    }

    /**
     * Multiple apps: comma-joined, no leading/trailing comma, all selected
     * package names present. Order is map-iteration-dependent, so assert on the
     * set of split parts.
     */
    @Test
    public void multipleApps_whitelistIsCommaJoinedWithAllPackages() {
        selectApps("com.example.alpha", "com.example.beta", "com.example.gamma");

        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        String appWhiteList = one(specs, FlagSpec.PKG_CAR, F_APP_WHITE_LIST).flag.stringValue;
        String carWhiteList =
                one(specs, FlagSpec.PKG_CAR, F_CAR_CONNECT_BROADCAST_WHITELIST).flag.stringValue;

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
     * Pins the CURRENT (unsanitized) join behavior: {@code patchedAppsSpecs} joins
     * raw pref keys with "," and does NO escaping/sanitization. A package key that
     * itself contains a comma therefore splits into the "wrong" number of entries.
     *
     * <p>This test deliberately asserts the buggy-by-omission behavior so it acts
     * as a CANARY: if sanitization/escaping is ever added in production, this test
     * will fail and force a conscious update here. Do not "fix" the assertion
     * without also fixing the producer.
     */
    @Test
    public void commaInPackageKey_isNotSanitized_pinsCurrentBehavior() {
        // One legit package plus one bogus key that itself contains a comma.
        selectApps("com.example.real", "com.example.evil,com.example.injected");

        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);
        String appWhiteList = one(specs, FlagSpec.PKG_CAR, F_APP_WHITE_LIST).flag.stringValue;

        // No sanitization => the embedded comma is emitted verbatim, so splitting on
        // "," yields THREE parts even though only TWO apps were selected.
        String[] parts = appWhiteList.split(",", -1);
        assertEquals("unsanitized comma should over-split the whitelist", 3, parts.length);

        // And the injected fragment leaks through as if it were its own entry.
        Set<String> partSet = new HashSet<>(Arrays.asList(parts));
        assertEquals(new HashSet<>(Arrays.asList(
                "com.example.real", "com.example.evil", "com.example.injected")), partSet);
    }

    /**
     * The two empty-string whitelist flags (allowed_package_list,
     * blocked_packages_by_installer) stay empty even when apps are selected — they
     * are intentionally not driven by the user's selection.
     */
    @Test
    public void selectionDoesNotLeakIntoAllowedOrBlockedLists() {
        selectApps("com.example.alpha", "com.example.beta");
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        assertStr(one(specs, FlagSpec.PKG_GEARHEAD, F_ALLOWED_PACKAGE_LIST), "");
        assertStr(one(specs, FlagSpec.PKG_GEARHEAD, F_BLOCKED_PACKAGES_BY_INSTALLER), "");
    }

    /** Static boolean flags are unaffected by how many apps are selected. */
    @Test
    public void staticFlagsUnchangedBySelectionSize() {
        selectApps("com.example.alpha", "com.example.beta");
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);

        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, F_SHOULD_BYPASS_VALIDATION_GH), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, F_PLAY_INSTALL_API), false);
        assertBool(one(specs, FlagSpec.PKG_CAR, F_SHOULD_BYPASS_VALIDATION_CAR), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, F_ALLOW_FULL_SCREEN_APPS), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, F_UNKNOWN_SOURCES_ENABLED), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, F_LITE_UNKNOWN_SOURCES_ALLOWED), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, F_GEARHEAD_DEVELOPER_ENABLED), true);
        assertBool(one(specs, FlagSpec.PKG_GEARHEAD, F_GEARHEAD_DEVELOPER_SETTINGS_ENABLED), true);
    }

    // -----------------------------------------------------------------------
    // Codec round-trip
    // -----------------------------------------------------------------------

    /**
     * End-to-end check that a multi-app {@code app_white_list} string survives the
     * snapshot codec: encode the flag list to the phixit byte stream, decode it
     * back, and assert the string value is byte-for-byte preserved (including the
     * comma-joined package list). Exercises the same {@link PhixitSnapshot} path
     * that actually writes the value into the served snapshot.
     */
    @Test
    public void whitelistValue_survivesEncodeDecodeRoundTrip() {
        selectApps("com.example.alpha", "com.example.beta", "com.example.gamma");
        List<FlagSpec> specs = TweakRegistry.patchedAppsSpecs(ctx);
        String original = one(specs, FlagSpec.PKG_CAR, F_APP_WHITE_LIST).flag.stringValue;

        // Build a minimal Flag list carrying the whitelist value.
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = F_APP_WHITE_LIST;
        f.numericName = false;
        f.type = PhixitSnapshot.TYPE_STRING;
        f.stringValue = original;
        List<PhixitSnapshot.Flag> in = new ArrayList<>();
        in.add(f);

        byte[] encoded = PhixitSnapshot.encode(in);
        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(encoded);

        assertEquals(1, out.size());
        PhixitSnapshot.Flag decoded = out.get(0);
        assertEquals(F_APP_WHITE_LIST, decoded.name);
        assertEquals(PhixitSnapshot.TYPE_STRING, decoded.type);
        assertEquals(original, decoded.stringValue);

        // Also survive the raw-DEFLATE layer used on the wire.
        byte[] deflated = PhixitSnapshot.deflateRaw(encoded);
        List<PhixitSnapshot.Flag> outDeflated =
                PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(deflated));
        assertEquals(original, outDeflated.get(0).stringValue);
    }
}

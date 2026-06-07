package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain-JUnit tests for {@link TweakRegistry}.
 *
 * <p>Run on the normal JUnit classloader (NOT Robolectric) so JaCoCo can instrument
 * the production class — Robolectric's sandbox classloader hides coverage. The only
 * Android dependency is {@link SharedPreferences}, supplied by {@link FakeSharedPreferences}
 * through a mocked {@link Context}. The per-tweak spec builders (battery/HUN/bitrate)
 * are pure {@code List<FlagSpec>} factories; {@code specsFor}, {@code patchedAppsSpecs},
 * {@code enabledSpecs} and {@code anyEnabled} read prefs.
 */
public class TweakRegistryTest {

    private Context ctx;
    private FakeSharedPreferences mainPrefs;
    private FakeSharedPreferences appsPrefs;

    @Before
    public void setUp() {
        mainPrefs = new FakeSharedPreferences();
        appsPrefs = new FakeSharedPreferences();
        final Map<String, SharedPreferences> byName = new HashMap<String, SharedPreferences>();
        byName.put(PhixitEngine.PREFS, mainPrefs);
        byName.put("appsListPref", appsPrefs);

        ctx = mock(Context.class);
        lenient().when(ctx.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(inv -> byName.get(inv.getArgument(0)));
    }

    private double dbl(FlagSpec s) {
        return Double.longBitsToDouble(s.flag.doubleBits);
    }

    // --- pure spec builders ------------------------------------------------

    @Test
    public void batteryWarningSpecs_disablesWarning() {
        List<FlagSpec> l = TweakRegistry.batteryWarningSpecs();
        assertEquals(1, l.size());
        assertEquals("BatterySaver__warning_enabled", l.get(0).name);
        assertEquals(PhixitSnapshot.TYPE_BOOL_FALSE, l.get(0).flag.type);
    }

    @Test
    public void hunSpecs_carriesValue() {
        List<FlagSpec> l = TweakRegistry.hunSpecs(1234);
        assertEquals(1, l.size());
        assertEquals(PhixitSnapshot.TYPE_LONG, l.get(0).flag.type);
        assertEquals(1234L, l.get(0).flag.longValue);
    }

    @Test
    public void mediaHunSpecs_carriesValue() {
        List<FlagSpec> l = TweakRegistry.mediaHunSpecs(77);
        assertEquals(1, l.size());
        assertEquals(77L, l.get(0).flag.longValue);
    }

    @Test
    public void usbBitrateSpecs_scalesAllSixResolutions() {
        List<FlagSpec> l = TweakRegistry.usbBitrateSpecs(2.0);
        assertEquals(6, l.size());
        assertEquals(16000000 * 2.0, dbl(l.get(0)), 0.001);
        assertEquals(3000000 * 2.0, dbl(l.get(1)), 0.001);
        assertEquals(8000000 * 2.0, dbl(l.get(2)), 0.001);
        assertEquals(1000000 * 2.0, dbl(l.get(3)), 0.001);
        assertEquals(12000000 * 2.0, dbl(l.get(4)), 0.001);
        assertEquals(2000000 * 2.0, dbl(l.get(5)), 0.001);
    }

    @Test
    public void wifiBitrateSpecs_scalesAllSixResolutions() {
        List<FlagSpec> l = TweakRegistry.wifiBitrateSpecs(0.5);
        assertEquals(6, l.size());
        assertEquals(16000000 * 0.5, dbl(l.get(0)), 0.001);
        assertEquals(2000000 * 0.5, dbl(l.get(5)), 0.001);
        for (FlagSpec s : l) assertEquals(FlagSpec.PKG_CAR, s.pkg);
    }

    // --- specsFor: every dynamic branch + default delegation ---------------

    @Test
    public void specsFor_batteryWarning_usesBuilder() {
        List<FlagSpec> l = TweakRegistry.specsFor(ctx, "battery_saver_warning");
        assertEquals("BatterySaver__warning_enabled", l.get(0).name);
    }

    @Test
    public void specsFor_hunReadsSavedPref() {
        mainPrefs.edit().putInt("messaging_hun_value", 555).apply();
        List<FlagSpec> l = TweakRegistry.specsFor(ctx, "aa_hun_ms");
        assertEquals(555L, l.get(0).flag.longValue);
    }

    @Test
    public void specsFor_mediaHunReadsSavedPref() {
        mainPrefs.edit().putInt("media_hun_value", 321).apply();
        List<FlagSpec> l = TweakRegistry.specsFor(ctx, "aa_media_hun");
        assertEquals(321L, l.get(0).flag.longValue);
    }

    @Test
    public void specsFor_usbBitrateReadsSavedPref() {
        mainPrefs.edit().putFloat("usb_bitrate_value", 3f).apply();
        List<FlagSpec> l = TweakRegistry.specsFor(ctx, "aa_bitrate_usb");
        assertEquals(16000000 * 3.0, dbl(l.get(0)), 1.0);
    }

    @Test
    public void specsFor_wifiBitrateReadsSavedPref() {
        mainPrefs.edit().putFloat("wifi_bitrate_value", 4f).apply();
        List<FlagSpec> l = TweakRegistry.specsFor(ctx, "aa_bitrate_wireless");
        assertEquals(16000000 * 4.0, dbl(l.get(0)), 1.0);
    }

    @Test
    public void specsFor_patchedAppsBranch() {
        List<FlagSpec> l = TweakRegistry.specsFor(ctx, "aa_patched_apps");
        assertNotNull(l);
        assertEquals("app_white_list", l.get(0).name);
    }

    @Test
    public void specsFor_defaultDelegatesToPhixitTweaks() {
        List<FlagSpec> l = TweakRegistry.specsFor(ctx, "aa_material_you");
        assertNotNull(l);
        assertEquals("SystemUi__material_you_settings_enabled", l.get(0).name);
        // an unknown key falls through to PhixitTweaks.specs -> null
        assertNull(TweakRegistry.specsFor(ctx, "nope_not_real"));
    }

    // --- patchedAppsSpecs --------------------------------------------------

    @Test
    public void patchedAppsSpecs_emptyWhitelist_whenNoAppsSelected() {
        List<FlagSpec> l = TweakRegistry.patchedAppsSpecs(ctx);
        assertEquals("", l.get(0).flag.stringValue);
        assertEquals(15, l.size());
    }

    @Test
    public void patchedAppsSpecs_joinsSelectedPackagesWithCommas() {
        appsPrefs.edit().putBoolean("com.foo", true).putBoolean("com.bar", true).apply();
        List<FlagSpec> l = TweakRegistry.patchedAppsSpecs(ctx);
        String whitelist = l.get(0).flag.stringValue;
        assertTrue(whitelist.contains("com.foo"));
        assertTrue(whitelist.contains("com.bar"));
        assertTrue(whitelist.contains(","));
        assertEquals(whitelist, l.get(1).flag.stringValue);
    }

    // --- enabledSpecs / anyEnabled ----------------------------------------

    @Test
    public void anyEnabled_falseWhenNothingToggled() {
        assertFalse(TweakRegistry.anyEnabled(ctx));
        assertTrue(TweakRegistry.enabledSpecs(ctx).isEmpty());
    }

    @Test
    public void anyEnabled_trueAndEnabledSpecsCollected() {
        mainPrefs.edit()
                .putBoolean("aa_material_you", true)
                .putBoolean("battery_saver_warning", true)
                .apply();
        assertTrue(TweakRegistry.anyEnabled(ctx));
        List<FlagSpec> all = TweakRegistry.enabledSpecs(ctx);
        assertEquals(2, all.size());
    }

    @Test
    public void enabledSpecs_collectsAcrossEveryKey() {
        for (String k : TweakRegistry.ALL_KEYS) mainPrefs.edit().putBoolean(k, true).apply();
        List<FlagSpec> all = TweakRegistry.enabledSpecs(ctx);
        assertNotNull(all);
        assertFalse(all.isEmpty());
    }

    @Test
    public void enabledSpecs_skipsEnabledKeyWhoseSpecsAreNull() {
        // A bogus enabled key whose specsFor() returns null exercises the defensive
        // `s != null` skip arm; a valid key is still accumulated.
        mainPrefs.edit()
                .putBoolean("not_a_real_key", true)
                .putBoolean("aa_material_you", true)
                .apply();
        List<FlagSpec> all = TweakRegistry.enabledSpecs(
                ctx, new String[]{"not_a_real_key", "aa_material_you"});
        assertEquals(1, all.size()); // only material_you contributed
        assertEquals("SystemUi__material_you_settings_enabled", all.get(0).name);
    }
}

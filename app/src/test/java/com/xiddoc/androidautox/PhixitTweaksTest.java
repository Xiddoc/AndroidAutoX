package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Pure-data tests for {@link PhixitTweaks}. {@code specs(key)} is a giant switch
 * that returns the flag table for each static tweak (or {@code null} for an
 * unknown key); {@code has(key)} is the boolean counterpart. We assert every case
 * label resolves to a non-empty list and that unknown keys fall through to the
 * {@code default} branch.
 */
public class PhixitTweaksTest {

    /** Every static tweak key handled by the switch (must all hit a case label). */
    private static final String[] KNOWN_KEYS = {
            "aa_message_autoread", "aa_speed_hack", "multi_display", "aa_six_tap",
            "coolwalk_daynight_tweak", "aa_battery_outline", "aa_activate_coolwalk",
            "aa_deactivate_coolwalk", "aa_material_you", "aa_activate_assistant_tips",
            "aa_activate_declinesms", "aa_new_seekbar", "bluetooth_pairing_off",
            "kill_telemetry", "uxprototype_tweak", "aa_inertial_scroll", "aa_vertical_bar",
            "aa_hun_ms", "aa_media_hun", "aa_bitrate_usb", "aa_bitrate_wireless",
            "aa_patched_apps",
    };

    @Test
    public void everyKnownKey_returnsNonEmptySpecList() {
        for (String key : KNOWN_KEYS) {
            List<FlagSpec> specs = PhixitTweaks.specs(key);
            assertNotNull("specs(" + key + ") should not be null", specs);
            assertFalse("specs(" + key + ") should not be empty", specs.isEmpty());
            // Each spec must carry a package and a name.
            for (FlagSpec s : specs) {
                assertNotNull(s.pkg);
                assertNotNull(s.name);
            }
        }
    }

    @Test
    public void everyKnownKey_hasReturnsTrue() {
        for (String key : KNOWN_KEYS) {
            assertTrue("has(" + key + ") should be true", PhixitTweaks.has(key));
        }
    }

    @Test
    public void unknownKey_returnsNull_andHasFalse() {
        assertNull(PhixitTweaks.specs("not_a_real_tweak"));
        assertFalse(PhixitTweaks.has("not_a_real_tweak"));
    }

    @Test
    public void specsCarryExpectedTypesAndValues() {
        // bool spec -> TYPE_BOOL_TRUE
        List<FlagSpec> autoread = PhixitTweaks.specs("aa_message_autoread");
        assertEquals(PhixitSnapshot.TYPE_BOOL_TRUE, autoread.get(0).flag.type);

        // double spec -> TYPE_DOUBLE with the encoded value
        List<FlagSpec> speed = PhixitTweaks.specs("aa_speed_hack");
        assertEquals(PhixitSnapshot.TYPE_DOUBLE, speed.get(0).flag.type);
        assertEquals(999.0, Double.longBitsToDouble(speed.get(0).flag.doubleBits), 0.0001);

        // long spec -> TYPE_LONG with the encoded value
        List<FlagSpec> vbar = PhixitTweaks.specs("aa_vertical_bar");
        assertEquals(PhixitSnapshot.TYPE_LONG, vbar.get(0).flag.type);
        assertEquals(40L, vbar.get(0).flag.longValue);

        // string spec -> TYPE_STRING
        List<FlagSpec> ux = PhixitTweaks.specs("uxprototype_tweak");
        FlagSpec urlSpec = ux.get(1);
        assertEquals(PhixitSnapshot.TYPE_STRING, urlSpec.flag.type);
        assertEquals("+ URL +", urlSpec.flag.stringValue);

        // bool false spec
        List<FlagSpec> batt = PhixitTweaks.specs("aa_battery_outline");
        assertEquals(PhixitSnapshot.TYPE_BOOL_FALSE, batt.get(0).flag.type);
    }

    @Test
    public void specsUseBothConfigPackages() {
        // kill_telemetry mixes PKG_CAR and PKG_GEARHEAD; ensure both appear.
        List<FlagSpec> kt = PhixitTweaks.specs("kill_telemetry");
        boolean sawCar = false, sawGearhead = false;
        for (FlagSpec s : kt) {
            if (FlagSpec.PKG_CAR.equals(s.pkg)) sawCar = true;
            if (FlagSpec.PKG_GEARHEAD.equals(s.pkg)) sawGearhead = true;
        }
        assertTrue(sawCar);
        assertTrue(sawGearhead);
    }
}

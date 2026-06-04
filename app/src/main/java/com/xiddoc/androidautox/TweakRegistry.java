package com.xiddoc.androidautox;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for "which flags does an enabled tweak set". Static tweaks
 * defer to {@link PhixitTweaks}; dynamic-value tweaks (HUN/bitrate) are rebuilt from the
 * value the user saved when they applied it, so the background re-apply job can
 * reconstruct them headlessly.
 */
public final class TweakRegistry {

    private TweakRegistry() {}

    /** Every tweak key that maps to flag overrides and can be auto-re-applied. */
    public static final String[] ALL_KEYS = {
            // static
            "aa_message_autoread", "aa_speed_hack", "multi_display", "aa_six_tap",
            "coolwalk_daynight_tweak", "aa_battery_outline", "aa_activate_coolwalk",
            "aa_deactivate_coolwalk", "aa_material_you", "aa_activate_assistant_tips",
            "aa_activate_declinesms", "aa_new_seekbar", "bluetooth_pairing_off",
            "kill_telemetry", "uxprototype_tweak", "aa_inertial_scroll", "aa_vertical_bar",
            // special
            "battery_saver_warning",
            // dynamic (value from saved pref)
            "aa_hun_ms", "aa_media_hun", "aa_bitrate_usb", "aa_bitrate_wireless",
            // dynamic (whitelist from appsListPref); the re-apply job re-asserts the
            // flags only -- it never reinstalls apps (that stays in patchforapps()).
            "aa_patched_apps",
    };

    /** Flags for a tweak key, with dynamic values filled in from saved prefs. */
    public static List<FlagSpec> specsFor(Context ctx, String key) {
        SharedPreferences sp = ctx.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE);
        switch (key) {
            case "battery_saver_warning":
                return batteryWarningSpecs();
            case "aa_hun_ms":
                return hunSpecs(sp.getInt("messaging_hun_value", 0));
            case "aa_media_hun":
                return mediaHunSpecs(sp.getInt("media_hun_value", 0));
            case "aa_bitrate_usb":
                return usbBitrateSpecs(sp.getFloat("usb_bitrate_value", 0));
            case "aa_bitrate_wireless":
                return wifiBitrateSpecs(sp.getFloat("wifi_bitrate_value", 0));
            case "aa_patched_apps":
                return patchedAppsSpecs(ctx);
            default:
                return PhixitTweaks.specs(key);
        }
    }

    public static List<FlagSpec> batteryWarningSpecs() {
        List<FlagSpec> l = new ArrayList<FlagSpec>();
        l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "BatterySaver__warning_enabled", false));
        return l;
    }

    public static List<FlagSpec> hunSpecs(int value) {
        List<FlagSpec> l = new ArrayList<FlagSpec>();
        l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "SystemUi__hun_default_heads_up_timeout_ms", value));
        return l;
    }

    public static List<FlagSpec> mediaHunSpecs(int value) {
        List<FlagSpec> l = new ArrayList<FlagSpec>();
        l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "SystemUi__media_hun_in_rail_widget_timeout_ms", value));
        return l;
    }

    public static List<FlagSpec> usbBitrateSpecs(double value) {
        List<FlagSpec> l = new ArrayList<FlagSpec>();
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_1080p_usb", 16000000 * value));
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_1080p_usb_hevc", 3000000 * value));
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_480p_usb", 8000000 * value));
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_480p_usb_hevc", 1000000 * value));
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_720p_usb", 12000000 * value));
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_720p_usb_hevc", 2000000 * value));
        return l;
    }

    public static List<FlagSpec> wifiBitrateSpecs(double value) {
        List<FlagSpec> l = new ArrayList<FlagSpec>();
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_1080p_wireless", 16000000 * value));
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_1080p_wireless_hevc", 3000000 * value));
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_480p_wireless", 8000000 * value));
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_480p_wireless_hevc", 1000000 * value));
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_720p_wireless", 12000000 * value));
        l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_720p_wireless_hevc", 2000000 * value));
        return l;
    }

    /**
     * Flags for the "patch custom apps" tweak. The whitelist value is dynamic:
     * it is rebuilt from the package names the user selected in {@code appsListPref}.
     * Only the FLAGS are produced here -- the app uninstall/reinstall loop lives in
     * {@code MainActivity.patchforapps()} and is intentionally NOT part of this spec
     * path, so the headless re-apply job re-asserts flags without touching apps.
     */
    public static List<FlagSpec> patchedAppsSpecs(Context ctx) {
        SharedPreferences apps = ctx.getSharedPreferences("appsListPref", Context.MODE_PRIVATE);
        StringBuilder whiteList = new StringBuilder();
        for (Map.Entry<String, ?> entry : apps.getAll().entrySet()) {
            if (whiteList.length() > 0) whiteList.append(",");
            whiteList.append(entry.getKey());
        }
        String whiteListString = whiteList.toString();

        List<FlagSpec> l = new ArrayList<FlagSpec>();
        l.add(FlagSpec.str(FlagSpec.PKG_CAR, "app_white_list", whiteListString));
        l.add(FlagSpec.str(FlagSpec.PKG_CAR, "car_connect_broadcast_whitelist", whiteListString));
        l.add(FlagSpec.str(FlagSpec.PKG_GEARHEAD, "AppValidation__allowed_package_list", ""));
        l.add(FlagSpec.str(FlagSpec.PKG_GEARHEAD, "AppValidation__blocked_packages_by_installer", ""));
        l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "AppValidation__should_bypass_validation", true));
        l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "AppValidation__play_install_api", false));
        l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "AppValidation__swallow_play_api_exception", true));
        l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "AppValidation__swallow_play_api_exception_return_value", true));
        l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "should_bypass_validation", true));
        l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarProjectionValidator__filter_disabled_packages_in_ispackageallowed_method", false));
        l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "UnknownSources__allow_full_screen_apps", true));
        return l;
    }

    /** Collects the specs for every currently-enabled tweak. */
    public static List<FlagSpec> enabledSpecs(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE);
        List<FlagSpec> all = new ArrayList<FlagSpec>();
        for (String key : ALL_KEYS) {
            if (!sp.getBoolean(key, false)) continue;
            List<FlagSpec> s = specsFor(ctx, key);
            if (s != null) all.addAll(s);
        }
        return all;
    }

    /** True if at least one flag-mapped tweak is currently enabled. */
    public static boolean anyEnabled(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE);
        for (String key : ALL_KEYS) {
            if (sp.getBoolean(key, false)) return true;
        }
        return false;
    }
}

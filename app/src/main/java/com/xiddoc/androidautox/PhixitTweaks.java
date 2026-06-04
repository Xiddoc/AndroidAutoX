package com.xiddoc.androidautox;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of AndroidAutoX tweaks mapped to the flag overrides they apply,
 * for the new "phixit" Phenotype schema. Generated from the legacy
 * FlagOverrides INSERT statements. Dynamic tweaks (HUN/bitrate SeekBar values,
 * app whitelist) are handled separately and are not in this registry.
 */
public final class PhixitTweaks {

    private PhixitTweaks() {}

    /** Returns the flag specs for a tweak key, or null if not a registry tweak. */
    public static List<FlagSpec> specs(String key) {
        List<FlagSpec> l = new ArrayList<FlagSpec>();
        switch (key) {
            case "aa_message_autoread":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Messaging__voice_messages_read_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Messaging__direct_reply_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Messaging__autoplay_messages_enabled", true));
                break;
            case "aa_speed_hack":
                l.add(FlagSpec.dbl(FlagSpec.PKG_GEARHEAD, "CarSensorParameters__max_parked_speed_gps_sensor", 999.0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_GEARHEAD, "CarSensorParameters__max_parked_speed_wheel_sensor", 999.0));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "VisualPreview__unchained", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "VisualPreview__chained", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "VisualPreview__unchained_experiment_id", true));
                break;
            case "multi_display":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "MultiDisplay__enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "MultiDisplay__clustersim_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "MultiDisplay__gal_munger_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "MultiDisplay__cluster_launcher_enabled", true));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "MultiDisplay__aux_display_default_configuration", 1L));
                break;
            case "aa_six_tap":
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "ContentBrowse__drawer_default_allowed_taps_touchpad", 999L));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ContentBrowse__enable_speed_bump_projected", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ContentBrowse__keyboard_force_disabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Dialer__speedbump_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Mesquite__speedbump_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ContentBrowse__speedbump_force_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "McFly__speedbump_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Media__projected_speedbump_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Watevra__speedbump_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Watevra__speedbump_map_interactivity_events_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Watevra__speedbump_non_scroll_events_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ContentBrowse__sixtap_force_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ContentBrowse__permits_chart", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ContentBrowse__use_updated_list_view_kill_switch", true));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "TouchpadUiNavigation__multimove_penalty_mm", 0L));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Watevra__speedbump_max_list_size", 400L));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Watevra__max_list_size", 400L));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Watevra__speedbump_max_grid_list_size", 300L));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Watevra__max_grid_list_size", 300L));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Watevra__speedbump_map_interactivity_enabled", true));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__max_list_size_with_speedbump", 300L));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__add_default_screen_size_value_kill_switch", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__allow_long_text_while_parked_kill_switch", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__allow_secondary_actions_in_half_lists", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__cluster_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__list_template_fab_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__grid_template_fab_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__tab_template_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__task_limit_restrictions_allows_overflow", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "AppQualityTester__developer_setting_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Assistant__transcription_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__app_driven_refresh_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__app_driven_refresh_enabled_for_undefined_category", false));
                break;
            case "coolwalk_daynight_tweak":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__day_night_theme_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__enable_palette_swap_by_broadcast", true));
                break;
            case "aa_battery_outline":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "BatterySaver__icon_outline_enabled", false));
                break;
            case "aa_activate_coolwalk":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Assistant__coolwalk_suggestions_grpc_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__media_rec_card_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__opt_in _default", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__rail_dock_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__rail_dock_four_app_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__rail_widget_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__allow_focus_input", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__assistant_media_rec_shortcut_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__assistant_suggestions_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__canonical_vertical_rail_default", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__choose_assistant_suggestion_over_app_suggestion", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Media__coolwalk_playback_gradient_scrim_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Media__favorites_button_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__streamed_media_recommendations_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Media__foreground_search_fab_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__a4c_suggestions_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__rotary_proximity_navigation", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__semi_wide_vertical_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__short_canonical_vertical_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__three_actions_hun_ui_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__indicate_severe_thermal_status", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__focus_check_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__fix_status_bar_highlight_ghosting_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__use_widescreen_crossfade", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__dashboard_placement_customization_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__media_notification_high_priority_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__launcher_settings_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Weather__enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Weather__icon_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Weather__preinstalled_frx_toggle_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Boardwalk__news_browser_available", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "SystemUi__car_ui_entry_use_configuration_context_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "SystemUi__media_switcher_page_while_started_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "SystemUi__projection_notification_hun_sbn_converter_hack_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "SystemUi__rail_assistant_media_rec_enabled_on_focus_screens", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "SystemUi__wallpaper_backdrop_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "AppListUi__use_updated_calendar_ui", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__is_toggle_allowed_in_map_and_pane_templates_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__messaging_aap_host_logic_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__tab_template_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CompanionDeviceManager__integration_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__allow_all_inputs_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Media__show_album_art_for_suggestion", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Media__show_settings_button_in_browse_view", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "CarAppLibrary__radio_buttons_ui_changes_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__improve_startup", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Media__custom_action_assert_connection_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__rail_widget_user_education_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__navigation_signal_to_assistant_enabled", true));
                break;
            case "aa_deactivate_coolwalk":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Assistant__coolwalk_suggestions_grpc_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__fishfood_nag_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__media_rec_card_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__opt_in _default", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__rail_dock_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__rail_dock_four_app_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__rail_widget_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__allow_focus_input", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__assistant_media_rec_shortcut_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__assistant_suggestions_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__canonical_vertical_rail_default", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__canonical_vertical_rail_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__choose_assistant_suggestion_over_app_suggestion", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Media__coolwalk_playback_gradient_scrim_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Media__favorites_button_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__streamed_media_recommendations_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__a4c_suggestions_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__rotary_proximity_navigation", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__semi_wide_vertical_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__short_canonical_vertical_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__three_actions_hun_ui_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__indicate_severe_thermal_status", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__use_widescreen_crossfade", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__dashboard_placement_customization_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__media_notification_high_priority_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__add_boardwalk_theme_attrs_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__choreograph_start_composition_kill_switch", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__rail_hotseat_check_app_available_kill_switch", false));
                break;
            case "aa_material_you":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "SystemUi__material_you_settings_enabled", true));
                break;
            case "aa_activate_assistant_tips":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "LauncherShortcuts__assistant_shortcut_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "LauncherApps__clean_up_cujs_kill_switch", true));
                break;
            case "aa_activate_declinesms":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Messaging__decline_call_message_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Messaging__template_ui_enabled", true));
                break;
            case "aa_new_seekbar":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Media__tappable_progress_bar_enabled", true));
                break;
            case "bluetooth_pairing_off":
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "BluetoothPairing__car_bluetooth_service_disable", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "BluetoothPairing__car_bluetooth_service_skip_pairing", true));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "BluetoothPairing__connect_bluetooth_timeout", 1.0));
                break;
            case "kill_telemetry":
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "CarEventLoggerRefactorFeature__convert_car_setup_analytics_telemetry", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "CarServiceTelemetry__enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "CarServiceTelemetry__is_wifi_kbps_logging_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "CarServiceTelemetry__log_battery_temperature", false));
                l.add(FlagSpec.lng(FlagSpec.PKG_CAR, "CarServiceTelemetry__wifi_latency_log_frequency_ms", 99999999L));
                l.add(FlagSpec.lng(FlagSpec.PKG_CAR, "ConnectivityLogging__heartbeat_interval_ms", 99999999L));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "TelemetryDriveIdFeature__enable_log_event_validation", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "TelemetryDriveIdFeature__enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "UsbStatusLoggingFeature__monitor_usb_ping_telemetry_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "TelemetryDriveIdForGearheadFeature__enable_frx_setup_logging_via_gearhead", false));
                l.add(FlagSpec.lng(FlagSpec.PKG_CAR, "AudioStatsLoggingFeature__audio_stats_logging_period_milliseconds", 99999999L));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "FrameworkMediaStatsLoggingFeature__is_media_stats_queue_time_logging_enabled", false));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__num_background_threads", 0L));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__include_extra_events", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__enable_heartbeat", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "WifiChannelLogging__enabled", false));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__session_info_dump_size", 0L));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "BluetoothMetadataLogger__enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "CarEventLoggerRefactorFeature__convert_car_analytics_telemetry", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Bugfix__sensitive_permissions_extra_logging", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__log_bluetooth_rssi", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__save_log_when_usb_starts", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__skip_retroactive_usb_logging", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "InternetConnectivityLogging__enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Telemetry__local_logging", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "WirelessProjectionInGearhead__wireless_wifi_additional_start_logging", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Dialer__r_telemetry_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "AssistantSilenceDiagnostics__enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "TelemetryDriveIdForGearheadFeature__enable_continuous_telemetry_binding", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "TelemetryDriveIdForGearheadFeature__enable_telemetry_impl_conversion", false));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__long_session_timeout_ms", 1L));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__short_session_timeout_ms", 1L));
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__session_timeout_ms", 1000L));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "ConnectivityLogging__use_realtime_if_invalid", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Performance__primes_logging_enabled", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Telemetry__westworld_logging_enabled_kill_switch", true));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "enable_blueooth_fsm_telemetry", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "Performance__use_optimized_car_activities", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "Messaging__assistant_notification_data_sharing_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "CarProjectionValidator__measure_latency_enabled", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "PhenotypeProcessStableFlags__first_read_latency", false));
                l.add(FlagSpec.bool(FlagSpec.PKG_CAR, "PhenotypeProcessStableFlags__legacy_flag_infrastructure_enabled", true));
                break;
            case "uxprototype_tweak":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "UxPrototype__enabled", true));
                l.add(FlagSpec.str(FlagSpec.PKG_GEARHEAD, "UxPrototype__url", "+ URL +"));
                break;
            case "aa_inertial_scroll":
                l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "SystemUi__inertial_scrolling_enabled", true));
                break;
            case "aa_vertical_bar":
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "SystemUi__horizontal_rail_canonical_breakpoint_dp", 40L));
                break;

            // Dynamic-value tweaks: names only (values set at apply time from the
            // SeekBar); listed so revert() can restore their captured baseline.
            case "aa_hun_ms":
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "SystemUi__hun_default_heads_up_timeout_ms", 0L));
                break;
            case "aa_media_hun":
                l.add(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "SystemUi__media_hun_in_rail_widget_timeout_ms", 0L));
                break;
            case "aa_bitrate_usb":
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_1080p_usb", 0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_1080p_usb_hevc", 0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_480p_usb", 0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_480p_usb_hevc", 0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_720p_usb", 0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_720p_usb_hevc", 0));
                break;
            case "aa_bitrate_wireless":
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_1080p_wireless", 0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_1080p_wireless_hevc", 0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_480p_wireless", 0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_480p_wireless_hevc", 0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_720p_wireless", 0));
                l.add(FlagSpec.dbl(FlagSpec.PKG_CAR, "VideoEncoderParamsFeature__bitrate_720p_wireless_hevc", 0));
                break;
            default:
                return null;
        }
        return l;
    }

    public static boolean has(String key) { return specs(key) != null; }
}

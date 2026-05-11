package com.camera.Protobuf.config;

import java.util.Arrays;
import java.util.List;

/**
 * GR 模式 CameraUnit protobuf 配置。
 *
 * 重点：
 * 1. UI 模式显示是 gr。
 * 2. CameraUnit 实际读取的是 gr_mode。
 * 3. 所以这里只修改 gr_mode。
 * 4. 不再创建 gr，避免 key=40/48/49/86/87 缺失导致功能不全。
 */
public final class GrModeConfig implements ProtobufConfig {

    private static final String MODE_GR_CAMERA_UNIT = "gr_mode";

    private static final String ENTRY_COMMON =
            "main_menu,other_app,video_other_app,quick_launch,watch,gimbal";

    private static final String ENTRY_NO_OTHER_VIDEO =
            "main_menu,quick_launch,watch,gimbal";

    private static final String ENTRY_RATIO =
            "main_menu,quick_launch,watch,gimbal,other_app,video_other_app";

    @Override
    public String name() {
        return "GrModeConfig";
    }

    @Override
    public boolean apply(ProtobufEditor editor) {
        boolean changed = false;

        changed |= editor.addFeaturesWithTemplate(MODE_GR_CAMERA_UNIT, 0, defaultFeatures());

        changed |= editor.addFeaturesWithTemplate(MODE_GR_CAMERA_UNIT, 40, cameraType40Features());

        changed |= editor.addFeaturesWithTemplate(MODE_GR_CAMERA_UNIT, 48, cameraType40Features());

        changed |= editor.addFeaturesWithTemplate(MODE_GR_CAMERA_UNIT, 49, cameraType49Features());

        changed |= editor.addFeaturesWithTemplate(MODE_GR_CAMERA_UNIT, 86, cameraType40Features());

        changed |= editor.addFeaturesWithTemplate(MODE_GR_CAMERA_UNIT, 87, cameraType40Features());

        return changed;
    }

    private List<FeatureInfo> defaultFeatures() {
        return Arrays.asList(
                new FeatureInfo(
                        "com.oplus.camera.feature.capture_defer",
                        "com.oplus.camera.feature.capture_defer",
                        "",
                        "",
                        "",
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.gr_photo",
                        "com.oplus.camera.feature.gr_photo",
                        "",
                        "",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.filter",
                        "feature_filter_index",
                        "[0~100]",
                        "0",
                        ENTRY_COMMON,
                        "int"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.scale.focus",
                        "com.oplus.camera.feature.scale.focus",
                        "[on,off]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.iso",
                        "pref_professional_iso_key_manual",
                        "[on,off]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.exposure_time",
                        "pref_professional_exposure_time_key_manual",
                        "[on,off]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.preview.flash.mode",
                        "pref_camera_flashmode_key",
                        "[on,off,torch,auto]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.assist_view",
                        "pref_assist_gradienter",
                        "[on,off]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.focus_exposure",
                        "com.oplus.camera.feature.focus_exposure",
                        "",
                        "",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.time_shutter",
                        "com.oplus.camera.feature.time_shutter",
                        "",
                        "",
                        ENTRY_COMMON,
                        "string"
                )
        );
    }

    private List<FeatureInfo> cameraType40Features() {
        return Arrays.asList(
                new FeatureInfo(
                        "com.oplus.camera.feature.temperature_notification",
                        "key_request_disable_flash_by_high_temperature",
                        "[off,on]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.preview.flash.mode",
                        "key_active_flash_mode",
                        "[on,off,torch,auto]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.temperature_notification",
                        "key_active_flash_mode_for_temperature_notification",
                        "[off,auto,on,torch]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.preview.flash.mode",
                        "key_disable_flash",
                        "[off,on]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.preview.focus.mode",
                        "com.oplus.preview.focus.mode",
                        "[1,3,4]",
                        "1",
                        ENTRY_COMMON,
                        "int"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.zoom",
                        "com.oplus.camera.feature.zoom",
                        "",
                        "",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.capture_params",
                        "pref_super_raw_control_key",
                        "[off,on,super_raw]",
                        "off",
                        ENTRY_NO_OTHER_VIDEO,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.frame_ratio",
                        "pref_camera_photo_ratio_key",
                        "[standard,full,square,16_9]",
                        "standard",
                        ENTRY_RATIO,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.face_detect",
                        "com.oplus.camera.feature.face_detect",
                        "[on,off]",
                        "on",
                        ENTRY_COMMON,
                        "string"
                )
        );
    }

    private List<FeatureInfo> cameraType49Features() {
        return Arrays.asList(
                new FeatureInfo(
                        "com.oplus.camera.feature.temperature_notification",
                        "key_request_disable_flash_by_high_temperature",
                        "[off,on]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.preview.flash.mode",
                        "key_active_flash_mode",
                        "[on,off,torch,auto]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.ten_bit",
                        "com.oplus.camera.feature.ten_bit",
                        "",
                        "",
                        "main_menu",
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.temperature_notification",
                        "key_active_flash_mode_for_temperature_notification",
                        "[off,auto,on,torch]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.preview.flash.mode",
                        "key_disable_flash",
                        "[off,on]",
                        "off",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.preview.focus.mode",
                        "com.oplus.preview.focus.mode",
                        "[1,3,4]",
                        "1",
                        ENTRY_COMMON,
                        "int"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.zoom",
                        "com.oplus.camera.feature.zoom",
                        "",
                        "",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.capture_params",
                        "pref_super_raw_control_key",
                        "[off,on,super_raw]",
                        "off",
                        ENTRY_NO_OTHER_VIDEO,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.frame_ratio",
                        "pref_camera_photo_ratio_key",
                        "[standard,full,square,16_9]",
                        "standard",
                        ENTRY_RATIO,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.preview.flash.mode",
                        "key_update_flash_support_state",
                        "[true,false]",
                        "true",
                        ENTRY_COMMON,
                        "boolean"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.zoom",
                        "key_zoom_support_flash_state_change",
                        "[true,false]",
                        "false",
                        ENTRY_COMMON,
                        "boolean"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.face_detect",
                        "com.oplus.camera.feature.face_detect",
                        "[on,off]",
                        "on",
                        ENTRY_COMMON,
                        "string"
                ),
                new FeatureInfo(
                        "com.oplus.camera.feature.filter",
                        "feature_filter_index",
                        "[0~100]",
                        "0",
                        ENTRY_COMMON,
                        "int"
                )
        );
    }
}
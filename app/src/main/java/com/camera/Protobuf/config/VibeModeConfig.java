package com.camera.Protobuf.config;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public final class VibeModeConfig implements ProtobufConfig {

    private static final String TAG = "ProtobufFeature";

    private static final String MODE_NAME = "vibe_mode";

    private static final String ENTRY_COMMON =
            "main_menu,other_app,video_other_app,quick_launch,watch,gimbal";

    private static final String ENTRY_MAIN_QUICK =
            "main_menu,quick_launch,watch,gimbal";

    private static final String ENTRY_RATIO =
            "main_menu,quick_launch,watch,gimbal,other_app,video_other_app";

    private static final int[] CAMERA_TYPE_KEYS = new int[]{
            0,
            40,
            48,
            49,
            80,
            86,
            87,
            313
    };

    public VibeModeConfig() {
    }

    @Override
    public String name() {
        return "VibeModeConfig";
    }

    public String getName() {
        return "VibeModeConfig";
    }

    @Override
    public boolean apply(ProtobufEditor editor) {
        return applyInternal(editor);
    }

    public boolean applyConfig(ProtobufEditor editor) {
        return applyInternal(editor);
    }

    public boolean run(ProtobufEditor editor) {
        return applyInternal(editor);
    }

    private boolean applyInternal(ProtobufEditor editor) {
        if (editor == null) {
            log("VibeModeConfig editor null");
            return false;
        }

        boolean changed = false;

        try {
            /*
             * 关键修复：
             *
             * 当前崩溃点：
             * HdrAllRoutePresenter.java:17
             * List.contains() null
             *
             * 所以必须删除 vibe_mode 里的 HDR 路由相关功能：
             * - com.oplus.camera.feature.hdr_all_route
             * - com.oplus.preview.capture.hdrMode
             * - key_should_reopen_hdr_mode
             * - pref_camera_hdr_mode_key
             *
             * 但不能把 vibe_photo / filter / scale.focus / ISO / 曝光 / 闪光灯等全部删掉。
             */
            /*
             * Vibe 拍照要跟 photo 链路一致，不能从 professional/master 模板创建。
             * 先强制用 photo_mode 克隆 vibe_mode，再删 HDR 路由冲突项，最后补 Vibe 专属 feature。
             */
            changed |= editor.replaceModeFromTemplate(MODE_NAME, "photo_mode");

            changed |= removeDangerHdrFeatures(editor);
            changed |= removeBeautyFeatures(editor);

            List<FeatureInfo> features = createVibeNoHdrFeatureList();

            for (int cameraTypeKey : CAMERA_TYPE_KEYS) {
                boolean oneChanged = editor.addFeaturesWithTemplate(
                        MODE_NAME,
                        cameraTypeKey,
                        features
                );

                if (oneChanged) {
                    changed = true;
                }

                log(
                        "VibeModeConfig restore no-hdr mode="
                                + MODE_NAME
                                + " key="
                                + cameraTypeKey
                                + " changed="
                                + oneChanged
                                + " size="
                                + features.size()
                );
            }

            log("VibeModeConfig restore no-hdr done changed=" + changed);

            return changed;
        } catch (Throwable t) {
            log("VibeModeConfig restore no-hdr failed " + t);
            return changed;
        }
    }

    private boolean removeDangerHdrFeatures(ProtobufEditor editor) {
        try {
            List<String> keywords = new ArrayList<>();

            keywords.add("hdr_all_route");
            keywords.add("hdrMode");
            keywords.add("pref_camera_hdr_mode_key");
            keywords.add("key_should_reopen_hdr_mode");

            boolean changed = editor.removeFeaturesByKeywordFromMode(MODE_NAME, keywords);

            log("VibeModeConfig remove danger HDR features changed=" + changed);

            return changed;
        } catch (Throwable t) {
            log("VibeModeConfig remove danger HDR features failed " + t);
            return false;
        }
    }

    private boolean removeBeautyFeatures(ProtobufEditor editor) {
        try {
            List<String> keywords = new ArrayList<>();

            keywords.add("com.oplus.camera.feature.beauty");
            keywords.add("FaceBeauty");
            keywords.add("facebeauty");
            keywords.add("beauty");

            boolean changed = editor.removeFeaturesByKeywordFromMode(MODE_NAME, keywords);

            log("VibeModeConfig remove beauty features for photo chain changed=" + changed);

            return changed;
        } catch (Throwable t) {
            log("VibeModeConfig remove beauty features failed " + t);
            return false;
        }
    }

    private List<FeatureInfo> createVibeNoHdrFeatureList() {
        List<FeatureInfo> list = new ArrayList<>();

        /*
         * 保留 Vibe 核心入口。
         */
        add(
                list,
                "com.oplus.camera.feature.vibe_photo",
                "com.oplus.camera.feature.vibe_photo",
                "",
                "",
                ENTRY_COMMON,
                "string"
        );

        /*
         * 保留 capture defer。
         */
        add(
                list,
                "com.oplus.camera.feature.capture_defer",
                "com.oplus.camera.feature.capture_defer",
                "",
                "",
                "",
                "string"
        );

        /*
         * 保留滤镜。
         */
        add(
                list,
                "com.oplus.camera.feature.filter",
                "feature_filter_index",
                "[0~100]",
                "0",
                ENTRY_COMMON,
                "int"
        );

        add(
                list,
                "com.oplus.preview.flash.mode",
                "pref_camera_flash_changed_by_filter",
                "[on,off]",
                "off",
                ENTRY_COMMON,
                "string"
        );

        /*
         * 保留焦段 / 缩放相关。
         */
        add(
                list,
                "com.oplus.camera.feature.scale.focus",
                "com.oplus.camera.feature.scale.focus",
                "[on,off]",
                "off",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.camera.feature.zoom",
                "com.oplus.camera.feature.zoom",
                "",
                "",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.preview.focus.mode",
                "com.oplus.preview.focus.mode",
                "[1,3,4]",
                "1",
                ENTRY_COMMON,
                "int"
        );

        /*
         * 保留专业参数。
         */
        add(
                list,
                "com.oplus.camera.feature.iso",
                "pref_professional_iso_key_manual",
                "[on,off]",
                "off",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.camera.feature.exposure_time",
                "pref_professional_exposure_time_key_manual",
                "[on,off]",
                "off",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.camera.feature.capture_params",
                "pref_super_raw_control_key",
                "[off,on,super_raw]",
                "off",
                ENTRY_MAIN_QUICK,
                "string"
        );

        /*
         * 保留闪光灯，但不保留 HDR 冲突项。
         */
        add(
                list,
                "com.oplus.preview.flash.mode",
                "pref_camera_flashmode_key",
                "[on,off,torch,auto]",
                "off",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.preview.flash.mode",
                "key_active_flash_mode",
                "[on,off,torch,auto]",
                "off",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.preview.flash.mode",
                "key_disable_flash",
                "[off,on]",
                "off",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.preview.flash.mode",
                "key_update_flash_support_state",
                "[true,false]",
                "true",
                ENTRY_COMMON,
                "boolean"
        );

        add(
                list,
                "com.oplus.camera.feature.zoom",
                "key_zoom_support_flash_state_change",
                "[true,false]",
                "false",
                ENTRY_COMMON,
                "boolean"
        );

        /*
         * 保留温控相关。
         */
        add(
                list,
                "com.oplus.camera.feature.temperature_notification",
                "key_request_disable_flash_by_high_temperature",
                "[off,on]",
                "off",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.camera.feature.temperature_notification",
                "key_active_flash_mode_for_temperature_notification",
                "[off,auto,on,torch]",
                "off",
                ENTRY_COMMON,
                "string"
        );

        /*
         * 保留比例 / 人脸 / 辅助 / 对焦曝光 / 延时快门。
         */
        add(
                list,
                "com.oplus.camera.feature.frame_ratio",
                "pref_camera_photo_ratio_key",
                "[standard,full,square,16_9]",
                "standard",
                ENTRY_RATIO,
                "string"
        );

        add(
                list,
                "com.oplus.camera.feature.face_detect",
                "com.oplus.camera.feature.face_detect",
                "[on,off]",
                "on",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.camera.feature.assist_view",
                "pref_assist_gradienter",
                "[on,off]",
                "off",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.camera.feature.focus_exposure",
                "com.oplus.camera.feature.focus_exposure",
                "",
                "",
                ENTRY_COMMON,
                "string"
        );

        add(
                list,
                "com.oplus.camera.feature.time_shutter",
                "com.oplus.camera.feature.time_shutter",
                "",
                "",
                ENTRY_COMMON,
                "string"
        );

        /*
         * 10bit 在原始 key=49 里有，放进来不会触发 HDR 路由。
         */
        add(
                list,
                "com.oplus.camera.feature.ten_bit",
                "com.oplus.camera.feature.ten_bit",
                "",
                "",
                "main_menu",
                "string"
        );

        return list;
    }

    private void add(
            List<FeatureInfo> list,
            String featureName,
            String featureKeyName,
            String featureValueRange,
            String featureDefaultValue,
            String entryType,
            String featureValueType
    ) {
        if (list == null) {
            return;
        }

        FeatureInfo info = new FeatureInfo(
                safe(featureName),
                safe(featureKeyName),
                safe(featureValueRange),
                safe(featureDefaultValue),
                safe(entryType),
                safe(featureValueType)
        );

        list.add(info);

        log(
                "VibeModeConfig add no-hdr feature="
                        + featureName
                        + " key="
                        + featureKeyName
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void log(String msg) {
        try {
            Log.e(TAG, msg);
        } catch (Throwable ignored) {
        }
    }
}
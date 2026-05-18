package com.camera.Protobuf.config;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * AI Scenery 模式 camera_unit_feature_config.protobuf 配置。
 *
 * 来源：用户提供的 camera_unit_feature_config(1).protobuf 中：
 * camera_feature_table.mode_feature_tables["ai_scenery_mode"]
 *
 * 这里不改磁盘上的 /odm/etc/camera/config/camera_unit_feature_config.protobuf，
 * 而是在 ProtobufFeature Hook 到 FeatureTable.parseFrom(byte[]) 后，
 * 直接重建内存里的 ai_scenery_mode feature table。
 */
public final class AiSceneryModeConfig implements ProtobufConfig {

    private static final String TAG = "ProtobufFeature";

    private static final String MODE_NAME = "ai_scenery_mode";

    private static final String V15_MARK = "AI_SCENERY_PROTO_V15_EXACT_NO_HDR_KEEP_HIGH_PIXEL";

    private static final String ENTRY_COMMON =
            "main_menu,other_app,video_other_app,quick_launch,watch,gimbal";

    @Override
    public String name() {
        return "AiSceneryModeConfig";
    }

    public String getName() {
        return name();
    }

    public boolean applyConfig(ProtobufEditor editor) {
        return apply(editor);
    }

    public boolean run(ProtobufEditor editor) {
        return apply(editor);
    }

    @Override
    public boolean apply(ProtobufEditor editor) {
        if (editor == null) {
            log("AiSceneryModeConfig editor null " + V15_MARK);
            return false;
        }

        boolean changed = replaceModeExact(editor);

        log("AiSceneryModeConfig done changed=" + changed + " " + V15_MARK);

        return changed;
    }

    /**
     * 精确重建 ai_scenery_mode：
     * - cameraTypeKey 0：9 个 feature，已去掉 hdr_all_route，避免 HdrAllRoutePresenter 空指针闪退
     * - cameraTypeKey 49：8 个 feature
     *
     * 不使用 addFeaturesWithTemplate 追加，避免从 photo/common 模板混入多余 feature。
     * HDR 会触发 HdrAllRoutePresenter 中 List.contains(null) 崩溃，这里不加入 hdr_all_route。
     */
    private boolean replaceModeExact(ProtobufEditor editor) {
        try {
            Object featureTableBuilder = getPrivateField(editor, "featureTableBuilder");

            if (featureTableBuilder == null) {
                log("AiSceneryModeConfig featureTableBuilder null");
                return false;
            }

            Object cameraFeatureTableBuilder =
                    ProtobufEditor.callMethod(featureTableBuilder, "getCameraFeatureTableBuilder");

            Object modeMapObject =
                    ProtobufEditor.callMethod(cameraFeatureTableBuilder, "getMutableModeFeatureTables");

            if (!(modeMapObject instanceof Map)) {
                log("AiSceneryModeConfig modeFeatureTables is not Map");
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> modeFeatureTablesMap = (Map<Object, Object>) modeMapObject;

            Object sourceModeTable = findModeTemplate(modeFeatureTablesMap);

            if (sourceModeTable == null) {
                log("AiSceneryModeConfig no mode template found");
                return false;
            }

            Object template0 = findCameraTypeTemplate(modeFeatureTablesMap, 0);
            Object template49 = findCameraTypeTemplate(modeFeatureTablesMap, 49);

            if (template0 == null) {
                template0 = findAnyCameraTypeTemplate(modeFeatureTablesMap);
            }

            if (template49 == null) {
                template49 = template0;
            }

            if (template0 == null || template49 == null) {
                log("AiSceneryModeConfig cameraType template missing key0="
                        + (template0 != null)
                        + " key49="
                        + (template49 != null));
                return false;
            }

            Object modeBuilder = ProtobufEditor.callMethod(sourceModeTable, "toBuilder");

            Object cameraTypeMapObject =
                    ProtobufEditor.callMethod(modeBuilder, "getMutableCameraTypeFeatureTables");

            if (!(cameraTypeMapObject instanceof Map)) {
                log("AiSceneryModeConfig cameraTypeFeatureTables is not Map");
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> cameraTypeTablesMap = (Map<Object, Object>) cameraTypeMapObject;

            cameraTypeTablesMap.clear();

            putExactCameraType(editor, cameraTypeTablesMap, 0, template0, defaultFeatures());
            putExactCameraType(editor, cameraTypeTablesMap, 49, template49, cameraType49Features());

            Object newModeTable = ProtobufEditor.callMethod(modeBuilder, "build");

            modeFeatureTablesMap.put(MODE_NAME, newModeTable);

            log("AiSceneryModeConfig replace exact mode success " + V15_MARK + " mode="
                    + MODE_NAME
                    + " cameraTypeKeys=0,49");

            return true;
        } catch (Throwable t) {
            log("AiSceneryModeConfig replace exact failed " + t);
            return false;
        }
    }

    private Object findModeTemplate(Map<Object, Object> modeFeatureTablesMap) {
        Object value = modeFeatureTablesMap.get(MODE_NAME);
        if (value != null) {
            log("AiSceneryModeConfig use existing mode template " + MODE_NAME);
            return value;
        }

        String[] preferredModes = new String[]{
                "photo_mode",
                "common",
                "professional_mode",
                "xpan_mode",
                "master_mode",
                "portrait_mode",
                "night_mode"
        };

        for (String preferredMode : preferredModes) {
            value = modeFeatureTablesMap.get(preferredMode);
            if (value != null) {
                log("AiSceneryModeConfig use fallback mode template " + preferredMode);
                return value;
            }
        }

        for (Object item : modeFeatureTablesMap.values()) {
            if (item != null) {
                log("AiSceneryModeConfig use first non-null mode template");
                return item;
            }
        }

        return null;
    }

    private Object findCameraTypeTemplate(Map<Object, Object> modeFeatureTablesMap, int cameraTypeKey) {
        String[] preferredModes = new String[]{
                MODE_NAME,
                "photo_mode",
                "common",
                "professional_mode",
                "xpan_mode",
                "master_mode",
                "portrait_mode",
                "night_mode"
        };

        for (String preferredMode : preferredModes) {
            Object modeTable = modeFeatureTablesMap.get(preferredMode);
            Object cameraTypeTable = getCameraTypeFromMode(modeTable, cameraTypeKey);
            if (cameraTypeTable != null) {
                log("AiSceneryModeConfig use cameraType template mode="
                        + preferredMode
                        + " key="
                        + cameraTypeKey);
                return cameraTypeTable;
            }
        }

        for (Map.Entry<Object, Object> entry : modeFeatureTablesMap.entrySet()) {
            Object cameraTypeTable = getCameraTypeFromMode(entry.getValue(), cameraTypeKey);
            if (cameraTypeTable != null) {
                log("AiSceneryModeConfig use fallback cameraType template mode="
                        + String.valueOf(entry.getKey())
                        + " key="
                        + cameraTypeKey);
                return cameraTypeTable;
            }
        }

        return null;
    }

    private Object findAnyCameraTypeTemplate(Map<Object, Object> modeFeatureTablesMap) {
        for (Object modeTable : modeFeatureTablesMap.values()) {
            if (modeTable == null) {
                continue;
            }

            try {
                Object modeBuilder = ProtobufEditor.callMethod(modeTable, "toBuilder");
                Object cameraTypeMapObject =
                        ProtobufEditor.callMethod(modeBuilder, "getMutableCameraTypeFeatureTables");

                if (!(cameraTypeMapObject instanceof Map)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<Object, Object> cameraTypeTablesMap = (Map<Object, Object>) cameraTypeMapObject;

                for (Object value : cameraTypeTablesMap.values()) {
                    if (value != null) {
                        log("AiSceneryModeConfig use any cameraType template");
                        return value;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private Object getCameraTypeFromMode(Object modeTable, int cameraTypeKey) {
        if (modeTable == null) {
            return null;
        }

        try {
            Object modeBuilder = ProtobufEditor.callMethod(modeTable, "toBuilder");
            Object cameraTypeMapObject =
                    ProtobufEditor.callMethod(modeBuilder, "getMutableCameraTypeFeatureTables");

            if (!(cameraTypeMapObject instanceof Map)) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> cameraTypeTablesMap = (Map<Object, Object>) cameraTypeMapObject;

            Object value = cameraTypeTablesMap.get(Integer.valueOf(cameraTypeKey));
            if (value != null) {
                return value;
            }

            return cameraTypeTablesMap.get(cameraTypeKey);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void putExactCameraType(
            ProtobufEditor editor,
            Map<Object, Object> cameraTypeTablesMap,
            int cameraTypeKey,
            Object templateCameraTypeTable,
            List<FeatureInfo> features
    ) throws Exception {
        Object cameraTypeBuilder = ProtobufEditor.callMethod(templateCameraTypeTable, "toBuilder");

        clearFeatureList(cameraTypeBuilder);

        for (FeatureInfo featureInfo : features) {
            Object feature = createFeature(editor, featureInfo);
            if (feature == null) {
                log("AiSceneryModeConfig create feature null key="
                        + cameraTypeKey
                        + " feature="
                        + featureInfo.featureName
                        + " featureKey="
                        + featureInfo.featureKeyName);
                continue;
            }

            ProtobufEditor.callMethod(cameraTypeBuilder, "addFeatureList", feature);
        }

        Object newCameraTypeTable = ProtobufEditor.callMethod(cameraTypeBuilder, "build");

        cameraTypeTablesMap.put(Integer.valueOf(cameraTypeKey), newCameraTypeTable);

        log("AiSceneryModeConfig replace exact cameraType success " + V15_MARK + " mode="
                + MODE_NAME
                + " key="
                + cameraTypeKey
                + " featureCount="
                + features.size());
    }

    private void clearFeatureList(Object cameraTypeBuilder) {
        try {
            ProtobufEditor.callMethod(cameraTypeBuilder, "clearFeatureList");
            return;
        } catch (Throwable ignored) {
        }

        try {
            List<?> featureList = getFeatureList(cameraTypeBuilder);
            if (featureList == null) {
                return;
            }

            for (int i = featureList.size() - 1; i >= 0; i--) {
                ProtobufEditor.callMethod(cameraTypeBuilder, "removeFeatureList", Integer.valueOf(i));
            }
        } catch (Throwable ignored) {
        }
    }

    private List<?> getFeatureList(Object cameraTypeBuilder) {
        try {
            Object value = ProtobufEditor.callMethod(cameraTypeBuilder, "getFeatureListList");
            if (value instanceof List) {
                return (List<?>) value;
            }
        } catch (Throwable ignored) {
        }

        try {
            Object value = ProtobufEditor.callMethod(cameraTypeBuilder, "getFeatureList");
            if (value instanceof List) {
                return (List<?>) value;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private Object createFeature(ProtobufEditor editor, FeatureInfo info) {
        try {
            Method method = ProtobufEditor.class.getDeclaredMethod("createFeature", FeatureInfo.class);
            method.setAccessible(true);
            return method.invoke(editor, info);
        } catch (Throwable t) {
            log("AiSceneryModeConfig createFeature failed feature="
                    + info.featureName
                    + " key="
                    + info.featureKeyName
                    + " "
                    + t);
            return null;
        }
    }

    private Object getPrivateField(Object target, String fieldName) {
        if (target == null || fieldName == null) {
            return null;
        }

        Class<?> current = target.getClass();

        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable t) {
                log("AiSceneryModeConfig get field failed " + fieldName + " " + t);
                return null;
            }
        }

        return null;
    }

    private List<FeatureInfo> defaultFeatures() {
        return Arrays.asList(
                feature("com.oplus.camera.feature.capture_defer", "com.oplus.camera.feature.capture_defer", "", "", "", "string"),
                feature("com.oplus.camera.feature.beauty", "com.oplus.camera.feature.beauty", "[on,off]", "off", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.filter", "feature_filter_index", "[0~100]", "0", ENTRY_COMMON, "int"),
                feature("com.oplus.camera.feature.zoom", "com.oplus.camera.feature.zoom", "", "", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.assist_view", "com.oplus.camera.feature.assist_view", "", "", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.assist_view", "pref_assist_gradienter", "[on,off]", "off", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.ai.scenery", "com.oplus.camera.feature.ai.scenery", "", "", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.time_shutter", "com.oplus.camera.feature.time_shutter", "", "", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.focus_exposure", "com.oplus.camera.feature.focus_exposure", "", "", ENTRY_COMMON, "string")
        );
    }

    private List<FeatureInfo> cameraType49Features() {
        return Arrays.asList(
                feature("com.oplus.preview.focus.mode", "com.oplus.preview.focus.mode", "[1,3,4]", "1", ENTRY_COMMON, "int"),
                feature("com.oplus.camera.feature.filter", "feature_filter_index", "[0~100]", "0", ENTRY_COMMON, "int"),
                feature("com.oplus.camera.feature.zoom", "com.oplus.camera.feature.zoom", "", "", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.zoom", "pref_ultra_wide_high_picture_size_key", "[on,off]", "off", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.zoom", "pref_none_sat_ultra_wide_angle_key", "[on,off]", "off", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.remosaic", "com.oplus.camera.feature.remosaic", "", "", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.face_detect", "com.oplus.camera.feature.face_detect", "[on,off]", "on", ENTRY_COMMON, "string"),
                feature("com.oplus.camera.feature.zoom", "pref_none_sat_tele_angle_key", "[on,off]", "off", ENTRY_COMMON, "string")
        );
    }

    private FeatureInfo feature(
            String featureName,
            String featureKeyName,
            String featureValueRange,
            String featureDefaultValue,
            String entryType,
            String featureValueType
    ) {
        return new FeatureInfo(
                safe(featureName),
                safe(featureKeyName),
                safe(featureValueRange),
                safe(featureDefaultValue),
                safe(entryType),
                safe(featureValueType)
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

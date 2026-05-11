package com.camera.Protobuf.config;

import android.util.Log;

import com.camera.Protobuf.ProtobufModule;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Protobuf 通用修改工具。
 *
 * 当前策略：
 * 1. 允许创建 gr_mode。
 * 2. 允许创建 gr_mode 下缺失的 cameraTypeKey。
 * 3. 不再创建 gr。
 * 4. cameraTypeKey 缺失时从模板 mode 的同 key 复制。
 * 5. 同 key 找不到时，才从 key=0 复制。
 */
public final class ProtobufEditor {

    private static final String FEATURE_CLASS =
            "com.oplus.ocs.camera.configure.ProtobufFeatureConfig$Feature";

    private final ProtobufModule host;

    private final Object featureTableBuilder;

    private List<String> strPool;

    public ProtobufEditor(ProtobufModule host, Object featureTableBuilder) {
        this.host = host;
        this.featureTableBuilder = featureTableBuilder;
        this.strPool = readStrPool(featureTableBuilder);
    }

    public boolean addFeaturesWithTemplate(String modeName, int cameraTypeKey, List<FeatureInfo> features) {
        return addFeaturesInternal(modeName, cameraTypeKey, features, true, true);
    }

    public boolean addFeaturesExistingOnly(String modeName, int cameraTypeKey, List<FeatureInfo> features) {
        return addFeaturesInternal(modeName, cameraTypeKey, features, false, false);
    }

    public boolean addFeaturesSafe(String modeName, int cameraTypeKey, List<FeatureInfo> features) {
        return addFeaturesInternal(modeName, cameraTypeKey, features, true, true);
    }

    public boolean addFeatures(String modeName, int cameraTypeKey, List<FeatureInfo> features) {
        return addFeaturesInternal(modeName, cameraTypeKey, features, true, true);
    }

    public boolean addFeatures(
            String modeName,
            int cameraTypeKey,
            List<FeatureInfo> features,
            boolean allowCreateMode
    ) {
        return addFeaturesInternal(modeName, cameraTypeKey, features, allowCreateMode, true);
    }

    private boolean addFeaturesInternal(
            String modeName,
            int cameraTypeKey,
            List<FeatureInfo> features,
            boolean allowCreateMode,
            boolean allowCreateCameraType
    ) {
        boolean changed = false;

        if (features == null || features.isEmpty()) {
            return false;
        }

        try {
            Object cameraFeatureTableBuilder =
                    callMethod(featureTableBuilder, "getCameraFeatureTableBuilder");

            Object modeMapObject =
                    callMethod(cameraFeatureTableBuilder, "getMutableModeFeatureTables");

            if (!(modeMapObject instanceof Map)) {
                host.xlog(Log.ERROR, "ProtobufEditor modeFeatureTables is not Map");
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> modeFeatureTablesMap = (Map<Object, Object>) modeMapObject;

            Object modeBuilder = getOrCreateModeBuilder(modeFeatureTablesMap, modeName, allowCreateMode);

            if (modeBuilder == null) {
                host.xlog(
                        Log.ERROR,
                        "ProtobufEditor skip mode, modeBuilder null mode="
                                + modeName
                                + " allowCreateMode="
                                + allowCreateMode
                );
                return false;
            }

            Object cameraTypeMapObject =
                    callMethod(modeBuilder, "getMutableCameraTypeFeatureTables");

            if (!(cameraTypeMapObject instanceof Map)) {
                host.xlog(Log.ERROR, "ProtobufEditor cameraTypeFeatureTables is not Map mode=" + modeName);
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> cameraTypeTablesMap = (Map<Object, Object>) cameraTypeMapObject;

            Object cameraTypeTable = getCameraTypeTableIfExists(cameraTypeTablesMap, cameraTypeKey);

            if (cameraTypeTable == null && allowCreateCameraType) {
                cameraTypeTable = createCameraTypeTableFromTemplate(
                        modeFeatureTablesMap,
                        cameraTypeTablesMap,
                        modeName,
                        cameraTypeKey
                );
            }

            if (cameraTypeTable == null) {
                host.xlog(
                        Log.ERROR,
                        "ProtobufEditor skip missing cameraTypeKey mode="
                                + modeName
                                + " key="
                                + cameraTypeKey
                );
                return false;
            }

            Object cameraTypeBuilder = callMethod(cameraTypeTable, "toBuilder");

            for (FeatureInfo featureInfo : features) {
                Object feature = createFeature(featureInfo);

                if (feature == null) {
                    host.xlog(
                            Log.ERROR,
                            "ProtobufEditor create feature failed mode="
                                    + modeName
                                    + " key="
                                    + cameraTypeKey
                                    + " feature="
                                    + featureInfo.featureName
                    );
                    continue;
                }

                int featureNameIndex = ensureStringIndex(featureInfo.featureName);
                int featureKeyNameIndex = ensureStringIndex(featureInfo.featureKeyName);

                if (hasFeature(cameraTypeTable, featureNameIndex, featureKeyNameIndex)) {
                    host.xlog(
                            Log.ERROR,
                            "ProtobufEditor feature already exists, skip mode="
                                    + modeName
                                    + " key="
                                    + cameraTypeKey
                                    + " feature="
                                    + featureInfo.featureName
                                    + " featureKey="
                                    + featureInfo.featureKeyName
                    );
                    continue;
                }

                callMethod(cameraTypeBuilder, "addFeatureList", feature);

                changed = true;

                host.xlog(
                        Log.ERROR,
                        "ProtobufEditor add feature success mode="
                                + modeName
                                + " key="
                                + cameraTypeKey
                                + " feature="
                                + featureInfo.featureName
                                + " featureKey="
                                + featureInfo.featureKeyName
                );
            }

            if (changed) {
                Object newCameraTypeTable = callMethod(cameraTypeBuilder, "build");

                cameraTypeTablesMap.put(Integer.valueOf(cameraTypeKey), newCameraTypeTable);

                Object newModeTable = callMethod(modeBuilder, "build");

                modeFeatureTablesMap.put(modeName, newModeTable);

                host.xlog(
                        Log.ERROR,
                        "ProtobufEditor save mode success mode="
                                + modeName
                                + " cameraTypeKey="
                                + cameraTypeKey
                );
            }
        } catch (Throwable t) {
            host.xlog(
                    Log.ERROR,
                    "ProtobufEditor addFeatures failed mode="
                            + modeName
                            + " cameraTypeKey="
                            + cameraTypeKey,
                    t
            );
            return false;
        }

        return changed;
    }

    private Object getOrCreateModeBuilder(
            Map<Object, Object> modeFeatureTablesMap,
            String modeName,
            boolean allowCreateMode
    ) {
        try {
            Object modeTable = modeFeatureTablesMap.get(modeName);

            if (modeTable != null) {
                host.xlog(Log.ERROR, "ProtobufEditor use existing mode=" + modeName);
                return callMethod(modeTable, "toBuilder");
            }

            if (!allowCreateMode) {
                host.xlog(Log.ERROR, "ProtobufEditor mode not found and create disabled mode=" + modeName);
                return null;
            }

            Object templateModeTable = findSafeTemplateModeTable(modeFeatureTablesMap);

            if (templateModeTable == null) {
                host.xlog(Log.ERROR, "ProtobufEditor no safe template mode table, cannot create mode=" + modeName);
                return null;
            }

            Object templateBuilder = callMethod(templateModeTable, "toBuilder");

            Object newModeTable = callMethod(templateBuilder, "build");

            modeFeatureTablesMap.put(modeName, newModeTable);

            host.xlog(Log.ERROR, "ProtobufEditor create missing mode from template: " + modeName);

            return callMethod(newModeTable, "toBuilder");
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "ProtobufEditor getOrCreateModeBuilder failed mode=" + modeName, t);
            return null;
        }
    }

    private Object createCameraTypeTableFromTemplate(
            Map<Object, Object> modeFeatureTablesMap,
            Map<Object, Object> targetCameraTypeTablesMap,
            String targetModeName,
            int cameraTypeKey
    ) {
        try {
            Object templateCameraTypeTable = findTemplateCameraTypeTableBySameKey(
                    modeFeatureTablesMap,
                    cameraTypeKey
            );

            if (templateCameraTypeTable == null) {
                templateCameraTypeTable = targetCameraTypeTablesMap.get(Integer.valueOf(0));
            }

            if (templateCameraTypeTable == null) {
                for (Object value : targetCameraTypeTablesMap.values()) {
                    if (value != null) {
                        templateCameraTypeTable = value;
                        break;
                    }
                }
            }

            if (templateCameraTypeTable == null) {
                host.xlog(
                        Log.ERROR,
                        "ProtobufEditor no template cameraTypeTable mode="
                                + targetModeName
                                + " key="
                                + cameraTypeKey
                );
                return null;
            }

            Object templateBuilder = callMethod(templateCameraTypeTable, "toBuilder");

            Object newCameraTypeTable = callMethod(templateBuilder, "build");

            targetCameraTypeTablesMap.put(Integer.valueOf(cameraTypeKey), newCameraTypeTable);

            host.xlog(
                    Log.ERROR,
                    "ProtobufEditor create missing cameraTypeKey from template mode="
                            + targetModeName
                            + " key="
                            + cameraTypeKey
            );

            return newCameraTypeTable;
        } catch (Throwable t) {
            host.xlog(
                    Log.ERROR,
                    "ProtobufEditor createCameraTypeTableFromTemplate failed mode="
                            + targetModeName
                            + " key="
                            + cameraTypeKey,
                    t
            );
            return null;
        }
    }

    private Object findTemplateCameraTypeTableBySameKey(
            Map<Object, Object> modeFeatureTablesMap,
            int cameraTypeKey
    ) {
        String[] preferredModes = new String[]{
                "professional_mode",
                "xpan_mode",
                "master_mode",
                "photo_mode",
                "common",
                "portrait_mode",
                "night_mode"
        };

        for (String preferredMode : preferredModes) {
            Object modeTable = modeFeatureTablesMap.get(preferredMode);

            Object cameraTypeTable = getCameraTypeFromModeTable(modeTable, cameraTypeKey);

            if (cameraTypeTable != null) {
                host.xlog(
                        Log.ERROR,
                        "ProtobufEditor use same-key cameraType template mode="
                                + preferredMode
                                + " key="
                                + cameraTypeKey
                );
                return cameraTypeTable;
            }
        }

        for (Map.Entry<Object, Object> entry : modeFeatureTablesMap.entrySet()) {
            Object modeName = entry.getKey();
            Object modeTable = entry.getValue();

            Object cameraTypeTable = getCameraTypeFromModeTable(modeTable, cameraTypeKey);

            if (cameraTypeTable != null) {
                host.xlog(
                        Log.ERROR,
                        "ProtobufEditor use fallback same-key cameraType template mode="
                                + String.valueOf(modeName)
                                + " key="
                                + cameraTypeKey
                );
                return cameraTypeTable;
            }
        }

        return null;
    }

    private Object getCameraTypeFromModeTable(Object modeTable, int cameraTypeKey) {
        if (modeTable == null) {
            return null;
        }

        try {
            Object modeBuilder = callMethod(modeTable, "toBuilder");

            Object cameraTypeMapObject =
                    callMethod(modeBuilder, "getMutableCameraTypeFeatureTables");

            if (!(cameraTypeMapObject instanceof Map)) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> cameraTypeTablesMap = (Map<Object, Object>) cameraTypeMapObject;

            return getCameraTypeTableIfExists(cameraTypeTablesMap, cameraTypeKey);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object findSafeTemplateModeTable(Map<Object, Object> modeFeatureTablesMap) {
        String[] preferredModes = new String[]{
                "professional_mode",
                "xpan_mode",
                "master_mode",
                "photo_mode",
                "common",
                "portrait_mode",
                "night_mode"
        };

        for (String preferredMode : preferredModes) {
            Object value = modeFeatureTablesMap.get(preferredMode);

            if (value != null) {
                host.xlog(Log.ERROR, "ProtobufEditor use template mode=" + preferredMode);
                return value;
            }
        }

        for (Object value : modeFeatureTablesMap.values()) {
            if (value != null) {
                host.xlog(Log.ERROR, "ProtobufEditor use first non-null template mode");
                return value;
            }
        }

        return null;
    }

    public boolean removeFeaturesByKeywordFromMode(String modeName, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }

        boolean changed = false;

        try {
            Object cameraFeatureTableBuilder =
                    callMethod(featureTableBuilder, "getCameraFeatureTableBuilder");

            Object modeMapObject =
                    callMethod(cameraFeatureTableBuilder, "getMutableModeFeatureTables");

            if (!(modeMapObject instanceof Map)) {
                host.xlog(Log.ERROR, "ProtobufEditor remove modeFeatureTables is not Map");
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> modeFeatureTablesMap = (Map<Object, Object>) modeMapObject;

            Object modeTable = modeFeatureTablesMap.get(modeName);

            if (modeTable == null) {
                host.xlog(Log.ERROR, "ProtobufEditor remove skip missing mode=" + modeName);
                return false;
            }

            Object modeBuilder = callMethod(modeTable, "toBuilder");

            Object cameraTypeMapObject =
                    callMethod(modeBuilder, "getMutableCameraTypeFeatureTables");

            if (!(cameraTypeMapObject instanceof Map)) {
                host.xlog(Log.ERROR, "ProtobufEditor remove cameraTypeFeatureTables is not Map mode=" + modeName);
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> cameraTypeTablesMap = (Map<Object, Object>) cameraTypeMapObject;

            List<Map.Entry<Object, Object>> entries = new ArrayList<>(cameraTypeTablesMap.entrySet());

            for (Map.Entry<Object, Object> entry : entries) {
                Object cameraTypeKey = entry.getKey();
                Object cameraTypeTable = entry.getValue();

                if (cameraTypeTable == null) {
                    continue;
                }

                Object cameraTypeBuilder = callMethod(cameraTypeTable, "toBuilder");

                List<?> featureList = getFeatureList(cameraTypeBuilder);

                if (featureList == null || featureList.isEmpty()) {
                    continue;
                }

                boolean cameraChanged = false;

                for (int i = featureList.size() - 1; i >= 0; i--) {
                    Object feature = featureList.get(i);

                    int featureNameIndex = getIntMethod(feature, "getFeatureNameIndex", -1);
                    int featureKeyNameIndex = getIntMethod(feature, "getFeatureKeyNameIndex", -1);

                    String featureName = getStringFromPool(featureNameIndex);
                    String featureKeyName = getStringFromPool(featureKeyNameIndex);

                    if (containsAnyKeyword(featureName, featureKeyName, keywords)) {
                        callMethod(cameraTypeBuilder, "removeFeatureList", Integer.valueOf(i));

                        cameraChanged = true;

                        host.xlog(
                                Log.ERROR,
                                "ProtobufEditor remove feature success mode="
                                        + modeName
                                        + " cameraTypeKey="
                                        + String.valueOf(cameraTypeKey)
                                        + " index="
                                        + i
                                        + " feature="
                                        + featureName
                                        + " featureKey="
                                        + featureKeyName
                        );
                    }
                }

                if (cameraChanged) {
                    Object newCameraTypeTable = callMethod(cameraTypeBuilder, "build");

                    cameraTypeTablesMap.put(cameraTypeKey, newCameraTypeTable);

                    changed = true;
                }
            }

            if (changed) {
                Object newModeTable = callMethod(modeBuilder, "build");

                modeFeatureTablesMap.put(modeName, newModeTable);

                host.xlog(Log.ERROR, "ProtobufEditor remove save mode success mode=" + modeName);
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "ProtobufEditor removeFeaturesByKeywordFromMode failed mode=" + modeName, t);
            return false;
        }

        return changed;
    }

    private Object getCameraTypeTableIfExists(Map<Object, Object> cameraTypeTablesMap, int cameraTypeKey) {
        Object value = cameraTypeTablesMap.get(Integer.valueOf(cameraTypeKey));

        if (value != null) {
            return value;
        }

        value = cameraTypeTablesMap.get(cameraTypeKey);

        if (value != null) {
            return value;
        }

        return null;
    }

    private Object createFeature(FeatureInfo info) {
        try {
            int featureNameIndex = ensureStringIndex(info.featureName);
            int featureKeyNameIndex = ensureStringIndex(info.featureKeyName);
            int featureValueRangeIndex = ensureStringIndex(info.featureValueRange);
            int featureDefaultValueIndex = ensureStringIndex(info.featureDefaultValue);
            int entryTypeIndex = ensureStringIndex(info.entryType);
            int featureValueTypeIndex = ensureStringIndex(info.featureValueType);

            if (featureNameIndex < 0 || featureKeyNameIndex < 0 || entryTypeIndex < 0 || featureValueTypeIndex < 0) {
                host.xlog(
                        Log.ERROR,
                        "ProtobufEditor required string index invalid feature="
                                + info.featureName
                                + " key="
                                + info.featureKeyName
                );
                return null;
            }

            Class<?> featureClass = host.loadCameraClass(FEATURE_CLASS);

            Object featureBuilder = callMethod(featureClass, "newBuilder");

            callMethod(featureBuilder, "setFeatureNameIndex", featureNameIndex);
            callMethod(featureBuilder, "setFeatureKeyNameIndex", featureKeyNameIndex);
            callMethod(featureBuilder, "setFeatureValueRangeIndex", featureValueRangeIndex);
            callMethod(featureBuilder, "setFeatureDefaultValueIndex", featureDefaultValueIndex);
            callMethod(featureBuilder, "setEntryTypeIndex", entryTypeIndex);
            callMethod(featureBuilder, "setFeatureValueTypeIndex", featureValueTypeIndex);

            return callMethod(featureBuilder, "build");
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "ProtobufEditor createFeature failed feature=" + info.featureName, t);
            return null;
        }
    }

    private int ensureStringIndex(String value) {
        String safeValue = value == null ? "" : value;

        int oldIndex = strPool.indexOf(safeValue);

        if (oldIndex >= 0) {
            return oldIndex;
        }

        try {
            callMethod(featureTableBuilder, "addStrPool", safeValue);

            strPool = readStrPool(featureTableBuilder);

            int newIndex = strPool.indexOf(safeValue);

            if (newIndex >= 0) {
                host.xlog(
                        Log.ERROR,
                        "ProtobufEditor add strPool success value="
                                + safeValue
                                + " index="
                                + newIndex
                );
                return newIndex;
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "ProtobufEditor addStrPool method failed value=" + safeValue, t);
        }

        try {
            Object strPoolFieldObject = getFieldValue(featureTableBuilder, "strPool_");

            if (strPoolFieldObject instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> realList = (List<String>) strPoolFieldObject;

                realList.add(safeValue);

                strPool = readStrPool(featureTableBuilder);

                int newIndex = strPool.indexOf(safeValue);

                if (newIndex >= 0) {
                    host.xlog(
                            Log.ERROR,
                            "ProtobufEditor direct strPool add success value="
                                    + safeValue
                                    + " index="
                                    + newIndex
                    );
                    return newIndex;
                }
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "ProtobufEditor direct strPool add failed value=" + safeValue, t);
        }

        return -1;
    }

    private boolean hasFeature(Object cameraTypeTable, int featureNameIndex, int featureKeyNameIndex) {
        try {
            List<?> listObject = getFeatureList(cameraTypeTable);

            if (listObject == null) {
                return false;
            }

            for (Object feature : listObject) {
                int oldFeatureNameIndex = getIntMethod(feature, "getFeatureNameIndex", -1);
                int oldFeatureKeyNameIndex = getIntMethod(feature, "getFeatureKeyNameIndex", -1);

                if (oldFeatureNameIndex == featureNameIndex && oldFeatureKeyNameIndex == featureKeyNameIndex) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private List<?> getFeatureList(Object receiver) {
        try {
            Object value = callMethod(receiver, "getFeatureListList");

            if (value instanceof List) {
                return (List<?>) value;
            }
        } catch (Throwable ignored) {
        }

        try {
            Object value = callMethod(receiver, "getFeatureList");

            if (value instanceof List) {
                return (List<?>) value;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private int getIntMethod(Object receiver, String methodName, int defaultValue) {
        try {
            Object value = callMethod(receiver, methodName);

            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
        }

        return defaultValue;
    }

    private boolean containsAnyKeyword(String featureName, String featureKeyName, List<String> keywords) {
        String safeName = featureName == null ? "" : featureName.toLowerCase();
        String safeKey = featureKeyName == null ? "" : featureKeyName.toLowerCase();

        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }

            String safeKeyword = keyword.toLowerCase();

            if (safeKeyword.length() == 0) {
                continue;
            }

            if (safeName.contains(safeKeyword)) {
                return true;
            }

            if (safeKey.contains(safeKeyword)) {
                return true;
            }
        }

        return false;
    }

    private String getStringFromPool(int index) {
        if (index < 0) {
            return "";
        }

        if (index >= strPool.size()) {
            return "";
        }

        String value = strPool.get(index);

        return value == null ? "" : value;
    }

    private static List<String> readStrPool(Object featureTableBuilder) {
        try {
            Object value = callMethod(featureTableBuilder, "getStrPoolList");

            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) value;

                return new ArrayList<>(list);
            }
        } catch (Throwable ignored) {
        }

        try {
            Object value = getFieldValue(featureTableBuilder, "strPool_");

            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) value;

                return new ArrayList<>(list);
            }
        } catch (Throwable ignored) {
        }

        return new ArrayList<>();
    }

    private static Object getFieldValue(Object object, String fieldName) throws Exception {
        Field field = findField(object.getClass(), fieldName);

        field.setAccessible(true);

        return field.get(object);
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;

        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        throw new NoSuchFieldException(fieldName);
    }

    public static Object callMethod(Object receiver, String methodName, Object... args) throws Exception {
        Class<?> clazz;
        Object target;

        if (receiver instanceof Class) {
            clazz = (Class<?>) receiver;
            target = null;
        } else {
            clazz = receiver.getClass();
            target = receiver;
        }

        Method method = findMethod(clazz, methodName, args);

        method.setAccessible(true);

        return method.invoke(target, args);
    }

    private static Method findMethod(Class<?> clazz, String methodName, Object[] args) throws NoSuchMethodException {
        Class<?> current = clazz;

        while (current != null) {
            Method[] methods = current.getDeclaredMethods();

            for (Method method : methods) {
                if (!methodName.equals(method.getName())) {
                    continue;
                }

                Class<?>[] parameterTypes = method.getParameterTypes();

                if (parameterTypes.length != args.length) {
                    continue;
                }

                if (isParameterTypesMatch(parameterTypes, args)) {
                    return method;
                }
            }

            current = current.getSuperclass();
        }

        throw new NoSuchMethodException(clazz.getName() + "#" + methodName);
    }

    private static boolean isParameterTypesMatch(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];

            if (arg == null) {
                continue;
            }

            Class<?> parameterType = wrapPrimitive(parameterTypes[i]);
            Class<?> argType = wrapPrimitive(arg.getClass());

            if (!parameterType.isAssignableFrom(argType)) {
                return false;
            }
        }

        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> clazz) {
        if (!clazz.isPrimitive()) {
            return clazz;
        }

        if (clazz == int.class) {
            return Integer.class;
        }

        if (clazz == long.class) {
            return Long.class;
        }

        if (clazz == boolean.class) {
            return Boolean.class;
        }

        if (clazz == byte.class) {
            return Byte.class;
        }

        if (clazz == short.class) {
            return Short.class;
        }

        if (clazz == float.class) {
            return Float.class;
        }

        if (clazz == double.class) {
            return Double.class;
        }

        if (clazz == char.class) {
            return Character.class;
        }

        return clazz;
    }
}
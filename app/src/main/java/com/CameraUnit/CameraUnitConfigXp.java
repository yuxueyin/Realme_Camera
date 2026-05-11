package com.camera.CameraUnit;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * XP 修改 camera_unit_config 运行时内容。
 *
 * 修复点：
 * 1. 不再强制 patch 只有 63 字符的路径/文件名字符串。
 * 2. String / byte[] 只有像完整 camera_unit_config 内容时才 patch。
 * 3. 新增 JSONObject / JSONArray 返回值处理。
 * 4. 真正的 camera_unit_config 很可能已经被 JsonParser 解析成 JSONObject。
 */
public final class CameraUnitConfigXp {

    private static final String TARGET_CONFIG_NAME = "camera_unit_config";

    private static final String UPDATE_HELPER_CLASS =
            "com.oplus.ocs.camera.consumer.apsAdapter.update.UpdateHelper";

    private static final String UPDATE_HELPER_METHOD =
            "getValidConfigData";

    private static final String[] JSON_PARSER_CLASS_CANDIDATES = new String[]{
            "com.oplus.ocs.camera.configure.JsonParser",
            "com.oplus.ocs.camera.configure.CameraUnitJsonParser",
            "com.oplus.ocs.camera.configure.CameraConfigJsonParser",
            "com.oplus.ocs.camera.configure.ConfigJsonParser",
            "com.oplus.ocs.camera.configure.CameraConfigure"
    };

    private final CameraUnitConfigModule host;

    private boolean installed = false;

    public CameraUnitConfigXp(CameraUnitConfigModule host) {
        this.host = host;
    }

    public void install() {
        if (installed) {
            host.xlog(Log.ERROR, "CameraUnitConfigXp already installed, skip");
            return;
        }

        installed = true;

        installUpdateHelperXp();

        installJsonParserXp();
    }

    private void installUpdateHelperXp() {
        try {
            Class<?> updateHelperClass = host.loadCameraClass(UPDATE_HELPER_CLASS);

            Method[] methods = updateHelperClass.getDeclaredMethods();

            boolean hookedAny = false;

            for (Method method : methods) {
                if (!UPDATE_HELPER_METHOD.equals(method.getName())) {
                    continue;
                }

                method.setAccessible(true);

                host.hookMethod(method).intercept(chain -> {
                    List<Object> args = chain.getArgs();

                    String configName = findConfigName(args);

                    Object originalResult = chain.proceed();

                    if (originalResult == null) {
                        return null;
                    }

                    boolean hitByName = isCameraUnitConfig(configName);
                    boolean hitByContent = looksLikeCameraUnitConfig(originalResult);

                    if (!hitByName && !hitByContent) {
                        return originalResult;
                    }

                    host.xlog(
                            Log.ERROR,
                            "CameraUnitConfigXp hit UpdateHelper#getValidConfigData configName="
                                    + configName
                                    + " hitByName="
                                    + hitByName
                                    + " hitByContent="
                                    + hitByContent
                                    + " resultType="
                                    + originalResult.getClass().getName()
                    );

                    return patchOrOriginal(
                            originalResult,
                            "UpdateHelper#getValidConfigData",
                            hitByName
                    );
                });

                hookedAny = true;

                host.xlog(Log.ERROR, "hook success: UpdateHelper#getValidConfigData -> " + method);
            }

            if (!hookedAny) {
                host.xlog(Log.ERROR, "hook failed: no UpdateHelper#getValidConfigData found");
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "installUpdateHelperXp failed", t);
        }
    }

    private void installJsonParserXp() {
        for (String className : JSON_PARSER_CLASS_CANDIDATES) {
            try {
                Class<?> clazz = host.loadCameraClass(className);

                Method[] methods = clazz.getDeclaredMethods();

                int hookCount = 0;

                for (Method method : methods) {
                    if (!shouldHookParserMethod(method)) {
                        continue;
                    }

                    method.setAccessible(true);

                    host.hookMethod(method).intercept(chain -> {
                        List<Object> args = chain.getArgs();

                        String configName = findConfigName(args);

                        Object originalResult = chain.proceed();

                        if (originalResult == null) {
                            return null;
                        }

                        boolean hitByName = isCameraUnitConfig(configName);
                        boolean hitByContent = looksLikeCameraUnitConfig(originalResult);

                        if (!hitByName && !hitByContent) {
                            return originalResult;
                        }

                        host.xlog(
                                Log.ERROR,
                                "CameraUnitConfigXp hit JsonParser method="
                                        + method.toString()
                                        + " configName="
                                        + configName
                                        + " hitByName="
                                        + hitByName
                                        + " hitByContent="
                                        + hitByContent
                                        + " resultType="
                                        + originalResult.getClass().getName()
                        );

                        return patchOrOriginal(
                                originalResult,
                                className + "#" + method.getName(),
                                hitByName
                        );
                    });

                    hookCount++;

                    host.xlog(Log.ERROR, "hook success: JsonParser candidate -> " + method);
                }

                if (hookCount == 0) {
                    host.xlog(Log.ERROR, "JsonParser class found but no target method hooked: " + className);
                } else {
                    host.xlog(Log.ERROR, "JsonParser class hooked count=" + hookCount + " class=" + className);
                }
            } catch (Throwable t) {
                host.xlog(Log.ERROR, "JsonParser candidate not available class=" + className, t);
            }
        }
    }

    private boolean shouldHookParserMethod(Method method) {
        Class<?> returnType = method.getReturnType();

        if (returnType == String.class) {
            return true;
        }

        if (returnType == byte[].class) {
            return true;
        }

        if (returnType == JSONObject.class) {
            return true;
        }

        if (returnType == JSONArray.class) {
            return true;
        }

        String methodName = method.getName();

        if (methodName == null) {
            return false;
        }

        String lowerName = methodName.toLowerCase();

        if (lowerName.contains("loadconfig")) {
            return true;
        }

        if (lowerName.contains("readconfig")) {
            return true;
        }

        if (lowerName.contains("parseconfig")) {
            return true;
        }

        return false;
    }

    private Object patchOrOriginal(Object originalResult, String from, boolean hitByName) {
        try {
            Object patchedResult = patchResult(originalResult, hitByName);

            if (patchedResult != null) {
                host.xlog(Log.ERROR, "CameraUnitConfigXp return patched camera_unit_config from " + from);
                return patchedResult;
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "CameraUnitConfigXp patch failed from " + from, t);
        }

        host.xlog(Log.ERROR, "CameraUnitConfigXp return original from " + from);

        return originalResult;
    }

    private Object patchResult(Object originalResult, boolean hitByName) {
        if (originalResult instanceof JSONObject) {
            JSONObject object = (JSONObject) originalResult;

            boolean looksLike = CameraUnitJsonObjectPatcher.looksLikeCameraUnitConfig(object);

            if (!hitByName && !looksLike) {
                host.xlog(Log.ERROR, "CameraUnitConfigXp JSONObject not camera_unit_config");
                return null;
            }

            boolean changed = CameraUnitJsonObjectPatcher.patchJSONObject(object, host);

            if (changed) {
                host.xlog(Log.ERROR, "CameraUnitConfigXp JSONObject patch success");
                return object;
            }

            host.xlog(Log.ERROR, "CameraUnitConfigXp JSONObject no change");
            return null;
        }

        if (originalResult instanceof JSONArray) {
            host.xlog(Log.ERROR, "CameraUnitConfigXp JSONArray result skip");
            return null;
        }

        return patchTextResult(originalResult, hitByName);
    }

    private Object patchTextResult(Object originalResult, boolean hitByName) {
        String originalText;

        boolean byteArrayResult = false;

        if (originalResult instanceof byte[]) {
            byteArrayResult = true;
            originalText = new String((byte[]) originalResult, StandardCharsets.UTF_8);
        } else if (originalResult instanceof String) {
            originalText = (String) originalResult;
        } else {
            host.xlog(
                    Log.ERROR,
                    "CameraUnitConfigXp unsupported result type="
                            + originalResult.getClass().getName()
            );
            return null;
        }

        if (originalText == null || originalText.trim().length() == 0) {
            host.xlog(Log.ERROR, "CameraUnitConfigXp originalText empty");
            return null;
        }

        boolean looksLikeConfig = looksLikeCameraUnitConfig(originalText);

        /*
         * 关键修复：
         * 命中路径但返回内容太短时，说明返回的是路径/文件名，不是 JSON 内容。
         * 这种不能 patch，否则就会出现 length=63、missing object。
         */
        if (!looksLikeConfig) {
            host.xlog(
                    Log.ERROR,
                    "CameraUnitConfigXp skip text result, not config content"
                            + " hitByName="
                            + hitByName
                            + " length="
                            + originalText.length()
                            + " head="
                            + safeHead(originalText)
            );
            return null;
        }

        host.xlog(
                Log.ERROR,
                "CameraUnitConfigXp start text patch"
                        + " hitByName="
                        + hitByName
                        + " looksLikeConfig="
                        + true
                        + " oldLength="
                        + originalText.length()
                        + " head="
                        + safeHead(originalText)
        );

        String patchedText = CameraUnitConfigPatcher.patch(originalText, host);

        if (patchedText == null || patchedText.trim().length() == 0) {
            host.xlog(Log.ERROR, "CameraUnitConfigXp patchedText empty");
            return null;
        }

        if (patchedText.equals(originalText)) {
            host.xlog(Log.ERROR, "CameraUnitConfigXp text no change");
            return null;
        }

        host.xlog(
                Log.ERROR,
                "CameraUnitConfigXp text patch success oldLength="
                        + originalText.length()
                        + " newLength="
                        + patchedText.length()
        );

        if (byteArrayResult) {
            return patchedText.getBytes(StandardCharsets.UTF_8);
        }

        return patchedText;
    }

    private boolean looksLikeCameraUnitConfig(Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof JSONObject) {
            return CameraUnitJsonObjectPatcher.looksLikeCameraUnitConfig((JSONObject) value);
        }

        if (value instanceof byte[]) {
            return looksLikeCameraUnitConfig(new String((byte[]) value, StandardCharsets.UTF_8));
        }

        return looksLikeCameraUnitConfig(String.valueOf(value));
    }

    private boolean looksLikeCameraUnitConfig(String text) {
        if (text == null) {
            return false;
        }

        String safe = text.trim();

        if (safe.length() < 500) {
            return false;
        }

        boolean hasCameraIdList = safe.contains("\"camera_id_list\"");
        boolean hasModeTypeList = safe.contains("\"mode_type_list\"");
        boolean hasUsecaseInfo = safe.contains("\"usecase_info\"");
        boolean hasModeOperationMode = safe.contains("\"mode_operation_mode\"");
        boolean hasParameterFeatureMapping = safe.contains("\"parameter_feature_mapping\"");
        boolean hasSupportPreviewSize = safe.contains("\"support_default_preview_sizes\"");
        boolean hasCaptureStreamNumber = safe.contains("\"capture_stream_number\"");

        if (hasCameraIdList && hasModeTypeList && hasUsecaseInfo) {
            return true;
        }

        if (hasModeTypeList && hasModeOperationMode && hasUsecaseInfo) {
            return true;
        }

        if (hasParameterFeatureMapping && hasSupportPreviewSize && hasUsecaseInfo) {
            return true;
        }

        if (hasCaptureStreamNumber && hasModeTypeList && hasModeOperationMode) {
            return true;
        }

        return false;
    }

    private String findConfigName(List<Object> args) {
        if (args == null || args.isEmpty()) {
            return "";
        }

        for (Object arg : args) {
            if (arg == null) {
                continue;
            }

            String value = String.valueOf(arg);

            if (isCameraUnitConfig(value)) {
                return value;
            }
        }

        if (args.size() > 1 && args.get(1) != null) {
            return String.valueOf(args.get(1));
        }

        if (args.get(0) != null) {
            return String.valueOf(args.get(0));
        }

        return "";
    }

    private boolean isCameraUnitConfig(String configName) {
        if (configName == null) {
            return false;
        }

        return configName.toLowerCase().contains(TARGET_CONFIG_NAME);
    }

    private String safeHead(String text) {
        if (text == null) {
            return "";
        }

        String safe = text.replace("\n", "\\n").replace("\r", "\\r");

        if (safe.length() > 160) {
            return safe.substring(0, 160);
        }

        return safe;
    }
}
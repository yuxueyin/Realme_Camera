package com.camera.CameraUnit;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;

public final class CameraUnitJsonObjectPatcher {

    private static final String TAG = "CameraUnitXp";

    private static final String MODE_GR = "gr_mode";
    private static final String MODE_VIBE = "vibe_mode";

    private static final String OPERATION_GR = "80BE";
    private static final String OPERATION_VIBE_SAFE = "8001";

    private static final String DEFAULT_REAR_MAIN_CUSTOM_INFO =
            "8010,3_35_3,4096X3072,3_32_3,4096X3072,3_37_3,4096X3072,"
                    + "2_35_3,2640X1980,2_32_3,2640X1980,2_37_3,2640X1980";

    private CameraUnitJsonObjectPatcher() {
    }

    public static boolean looksLikeCameraUnitConfig(JSONObject object) {
        if (object == null) {
            return false;
        }

        try {
            return object.has("mode_type_list")
                    || object.has("mode_operation_mode")
                    || object.has("usecase_info")
                    || object.has("capture_stream_number")
                    || object.has("mode_type_table")
                    || object.has("mode_type");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean patchJSONObject(JSONObject object, Object host) {
        if (object == null) {
            log(host, "CameraUnitJsonObjectPatcher patchJSONObject object null");
            return false;
        }

        boolean changed = patch(object, host);

        log(host, "CameraUnitJsonObjectPatcher patchJSONObject changed=" + changed);

        return changed;
    }

    public static boolean patch(JSONObject root) {
        return patch(root, null);
    }

    public static boolean patch(JSONObject root, Object host) {
        if (root == null) {
            log(host, "CameraUnitJsonObjectPatcher root null");
            return false;
        }

        boolean changed = false;

        try {
            log(
                    host,
                    "CameraUnitJsonObjectPatcher patch begin"
                            + " hasModeTypeList=" + root.has("mode_type_list")
                            + " hasModeOperationMode=" + root.has("mode_operation_mode")
                            + " hasUsecaseInfo=" + root.has("usecase_info")
                            + " hasCaptureStreamNumber=" + root.has("capture_stream_number")
            );

            changed |= patchGrMode(root, host);
            changed |= patchVibeModeRearMainOnlyV3(root, host);

            verify(root, host);

            return changed;
        } catch (Throwable t) {
            log(host, "CameraUnitJsonObjectPatcher patch failed " + t);
            return changed;
        }
    }

    private static boolean patchGrMode(JSONObject root, Object host) {
        boolean changed = false;

        changed |= addModeToCameraType(root, "rear_wide", MODE_GR, host);
        changed |= addModeToCameraType(root, "rear_main", MODE_GR, host);
        changed |= addModeToCameraType(root, "rear_tele", MODE_GR, host);
        changed |= addModeToCameraType(root, "rear_sat", MODE_GR, host);

        changed |= putModeOperation(root, MODE_GR, OPERATION_GR, host);

        changed |= putUsecaseIfMissing(root, "gr_mode_case", createGrModeCase(), host);
        changed |= putUsecaseIfMissing(root, "gr_mode_hq_raw_case", createGrModeHqRawCase(), host);

        changed |= putCaptureStreamNumber(root, MODE_GR, true, true, true, true, host);

        return changed;
    }

    private static boolean patchVibeModeRearMainOnlyV3(JSONObject root, Object host) {
        boolean changed = false;

        changed |= removeModeFromCameraType(root, "rear_wide", MODE_VIBE, host);
        changed |= addModeToCameraType(root, "rear_main", MODE_VIBE, host);
        changed |= removeModeFromCameraType(root, "rear_tele", MODE_VIBE, host);
        changed |= removeModeFromCameraType(root, "rear_sat", MODE_VIBE, host);

        changed |= putModeOperation(root, MODE_VIBE, OPERATION_VIBE_SAFE, host);

        changed |= replaceUsecase(root, "vibe_mode_case", createVibeRearMainOnlyCase(), host);

        changed |= removeCaptureStreamNumber(root, MODE_VIBE, host);

        changed |= patchRearMainCustomInfo(root, host);

        log(host, "CameraUnitJsonObjectPatcher VIBE_REAR_MAIN_ONLY_V3 applied changed=" + changed);

        return changed;
    }

    private static boolean addModeToCameraType(
            JSONObject root,
            String cameraType,
            String modeName,
            Object host
    ) {
        try {
            JSONObject modeTypeList = root.optJSONObject("mode_type_list");

            if (modeTypeList == null) {
                modeTypeList = new JSONObject();
                root.put("mode_type_list", modeTypeList);
                log(host, "CameraUnitJsonObjectPatcher create mode_type_list");
            }

            JSONArray array = modeTypeList.optJSONArray(cameraType);

            if (array == null) {
                array = new JSONArray();
                modeTypeList.put(cameraType, array);
                log(host, "CameraUnitJsonObjectPatcher create mode_type_list array=" + cameraType);
            }

            if (arrayHasValue(array, modeName)) {
                log(host, "CameraUnitJsonObjectPatcher array already has value array=" + cameraType + " value=" + modeName);
                return false;
            }

            array.put(modeName);

            log(host, "CameraUnitJsonObjectPatcher add mode_type_list " + cameraType + " -> " + modeName);

            return true;
        } catch (Throwable t) {
            log(host, "CameraUnitJsonObjectPatcher addModeToCameraType failed cameraType=" + cameraType + " mode=" + modeName + " " + t);
            return false;
        }
    }

    private static boolean removeModeFromCameraType(
            JSONObject root,
            String cameraType,
            String modeName,
            Object host
    ) {
        try {
            JSONObject modeTypeList = root.optJSONObject("mode_type_list");

            if (modeTypeList == null) {
                return false;
            }

            JSONArray oldArray = modeTypeList.optJSONArray(cameraType);

            if (oldArray == null) {
                return false;
            }

            JSONArray newArray = new JSONArray();
            boolean removed = false;

            for (int i = 0; i < oldArray.length(); i++) {
                String item = oldArray.optString(i, "");

                if (modeName.equals(item)) {
                    removed = true;
                    continue;
                }

                newArray.put(item);
            }

            if (!removed) {
                return false;
            }

            modeTypeList.put(cameraType, newArray);

            log(host, "CameraUnitJsonObjectPatcher remove mode_type_list " + cameraType + " -> " + modeName);

            return true;
        } catch (Throwable t) {
            log(host, "CameraUnitJsonObjectPatcher removeModeFromCameraType failed cameraType=" + cameraType + " mode=" + modeName + " " + t);
            return false;
        }
    }

    private static boolean putModeOperation(
            JSONObject root,
            String modeName,
            String operationMode,
            Object host
    ) {
        try {
            JSONObject modeOperation = root.optJSONObject("mode_operation_mode");

            if (modeOperation == null) {
                modeOperation = new JSONObject();
                root.put("mode_operation_mode", modeOperation);
                log(host, "CameraUnitJsonObjectPatcher create mode_operation_mode");
            }

            String oldValue = modeOperation.optString(modeName, "");

            if (operationMode.equalsIgnoreCase(oldValue)) {
                log(host, "CameraUnitJsonObjectPatcher mode_operation_mode " + modeName + " already " + operationMode);
                return false;
            }

            modeOperation.put(modeName, operationMode);

            log(host, "CameraUnitJsonObjectPatcher put mode_operation_mode " + modeName + "=" + operationMode + " old=" + oldValue);

            return true;
        } catch (Throwable t) {
            log(host, "CameraUnitJsonObjectPatcher putModeOperation failed mode=" + modeName + " " + t);
            return false;
        }
    }

    private static boolean putUsecaseIfMissing(
            JSONObject root,
            String usecaseName,
            JSONArray usecaseArray,
            Object host
    ) {
        try {
            JSONObject usecaseInfo = root.optJSONObject("usecase_info");

            if (usecaseInfo == null) {
                usecaseInfo = new JSONObject();
                root.put("usecase_info", usecaseInfo);
                log(host, "CameraUnitJsonObjectPatcher create usecase_info");
            }

            if (usecaseInfo.has(usecaseName)) {
                log(host, "CameraUnitJsonObjectPatcher usecase_info " + usecaseName + " already exists");
                return false;
            }

            usecaseInfo.put(usecaseName, usecaseArray);

            log(host, "CameraUnitJsonObjectPatcher add usecase_info " + usecaseName);

            return true;
        } catch (Throwable t) {
            log(host, "CameraUnitJsonObjectPatcher putUsecaseIfMissing failed usecase=" + usecaseName + " " + t);
            return false;
        }
    }

    private static boolean replaceUsecase(
            JSONObject root,
            String usecaseName,
            JSONArray usecaseArray,
            Object host
    ) {
        try {
            JSONObject usecaseInfo = root.optJSONObject("usecase_info");

            if (usecaseInfo == null) {
                usecaseInfo = new JSONObject();
                root.put("usecase_info", usecaseInfo);
                log(host, "CameraUnitJsonObjectPatcher create usecase_info");
            }

            JSONArray oldArray = usecaseInfo.optJSONArray(usecaseName);

            String oldText = oldArray == null ? "null" : oldArray.toString();
            String newText = usecaseArray == null ? "null" : usecaseArray.toString();

            if (newText.equals(oldText)) {
                log(host, "CameraUnitJsonObjectPatcher replace usecase_info " + usecaseName + " already same");
                return false;
            }

            usecaseInfo.put(usecaseName, usecaseArray);

            log(host, "CameraUnitJsonObjectPatcher replace usecase_info " + usecaseName + " old=" + oldText + " new=" + newText);

            return true;
        } catch (Throwable t) {
            log(host, "CameraUnitJsonObjectPatcher replaceUsecase failed usecase=" + usecaseName + " " + t);
            return false;
        }
    }

    private static boolean putCaptureStreamNumber(
            JSONObject root,
            String modeName,
            boolean rearWide,
            boolean rearMain,
            boolean rearTele,
            boolean rearSat,
            Object host
    ) {
        try {
            JSONObject captureStreamNumber = root.optJSONObject("capture_stream_number");

            if (captureStreamNumber == null) {
                captureStreamNumber = new JSONObject();
                root.put("capture_stream_number", captureStreamNumber);
                log(host, "CameraUnitJsonObjectPatcher create capture_stream_number");
            }

            JSONObject modeObject = captureStreamNumber.optJSONObject(modeName);

            if (modeObject == null) {
                modeObject = new JSONObject();
                captureStreamNumber.put(modeName, modeObject);
                log(host, "CameraUnitJsonObjectPatcher create capture_stream_number mode=" + modeName);
            }

            boolean changed = false;

            if (rearWide) {
                changed |= put2Dol(modeObject, "rear_wide", modeName, host);
            }

            if (rearMain) {
                changed |= put2Dol(modeObject, "rear_main", modeName, host);
            }

            if (rearTele) {
                changed |= put2Dol(modeObject, "rear_tele", modeName, host);
            }

            if (rearSat) {
                changed |= put2Dol(modeObject, "rear_sat", modeName, host);
            }

            if (!changed) {
                log(host, "CameraUnitJsonObjectPatcher capture_stream_number " + modeName + " already exists");
            }

            return changed;
        } catch (Throwable t) {
            log(host, "CameraUnitJsonObjectPatcher putCaptureStreamNumber failed mode=" + modeName + " " + t);
            return false;
        }
    }

    private static boolean put2Dol(
            JSONObject modeObject,
            String cameraType,
            String modeName,
            Object host
    ) {
        try {
            JSONObject cameraObject = modeObject.optJSONObject(cameraType);

            if (cameraObject == null) {
                cameraObject = new JSONObject();
                modeObject.put(cameraType, cameraObject);
            }

            String oldValue = cameraObject.optString("2dol", "");

            if ("2".equals(oldValue)) {
                log(host, "CameraUnitJsonObjectPatcher capture_stream_number " + modeName + " " + cameraType + " already 2dol=2");
                return false;
            }

            cameraObject.put("2dol", "2");

            log(host, "CameraUnitJsonObjectPatcher put capture_stream_number " + modeName + " " + cameraType + " 2dol=2 old=" + oldValue);

            return true;
        } catch (Throwable t) {
            log(host, "CameraUnitJsonObjectPatcher put2Dol failed mode=" + modeName + " cameraType=" + cameraType + " " + t);
            return false;
        }
    }

    private static boolean removeCaptureStreamNumber(
            JSONObject root,
            String modeName,
            Object host
    ) {
        try {
            JSONObject captureStreamNumber = root.optJSONObject("capture_stream_number");

            if (captureStreamNumber == null) {
                log(host, "CameraUnitJsonObjectPatcher capture_stream_number null skip remove " + modeName);
                return false;
            }

            if (!captureStreamNumber.has(modeName)) {
                log(host, "CameraUnitJsonObjectPatcher capture_stream_number no mode skip remove " + modeName);
                return false;
            }

            Object old = captureStreamNumber.opt(modeName);

            captureStreamNumber.remove(modeName);

            log(host, "CameraUnitJsonObjectPatcher remove capture_stream_number " + modeName + " old=" + String.valueOf(old));

            return true;
        } catch (Throwable t) {
            log(host, "CameraUnitJsonObjectPatcher removeCaptureStreamNumber failed mode=" + modeName + " " + t);
            return false;
        }
    }

    private static boolean patchRearMainCustomInfo(JSONObject root, Object host) {
        try {
            return patchRearMainCustomInfoRecursive(root, host, "root");
        } catch (Throwable t) {
            log(host, "CameraUnitJsonObjectPatcher patchRearMainCustomInfo failed " + t);
            return false;
        }
    }

    private static boolean patchRearMainCustomInfoRecursive(
            Object value,
            Object host,
            String path
    ) {
        boolean changed = false;

        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;

            changed |= patchOneRearMainCustomInfoObject(object, host, path);

            JSONArray names = object.names();

            if (names == null) {
                return changed;
            }

            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i, "");

                if (key.length() == 0) {
                    continue;
                }

                Object child = object.opt(key);

                if (child instanceof JSONObject || child instanceof JSONArray) {
                    changed |= patchRearMainCustomInfoRecursive(child, host, path + "." + key);
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;

            for (int i = 0; i < array.length(); i++) {
                Object child = array.opt(i);

                if (child instanceof JSONObject || child instanceof JSONArray) {
                    changed |= patchRearMainCustomInfoRecursive(child, host, path + "[" + i + "]");
                }
            }
        }

        return changed;
    }

    private static boolean patchOneRearMainCustomInfoObject(
            JSONObject object,
            Object host,
            String path
    ) {
        if (!looksLikeCameraCustomInfoObject(object)) {
            return false;
        }

        String oldRearMain = object.optString("rear_main", "");

        if (looksLikeCustomInfo(oldRearMain)) {
            return false;
        }

        String source = findCustomInfoSource(object);

        if (isEmpty(source)) {
            source = DEFAULT_REAR_MAIN_CUSTOM_INFO;
        }

        if (!isEmpty(oldRearMain) && !looksLikeCustomInfo(oldRearMain)) {
            log(
                    host,
                    "CameraUnitJsonObjectPatcher rear_main customInfo skip non-empty invalid path="
                            + path
                            + " old="
                            + oldRearMain
            );

            return false;
        }

        try {
            object.put("rear_main", source);
        } catch (Throwable t) {
            log(
                    host,
                    "CameraUnitJsonObjectPatcher rear_main customInfo put failed path="
                            + path
                            + " error="
                            + t
            );

            return false;
        }

        log(
                host,
                "CameraUnitJsonObjectPatcher rear_main customInfo patch path="
                        + path
                        + " old="
                        + oldRearMain
                        + " new="
                        + source
        );

        return true;
    }

    private static boolean looksLikeCameraCustomInfoObject(JSONObject object) {
        if (object == null) {
            return false;
        }

        boolean hasCameraKey = false;

        String[] cameraKeys = new String[]{
                "rear_main",
                "rear_sat",
                "rear_wide",
                "rear_tele"
        };

        for (String key : cameraKeys) {
            if (!object.has(key)) {
                continue;
            }

            hasCameraKey = true;

            Object value = object.opt(key);

            if (value == null || value == JSONObject.NULL) {
                continue;
            }

            if (!(value instanceof String)) {
                return false;
            }
        }

        return hasCameraKey;
    }

    private static String findCustomInfoSource(JSONObject object) {
        String[] cameraKeys = new String[]{
                "rear_main",
                "rear_sat",
                "rear_wide",
                "rear_tele"
        };

        for (String key : cameraKeys) {
            String value = object.optString(key, "");

            if (looksLikeCustomInfo(value)) {
                return value;
            }
        }

        JSONArray names = object.names();

        if (names == null) {
            return null;
        }

        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i, "");

            if (key.length() == 0) {
                continue;
            }

            Object value = object.opt(key);

            if (value instanceof String && looksLikeCustomInfo((String) value)) {
                return (String) value;
            }
        }

        return null;
    }

    private static boolean looksLikeCustomInfo(String value) {
        if (value == null) {
            return false;
        }

        String text = value.trim();

        return text.startsWith("8010,")
                && text.contains("3_35_3")
                && text.contains("2_35_3");
    }

    private static boolean hasRearMainCustomInfo(JSONObject root) {
        try {
            return hasRearMainCustomInfoRecursive(root);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasRearMainCustomInfoRecursive(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;

            if (looksLikeCustomInfo(object.optString("rear_main", ""))) {
                return true;
            }

            JSONArray names = object.names();

            if (names == null) {
                return false;
            }

            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i, "");

                if (key.length() == 0) {
                    continue;
                }

                Object child = object.opt(key);

                if (child instanceof JSONObject || child instanceof JSONArray) {
                    if (hasRearMainCustomInfoRecursive(child)) {
                        return true;
                    }
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;

            for (int i = 0; i < array.length(); i++) {
                Object child = array.opt(i);

                if (child instanceof JSONObject || child instanceof JSONArray) {
                    if (hasRearMainCustomInfoRecursive(child)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static JSONArray createGrModeCase() {
        JSONArray array = new JSONArray();

        putPair(array, "preview", "rear_sat");
        putPair(array, "capture_meta", "rear_sat");
        putPair(array, "raw16_output", "rear_main");
        putPair(array, "raw16_output", "rear_wide");
        putPair(array, "raw16_output", "rear_tele");
        putPair(array, "raw_output", "rear_main");
        putPair(array, "raw_output", "rear_wide");
        putPair(array, "raw_output", "rear_tele");
        putPair(array, "capture_yuv", "rear_main");
        putPair(array, "capture_yuv", "rear_wide");
        putPair(array, "capture_yuv", "rear_tele");
        putPair(array, "preview_in_preview", "rear_sat");
        putPair(array, "capture_raw10_dol", "rear_main");
        putPair(array, "capture_raw10_dol", "rear_tele");

        return array;
    }

    private static JSONArray createGrModeHqRawCase() {
        JSONArray array = new JSONArray();

        putPair(array, "preview", "var_camera");
        putPair(array, "capture_raw", "var_camera");
        putPair(array, "raw_output", "var_camera");
        putPair(array, "capture_yuv", "var_camera");
        putPair(array, "capture_raw_mfnr", "var_camera");

        return array;
    }

    private static JSONArray createVibeRearMainOnlyCase() {
        JSONArray array = new JSONArray();

        putPair(array, "preview", "rear_main");
        putPair(array, "capture_yuv", "rear_main");

        return array;
    }

    private static void putPair(JSONArray array, String key, String value) {
        try {
            JSONObject object = new JSONObject();
            object.put(key, value);
            array.put(object);
        } catch (Throwable ignored) {
        }
    }

    private static boolean arrayHasValue(JSONArray array, String value) {
        if (array == null || value == null) {
            return false;
        }

        for (int i = 0; i < array.length(); i++) {
            try {
                String item = array.optString(i, "");

                if (value.equals(item)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static boolean hasModeInCameraType(JSONObject root, String cameraType, String modeName) {
        try {
            JSONObject modeTypeList = root.optJSONObject("mode_type_list");

            if (modeTypeList == null) {
                return false;
            }

            JSONArray array = modeTypeList.optJSONArray(cameraType);

            return arrayHasValue(array, modeName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasCaptureStream(JSONObject root, String modeName, String cameraType) {
        try {
            JSONObject captureStreamNumber = root.optJSONObject("capture_stream_number");

            if (captureStreamNumber == null) {
                return false;
            }

            JSONObject modeObject = captureStreamNumber.optJSONObject(modeName);

            if (modeObject == null) {
                return false;
            }

            JSONObject cameraObject = modeObject.optJSONObject(cameraType);

            if (cameraObject == null) {
                return false;
            }

            return "2".equals(cameraObject.optString("2dol", ""));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasCaptureStreamMode(JSONObject root, String modeName) {
        try {
            JSONObject captureStreamNumber = root.optJSONObject("capture_stream_number");

            return captureStreamNumber != null && captureStreamNumber.has(modeName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasUsecase(JSONObject root, String usecaseName) {
        try {
            JSONObject usecaseInfo = root.optJSONObject("usecase_info");

            return usecaseInfo != null && usecaseInfo.has(usecaseName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasModeOperation(JSONObject root, String modeName, String operationMode) {
        try {
            JSONObject modeOperation = root.optJSONObject("mode_operation_mode");

            if (modeOperation == null) {
                return false;
            }

            return operationMode.equalsIgnoreCase(modeOperation.optString(modeName, ""));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void verify(JSONObject root, Object host) {
        boolean grOperation = hasModeOperation(root, MODE_GR, OPERATION_GR);
        boolean grUsecase = hasUsecase(root, "gr_mode_case");
        boolean grCapture = hasCaptureStream(root, MODE_GR, "rear_main")
                && hasCaptureStream(root, MODE_GR, "rear_wide")
                && hasCaptureStream(root, MODE_GR, "rear_tele")
                && hasCaptureStream(root, MODE_GR, "rear_sat");

        boolean grRearWide = hasModeInCameraType(root, "rear_wide", MODE_GR);
        boolean grRearMain = hasModeInCameraType(root, "rear_main", MODE_GR);
        boolean grRearTele = hasModeInCameraType(root, "rear_tele", MODE_GR);
        boolean grRearSat = hasModeInCameraType(root, "rear_sat", MODE_GR);

        boolean vibeOperation = hasModeOperation(root, MODE_VIBE, OPERATION_VIBE_SAFE);
        boolean vibeUsecase = hasUsecase(root, "vibe_mode_case");
        boolean vibeHasCaptureStream = hasCaptureStreamMode(root, MODE_VIBE);

        boolean vibeRearWide = hasModeInCameraType(root, "rear_wide", MODE_VIBE);
        boolean vibeRearMain = hasModeInCameraType(root, "rear_main", MODE_VIBE);
        boolean vibeRearTele = hasModeInCameraType(root, "rear_tele", MODE_VIBE);
        boolean vibeRearSat = hasModeInCameraType(root, "rear_sat", MODE_VIBE);

        boolean rearMainCustomInfo = hasRearMainCustomInfo(root);

        log(
                host,
                "CameraUnitJsonObjectPatcher verify GR"
                        + " modeOperation=" + grOperation
                        + " usecase=" + grUsecase
                        + " captureStream=" + grCapture
                        + " rearWide=" + grRearWide
                        + " rearMain=" + grRearMain
                        + " rearTele=" + grRearTele
                        + " rearSat=" + grRearSat
        );

        log(
                host,
                "CameraUnitJsonObjectPatcher verify VIBE_REAR_MAIN_ONLY_V3"
                        + " operation8001=" + vibeOperation
                        + " usecase=" + vibeUsecase
                        + " hasCaptureStream=" + vibeHasCaptureStream
                        + " rearWide=" + vibeRearWide
                        + " rearMain=" + vibeRearMain
                        + " rearTele=" + vibeRearTele
                        + " rearSat=" + vibeRearSat
                        + " rearMainCustomInfo=" + rearMainCustomInfo
        );
    }

    private static void log(Object host, String msg) {
        try {
            Log.e(TAG, msg);
        } catch (Throwable ignored) {
        }

        if (host == null) {
            return;
        }

        tryInvokeHostLog(host, "xlog", new Class[]{int.class, String.class}, new Object[]{Log.ERROR, msg});
        tryInvokeHostLog(host, "log", new Class[]{String.class}, new Object[]{msg});
    }

    private static void tryInvokeHostLog(
            Object host,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] args
    ) {
        try {
            Method method = host.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(host, args);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method method = host.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(host, args);
        } catch (Throwable ignored) {
        }
    }
}
package com.camera.CameraUnit;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;

public final class CameraUnitConfigPatcher {

    private static final String TAG = "CameraUnitXp";

    private CameraUnitConfigPatcher() {
    }

    public static String patch(String originalText, Object host) {
        if (originalText == null) {
            xlog(host, "CameraUnitConfigPatcher patch originalText null");
            return null;
        }

        String text = originalText.trim();

        if (text.length() == 0) {
            xlog(host, "CameraUnitConfigPatcher patch originalText empty");
            return originalText;
        }

        try {
            if (text.startsWith("{")) {
                JSONObject object = new JSONObject(text);

                boolean looksLike = CameraUnitJsonObjectPatcher.looksLikeCameraUnitConfig(object);

                xlog(host, "CameraUnitConfigPatcher JSONObject looksLike=" + looksLike);

                if (!looksLike) {
                    return originalText;
                }

                boolean changed = CameraUnitJsonObjectPatcher.patchJSONObject(object, host);

                xlog(host, "CameraUnitConfigPatcher JSONObject changed=" + changed);

                if (!changed) {
                    return originalText;
                }

                return object.toString();
            }

            if (text.startsWith("[")) {
                JSONArray array = new JSONArray(text);

                boolean changed = patchJSONArray(array, host);

                xlog(host, "CameraUnitConfigPatcher JSONArray changed=" + changed);

                if (!changed) {
                    return originalText;
                }

                return array.toString();
            }

            xlog(host, "CameraUnitConfigPatcher unsupported json text head=" + safeHead(text));

            return originalText;
        } catch (Throwable t) {
            xlog(host, "CameraUnitConfigPatcher patch failed " + t);
            return originalText;
        }
    }

    private static boolean patchJSONArray(JSONArray array, Object host) {
        if (array == null) {
            return false;
        }

        boolean changed = false;

        for (int i = 0; i < array.length(); i++) {
            try {
                Object value = array.opt(i);

                if (value instanceof JSONObject) {
                    JSONObject object = (JSONObject) value;

                    if (CameraUnitJsonObjectPatcher.looksLikeCameraUnitConfig(object)) {
                        changed |= CameraUnitJsonObjectPatcher.patchJSONObject(object, host);
                    } else {
                        changed |= patchJSONObjectChildren(object, host);
                    }
                } else if (value instanceof JSONArray) {
                    changed |= patchJSONArray((JSONArray) value, host);
                }
            } catch (Throwable t) {
                xlog(host, "CameraUnitConfigPatcher patchJSONArray index=" + i + " failed " + t);
            }
        }

        return changed;
    }

    private static boolean patchJSONObjectChildren(JSONObject object, Object host) {
        if (object == null) {
            return false;
        }

        boolean changed = false;

        JSONArray names = object.names();

        if (names == null) {
            return false;
        }

        for (int i = 0; i < names.length(); i++) {
            try {
                String key = names.optString(i, "");

                if (key.length() == 0) {
                    continue;
                }

                Object value = object.opt(key);

                if (value instanceof JSONObject) {
                    JSONObject child = (JSONObject) value;

                    if (CameraUnitJsonObjectPatcher.looksLikeCameraUnitConfig(child)) {
                        changed |= CameraUnitJsonObjectPatcher.patchJSONObject(child, host);
                    } else {
                        changed |= patchJSONObjectChildren(child, host);
                    }
                } else if (value instanceof JSONArray) {
                    changed |= patchJSONArray((JSONArray) value, host);
                }
            } catch (Throwable t) {
                xlog(host, "CameraUnitConfigPatcher patchJSONObjectChildren failed " + t);
            }
        }

        return changed;
    }

    private static String safeHead(String text) {
        if (text == null) {
            return "null";
        }

        String compact = text.replace("\r", "\\r").replace("\n", "\\n");

        if (compact.length() <= 80) {
            return compact;
        }

        return compact.substring(0, 80);
    }

    private static void xlog(Object host, String msg) {
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
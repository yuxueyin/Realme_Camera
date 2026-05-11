package com.camera.VendorTag;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

public final class OplusCameraConfigHook {

    private static final String UPDATE_HELPER_CLASS =
            "com.oplus.ocs.camera.consumer.apsAdapter.update.UpdateHelper";

    private static final String TARGET_METHOD_NAME =
            "getValidConfigData";

    private final VendorTagModule host;

    private boolean installed = false;

    public OplusCameraConfigHook(VendorTagModule host) {
        this.host = host;
    }

    public void install() {
        if (installed) {
            host.xlog(Log.ERROR, "OplusCameraConfigHook already installed, skip");
            return;
        }

        installed = true;

        hookGetValidConfigData();
    }

    private void hookGetValidConfigData() {
        try {
            Class<?> updateHelperClass = host.loadCameraClass(UPDATE_HELPER_CLASS);

            Method[] methods = updateHelperClass.getDeclaredMethods();

            boolean hookedAny = false;

            for (Method method : methods) {
                if (!TARGET_METHOD_NAME.equals(method.getName())) {
                    continue;
                }

                method.setAccessible(true);

                host.hookMethod(method).intercept(chain -> {
                    List<Object> args = chain.getArgs();

                    String configName = findConfigName(args);

                    Object originalResult = chain.proceed();

                    if (!(originalResult instanceof String)) {
                        return originalResult;
                    }

                    if (!isOplusCameraConfig(configName)) {
                        return originalResult;
                    }

                    /*
                     * 只要 Hook 真正命中 oplus_camera_config，
                     * 就再次通知 UI：LSP 模块已经在相机进程激活。
                     */
                    host.notifyLspActiveAsync();

                    String originalJson = (String) originalResult;

                    try {
                        host.xlog(Log.ERROR, "VendorTagHook hit oplus_camera_config configName=" + configName);

                        String modifyJson = VendorTagJsonEditor.modifyOplusCameraConfig(originalJson, host);

                        if (modifyJson == null || modifyJson.length() == 0) {
                            host.xlog(Log.ERROR, "VendorTagHook modifyJson empty, return original");
                            return originalResult;
                        }

                        host.xlog(Log.ERROR, "VendorTagHook return modify config success");

                        return modifyJson;
                    } catch (Throwable t) {
                        host.xlog(Log.ERROR, "VendorTagHook modify config failed", t);
                        return originalResult;
                    }
                });

                hookedAny = true;

                host.xlog(Log.ERROR, "hook success: UpdateHelper#getValidConfigData -> " + method);
            }

            if (!hookedAny) {
                host.xlog(Log.ERROR, "hook failed: no getValidConfigData found");
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "hook UpdateHelper#getValidConfigData failed", t);
        }
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

            if (isOplusCameraConfig(value)) {
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

    private boolean isOplusCameraConfig(String configName) {
        if (configName == null) {
            return false;
        }

        return configName.toLowerCase().contains("oplus_camera_config");
    }
}
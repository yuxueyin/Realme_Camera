package com.camera.CameraUnit;

import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * camera_unit_config XP 入口。
 *
 * 注意：
 * 不是替换 /odm/etc/camera/config/camera_unit_config 文件。
 * 也不是直接返回完整补全文件。
 *
 * V15 标记：AI_SCENERY_V15_EXACT_NO_HDR_KEEP_HIGH_PIXEL
 */
public class CameraUnitConfigModule extends XposedModule {

    public static final String TAG = "CameraUnitXp";

    private static final String TARGET_PACKAGE = "com.oplus.camera";

    private static final String VERSION_MARK = "AI_SCENERY_V15_EXACT_NO_HDR_KEEP_HIGH_PIXEL";

    private ClassLoader appClassLoader;

    private boolean installed = false;

    private CameraUnitConfigXp cameraUnitConfigXp;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        xlog(Log.ERROR, "CameraUnitConfigModule onModuleLoaded " + VERSION_MARK);
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();

        xlog(Log.ERROR, "CameraUnitConfigModule onPackageLoaded pkg=" + packageName + " " + VERSION_MARK);

        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        appClassLoader = param.getDefaultClassLoader();

        if (installed) {
            xlog(Log.ERROR, "CameraUnitConfigModule already installed, skip " + VERSION_MARK);
            return;
        }

        installed = true;

        cameraUnitConfigXp = new CameraUnitConfigXp(this);
        cameraUnitConfigXp.install();

        xlog(Log.ERROR, "CameraUnitConfigModule installed CameraUnitConfigXp " + VERSION_MARK);
    }

    public Class<?> loadCameraClass(String className) throws ClassNotFoundException {
        if (appClassLoader != null) {
            return Class.forName(className, false, appClassLoader);
        }

        return Class.forName(className);
    }

    public XposedInterface.HookBuilder hookMethod(Method method) {
        return hook(method);
    }

    public void xlog(int priority, String msg) {
        try {
            android.util.Log.println(priority, TAG, msg);
        } catch (Throwable ignored) {
        }

        try {
            log(priority, TAG, msg);
        } catch (Throwable ignored) {
        }
    }

    public void xlog(int priority, String msg, Throwable tr) {
        try {
            android.util.Log.println(priority, TAG, msg + " " + String.valueOf(tr));
        } catch (Throwable ignored) {
        }

        try {
            log(priority, TAG, msg, tr);
        } catch (Throwable ignored) {
        }
    }
}

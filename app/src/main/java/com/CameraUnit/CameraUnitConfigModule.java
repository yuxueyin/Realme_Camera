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
 * 作用：
 * 1. 在相机读取 camera_unit_config 后，拿到原始配置字符串。
 * 2. 只把 GR 缺失节点动态添加进去。
 * 3. 返回合并后的配置给相机运行时使用。
 */
public class CameraUnitConfigModule extends XposedModule {

    public static final String TAG = "CameraUnitXp";

    private static final String TARGET_PACKAGE = "com.oplus.camera";

    private ClassLoader appClassLoader;

    private boolean installed = false;

    private CameraUnitConfigXp cameraUnitConfigXp;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        xlog(Log.ERROR, "CameraUnitConfigModule onModuleLoaded");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();

        xlog(Log.ERROR, "CameraUnitConfigModule onPackageLoaded pkg=" + packageName);

        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        appClassLoader = param.getDefaultClassLoader();

        if (installed) {
            xlog(Log.ERROR, "CameraUnitConfigModule already installed, skip");
            return;
        }

        installed = true;

        cameraUnitConfigXp = new CameraUnitConfigXp(this);
        cameraUnitConfigXp.install();

        xlog(Log.ERROR, "CameraUnitConfigModule installed CameraUnitConfigXp");
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
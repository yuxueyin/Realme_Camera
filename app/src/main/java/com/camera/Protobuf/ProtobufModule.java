package com.camera.Protobuf;

import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Protobuf 独立入口。
 *
 * 作用：
 * 1. 只在 com.oplus.camera 进程加载。
 * 2. 初始化 ProtobufFeature。
 * 3. 给 ProtobufFeature / config 提供 ClassLoader、hookMethod、日志方法。
 */
public class ProtobufModule extends XposedModule {

    public static final String TAG = "ProtobufFeature";

    public static final String TARGET_PACKAGE = "com.oplus.camera";

    private boolean hooked = false;

    private ClassLoader appClassLoader;

    private ProtobufFeature protobufFeature;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        xlog(Log.ERROR, "ProtobufModule onModuleLoaded");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();

        xlog(Log.ERROR, "ProtobufModule onPackageLoaded pkg=" + packageName);

        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        appClassLoader = param.getDefaultClassLoader();

        if (hooked) {
            xlog(Log.ERROR, "ProtobufModule already hooked, skip");
            return;
        }

        hooked = true;

        protobufFeature = new ProtobufFeature(this);
        protobufFeature.install();

        xlog(Log.ERROR, "ProtobufModule installed ProtobufFeature");
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
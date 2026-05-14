package com.camera.gr;

import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/*
 * GR 独立入口。
 *
 * 这里只加载 GrModeFix。
 * 不加载 LumoFix。
 */
public class GrModule extends XposedModule {

    public static final String TAG = "GrModeFix";
    public static final String TARGET_PACKAGE = "com.oplus.camera";

    private boolean hooked = false;
    private ClassLoader appClassLoader;
    private GrModeFix grModeFix;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        xlog(Log.ERROR, "GrModule onModuleLoaded");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();

        xlog(Log.ERROR, "GrModule onPackageLoaded pkg=" + packageName);

        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        appClassLoader = param.getDefaultClassLoader();

        if (hooked) {
            xlog(Log.ERROR, "GrModule already hooked, skip");
            return;
        }

        hooked = true;

        grModeFix = new GrModeFix(this);
        grModeFix.install();

        xlog(Log.ERROR, "GrModule installed only GrModeFix");
    }

    public Class loadCameraClass(String className) throws ClassNotFoundException {
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
}
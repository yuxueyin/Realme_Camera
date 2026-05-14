package com.min.lite;

import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/*
 * Lumo 独立入口。
 *
 * 这里只加载 LumoFix。
 * 不加载 GR。
 */
public class ModuleMain extends XposedModule {

    public static final String TAG = "LumoBlockFinal";
    public static final String TARGET_PACKAGE = "com.oplus.camera";

    private boolean hooked = false;
    private ClassLoader appClassLoader;
    private LumoFix lumoFix;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        xlog(Log.ERROR, "Lumo ModuleMain onModuleLoaded");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();

        xlog(Log.ERROR, "Lumo ModuleMain onPackageLoaded pkg=" + packageName);

        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        appClassLoader = param.getDefaultClassLoader();

        if (hooked) {
            xlog(Log.ERROR, "Lumo ModuleMain already hooked, skip");
            return;
        }

        hooked = true;

        lumoFix = new LumoFix(this);
        lumoFix.install();

        xlog(Log.ERROR, "Lumo ModuleMain installed only LumoFix");
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
package com.camera.VendorTag;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.camera.VendorTag.ui.LspStatusMarker;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public class VendorTagModule extends XposedModule {

    public static final String TAG = "VendorTagHook";

    private static final String TARGET_PACKAGE = "com.oplus.camera";

    private static final String STATUS_URI_STRING =
            "content://com.min.lite.vendortag/status";

    private ClassLoader appClassLoader;

    private boolean hooked = false;

    private OplusCameraConfigHook configHook;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        xlog(Log.ERROR, "VendorTagModule onModuleLoaded");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();

        xlog(Log.ERROR, "VendorTagModule onPackageLoaded pkg=" + packageName);

        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        appClassLoader = param.getDefaultClassLoader();

        notifyLspActiveAsync();

        if (hooked) {
            xlog(Log.ERROR, "VendorTagModule already hooked, skip");
            return;
        }

        hooked = true;

        configHook = new OplusCameraConfigHook(this);
        configHook.install();

        xlog(Log.ERROR, "VendorTagModule installed OplusCameraConfigHook");
    }

    public void notifyLspActiveAsync() {
        new Thread(() -> {
            for (int i = 0; i < 12; i++) {
                boolean ok = notifyLspActiveOnce();

                if (ok) {
                    xlog(Log.ERROR, "VendorTagModule notifyLspActive success retry=" + i);
                    return;
                }

                try {
                    Thread.sleep(800L);
                } catch (Throwable ignored) {
                }
            }

            xlog(Log.ERROR, "VendorTagModule notifyLspActive failed after retry");
        }, "VendorTag-LspActive").start();
    }

    private boolean notifyLspActiveOnce() {
        boolean markerOk = false;
        boolean providerOk = false;

        try {
            markerOk = LspStatusMarker.markBySu();
            xlog(Log.ERROR, "VendorTagModule notifyLspActive markerOk=" + markerOk);
        } catch (Throwable t) {
            xlog(Log.ERROR, "VendorTagModule notifyLspActive marker failed", t);
        }

        try {
            Context context = getCurrentApplicationContext();

            if (context == null) {
                xlog(Log.ERROR, "VendorTagModule notifyLspActive context null");
                return markerOk;
            }

            Uri uri = Uri.parse(STATUS_URI_STRING);

            ContentValues values = new ContentValues();
            values.put("active", "1");
            values.put("time", String.valueOf(System.currentTimeMillis()));

            Uri result = context.getContentResolver().insert(uri, values);

            providerOk = result != null;

            xlog(Log.ERROR, "VendorTagModule notifyLspActive provider result=" + result);
        } catch (Throwable t) {
            xlog(Log.ERROR, "VendorTagModule notifyLspActive provider failed", t);
        }

        return markerOk || providerOk;
    }

    private Context getCurrentApplicationContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");

            Method currentApplicationMethod =
                    activityThreadClass.getDeclaredMethod("currentApplication");

            currentApplicationMethod.setAccessible(true);

            Object application = currentApplicationMethod.invoke(null);

            if (application instanceof Context) {
                return (Context) application;
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> appGlobalsClass = Class.forName("android.app.AppGlobals");

            Method getInitialApplicationMethod =
                    appGlobalsClass.getDeclaredMethod("getInitialApplication");

            getInitialApplicationMethod.setAccessible(true);

            Object application = getInitialApplicationMethod.invoke(null);

            if (application instanceof Context) {
                return (Context) application;
            }
        } catch (Throwable ignored) {
        }

        return null;
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
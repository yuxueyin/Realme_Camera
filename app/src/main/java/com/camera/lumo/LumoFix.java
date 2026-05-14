package com.min.lite;

import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/*
 * 这里只保留 Lumo 原修复：
 * com.oplus.picture.exif.flag
 *
 * 不放任何 GR 逻辑。
 */
public class LumoFix {

    private static final String PICTURE_EXIF_VENDOR_TAG =
            "com.oplus.picture.exif.flag";

    private final ModuleMain host;

    public LumoFix(ModuleMain host) {
        this.host = host;
    }

    public void install() {
        hookJ8V0F();
        hookCameraDeviceSetParameter();

        host.xlog(Log.ERROR, "LumoFix installed only picture.exif.flag");
    }

    private void hookJ8V0F() {
        try {
            Class clazz = host.loadCameraClass("j8.v0");

            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                if (!"f".equals(method.getName())) {
                    continue;
                }

                if (method.getParameterTypes().length < 2) {
                    continue;
                }

                method.setAccessible(true);

                host.hookMethod(method).intercept(chain -> {
                    List argsList = chain.getArgs();

                    if (argsList == null || argsList.isEmpty()) {
                        return chain.proceed();
                    }

                    if (!hasPictureExifKey(argsList)) {
                        return chain.proceed();
                    }

                    Object[] newArgs = argsList.toArray(new Object[0]);

                    boolean changed = fixPictureExifArgs(newArgs, "j8.v0.f");

                    if (changed) {
                        return chain.proceed(newArgs);
                    }

                    return chain.proceed();
                });

                host.xlog(Log.ERROR, "hook success: LumoFix j8.v0.f -> " + method);
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "hook LumoFix j8.v0.f failed: " + String.valueOf(t));
        }
    }

    private void hookCameraDeviceSetParameter() {
        try {
            Class clazz = host.loadCameraClass("com.oplus.ocs.camera.CameraDevice");

            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                if (!"setParameter".equals(method.getName())) {
                    continue;
                }

                if (method.getParameterTypes().length < 2) {
                    continue;
                }

                method.setAccessible(true);

                host.hookMethod(method).intercept(chain -> {
                    List argsList = chain.getArgs();

                    if (argsList == null || argsList.isEmpty()) {
                        return chain.proceed();
                    }

                    if (!hasPictureExifKey(argsList)) {
                        return chain.proceed();
                    }

                    Object[] newArgs = argsList.toArray(new Object[0]);

                    boolean changed = fixPictureExifArgs(
                            newArgs,
                            "com.oplus.ocs.camera.CameraDevice.setParameter"
                    );

                    if (changed) {
                        return chain.proceed(newArgs);
                    }

                    return chain.proceed();
                });

                host.xlog(
                        Log.ERROR,
                        "hook success: LumoFix CameraDevice.setParameter -> " + method
                );
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "hook LumoFix CameraDevice.setParameter failed: " + String.valueOf(t));
        }
    }

    private boolean hasPictureExifKey(List args) {
        if (args == null || args.isEmpty()) {
            return false;
        }

        for (Object arg : args) {
            if (isPictureExifKey(arg)) {
                return true;
            }

            if (arg instanceof Object[]) {
                Object[] array = (Object[]) arg;

                for (Object item : array) {
                    if (isPictureExifKey(item)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean fixPictureExifArgs(Object[] args, String where) {
        if (args == null || args.length < 2) {
            return false;
        }

        boolean changed = false;

        for (int i = 0; i < args.length - 1; i++) {
            Object key = args[i];

            if (!isPictureExifKey(key)) {
                continue;
            }

            Object oldValue = args[i + 1];
            Object newValue = fixPictureExifValue(oldValue);

            args[i + 1] = newValue;
            changed = true;

            host.xlog(
                    Log.ERROR,
                    "LumoFix fix "
                            + where
                            + " key="
                            + getKeyName(key)
                            + " old="
                            + describeValue(oldValue)
                            + " new="
                            + describeValue(newValue)
            );
        }

        return changed;
    }

    private Object fixPictureExifValue(Object value) {
        if (value == null) {
            return new long[]{0L};
        }

        if (value instanceof long[]) {
            return new long[]{0L};
        }

        if (value instanceof Long) {
            return 0L;
        }

        if (value instanceof int[]) {
            return new int[]{0};
        }

        if (value instanceof Integer) {
            return 0;
        }

        if (value instanceof String) {
            return "0";
        }

        Class valueClass = value.getClass();

        if (valueClass.isArray()) {
            try {
                Class componentType = valueClass.getComponentType();

                if (componentType == long.class || componentType == Long.class) {
                    return new long[]{0L};
                }

                if (componentType == int.class || componentType == Integer.class) {
                    return new int[]{0};
                }

                return Array.newInstance(componentType, 1);
            } catch (Throwable t) {
                host.xlog(Log.ERROR, "LumoFix fixPictureExifValue array failed: " + String.valueOf(t));
            }
        }

        return new long[]{0L};
    }

    private boolean isPictureExifKey(Object key) {
        if (key == null) {
            return false;
        }

        String name = getKeyName(key);

        if (name == null) {
            return false;
        }

        return PICTURE_EXIF_VENDOR_TAG.equals(name)
                || name.contains(PICTURE_EXIF_VENDOR_TAG)
                || name.contains("picture.exif.flag")
                || name.contains("PICTURE_EXIF_FLAG")
                || name.contains("picture_exif_flag");
    }

    private String getKeyName(Object key) {
        if (key == null) {
            return null;
        }

        if (key instanceof CharSequence) {
            return key.toString();
        }

        String[] methodNames = new String[]{
                "getName",
                "getKeyName",
                "getKey",
                "getVendorTag",
                "getVendorTagName",
                "name"
        };

        for (String methodName : methodNames) {
            try {
                Method method = key.getClass().getMethod(methodName);
                method.setAccessible(true);

                Object result = method.invoke(key);

                if (result != null) {
                    return String.valueOf(result);
                }
            } catch (Throwable ignored) {
            }
        }

        String[] fieldNames = new String[]{
                "mName",
                "mKey",
                "mKeyName",
                "name",
                "key",
                "mVendorTag",
                "vendorTag",
                "mVendorTagName"
        };

        for (String fieldName : fieldNames) {
            try {
                Field field = key.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);

                Object result = field.get(key);

                if (result != null) {
                    return String.valueOf(result);
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            return String.valueOf(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String describeValue(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof long[]) {
            return Arrays.toString((long[]) value);
        }

        if (value instanceof int[]) {
            return Arrays.toString((int[]) value);
        }

        if (value instanceof byte[]) {
            return Arrays.toString((byte[]) value);
        }

        if (value instanceof boolean[]) {
            return Arrays.toString((boolean[]) value);
        }

        if (value instanceof Object[]) {
            return Arrays.toString((Object[]) value);
        }

        return String.valueOf(value);
    }
}
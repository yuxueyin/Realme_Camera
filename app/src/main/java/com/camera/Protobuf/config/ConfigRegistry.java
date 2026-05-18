package com.camera.Protobuf.config;

import android.util.Log;

import com.camera.Protobuf.ProtobufModule;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class ConfigRegistry {

    private static final String TAG = "ProtobufFeature";

    private static final String V15_MARK = "AI_SCENERY_PROTO_V15_REGISTRY";

    private static final String[] CONFIG_CLASS_NAMES = new String[]{
            "com.camera.Protobuf.config.GrModeConfig",
            "com.camera.Protobuf.config.VibeModeConfig",
            "com.camera.Protobuf.config.AiSceneryModeConfig"
    };

    private ConfigRegistry() {
    }

    /**
     * ProtobufFeature.java 当前调用的是这个方法：
     *
     * List<ProtobufConfig> configs = ConfigRegistry.loadAll(host);
     *
     * 所以这个方法必须保留。
     */
    public static List<ProtobufConfig> loadAll(ProtobufModule host) {
        Log.e(TAG, "ConfigRegistry loadAll " + V15_MARK + " classCount=" + CONFIG_CLASS_NAMES.length);

        List<ProtobufConfig> result = new ArrayList<>();

        for (String className : CONFIG_CLASS_NAMES) {
            ProtobufConfig config = loadOne(host, className);

            if (config != null) {
                result.add(config);
                Log.e(TAG, "ConfigRegistry found config: " + className + " " + V15_MARK);
            } else {
                Log.e(TAG, "ConfigRegistry skip config: " + className + " " + V15_MARK);
            }
        }

        return result;
    }

    /**
     * 兼容其他旧调用。
     */
    public static List<ProtobufConfig> getConfigs(ProtobufModule host) {
        return loadAll(host);
    }

    /**
     * 兼容其他旧调用。
     */
    public static List<ProtobufConfig> getAllConfigs(ProtobufModule host) {
        return loadAll(host);
    }

    /**
     * 兼容其他旧调用。
     */
    public static List<ProtobufConfig> listConfigs(ProtobufModule host) {
        return loadAll(host);
    }

    private static ProtobufConfig loadOne(ProtobufModule host, String className) {
        try {
            Class<?> cls = Class.forName(className);
            Object instance = createInstance(host, cls);

            if (instance == null) {
                Log.e(TAG, "ConfigRegistry create instance null: " + className);
                return null;
            }

            if (instance instanceof ProtobufConfig) {
                return (ProtobufConfig) instance;
            }

            /*
             * 兼容 VibeModeConfig 这种没有直接 implements ProtobufConfig 的情况。
             * 通过动态代理转成 ProtobufConfig。
             */
            return createProxy(instance);
        } catch (Throwable t) {
            Log.e(TAG, "ConfigRegistry load config failed: " + className + " " + t);
            return null;
        }
    }

    private static Object createInstance(ProtobufModule host, Class<?> cls) {
        /*
         * 优先尝试构造：
         * new XxxConfig(ProtobufModule host)
         */
        try {
            Constructor<?> constructor = cls.getDeclaredConstructor(ProtobufModule.class);
            constructor.setAccessible(true);
            return constructor.newInstance(host);
        } catch (Throwable ignored) {
        }

        /*
         * 再尝试无参构造：
         * new XxxConfig()
         */
        try {
            Constructor<?> constructor = cls.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable t) {
            Log.e(TAG, "ConfigRegistry create instance failed: " + cls.getName() + " " + t);
            return null;
        }
    }

    private static ProtobufConfig createProxy(final Object target) {
        try {
            Object proxy = Proxy.newProxyInstance(
                    ProtobufConfig.class.getClassLoader(),
                    new Class[]{ProtobufConfig.class},
                    new ConfigInvocationHandler(target)
            );

            return (ProtobufConfig) proxy;
        } catch (Throwable t) {
            Log.e(TAG, "ConfigRegistry create proxy failed: " + target.getClass().getName() + " " + t);
            return null;
        }
    }

    private static final class ConfigInvocationHandler implements InvocationHandler {

        private final Object target;

        ConfigInvocationHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            if ("toString".equals(methodName)) {
                return target.getClass().getSimpleName();
            }

            if ("hashCode".equals(methodName)) {
                return target.hashCode();
            }

            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }

            /*
             * 如果 ProtobufConfig 有 getName() / name()
             */
            if ("getName".equals(methodName) || "name".equals(methodName)) {
                Object name = invokeTarget(
                        target,
                        new String[]{"getName", "name"},
                        args
                );

                if (name != null) {
                    return name;
                }

                return target.getClass().getSimpleName();
            }

            /*
             * 如果 ProtobufFeature 调用 apply(...)，
             * 这里会转发到 VibeModeConfig 的：
             * apply(...)
             * applyConfig(...)
             * run(...)
             */
            if ("apply".equals(methodName)
                    || "applyConfig".equals(methodName)
                    || "run".equals(methodName)) {

                Object value = invokeTarget(
                        target,
                        new String[]{"apply", "applyConfig", "run"},
                        args
                );

                if (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class) {
                    if (value instanceof Boolean) {
                        return value;
                    }

                    return Boolean.TRUE;
                }

                return value;
            }

            Object value = invokeTarget(
                    target,
                    new String[]{methodName},
                    args
            );

            if (value != null) {
                return value;
            }

            if (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class) {
                return Boolean.FALSE;
            }

            if (method.getReturnType() == int.class || method.getReturnType() == Integer.class) {
                return Integer.valueOf(0);
            }

            return null;
        }

        private Object invokeTarget(Object target, String[] methodNames, Object[] args) {
            if (target == null || methodNames == null) {
                return null;
            }

            Object[] safeArgs = args == null ? new Object[0] : args;
            Method[] methods = target.getClass().getMethods();

            for (String methodName : methodNames) {
                for (Method method : methods) {
                    if (!methodName.equals(method.getName())) {
                        continue;
                    }

                    if (method.getParameterTypes().length != safeArgs.length) {
                        continue;
                    }

                    if (!isArgsCompatible(method.getParameterTypes(), safeArgs)) {
                        continue;
                    }

                    try {
                        method.setAccessible(true);
                        return method.invoke(target, safeArgs);
                    } catch (Throwable ignored) {
                    }
                }
            }

            Method[] declaredMethods = target.getClass().getDeclaredMethods();

            for (String methodName : methodNames) {
                for (Method method : declaredMethods) {
                    if (!methodName.equals(method.getName())) {
                        continue;
                    }

                    if (method.getParameterTypes().length != safeArgs.length) {
                        continue;
                    }

                    if (!isArgsCompatible(method.getParameterTypes(), safeArgs)) {
                        continue;
                    }

                    try {
                        method.setAccessible(true);
                        return method.invoke(target, safeArgs);
                    } catch (Throwable ignored) {
                    }
                }
            }

            return null;
        }

        private boolean isArgsCompatible(Class<?>[] parameterTypes, Object[] args) {
            if (parameterTypes == null || args == null) {
                return parameterTypes == args;
            }

            if (parameterTypes.length != args.length) {
                return false;
            }

            for (int i = 0; i < parameterTypes.length; i++) {
                Object arg = args[i];

                if (arg == null) {
                    continue;
                }

                Class<?> parameterType = wrapPrimitive(parameterTypes[i]);
                Class<?> argType = wrapPrimitive(arg.getClass());

                if (!parameterType.isAssignableFrom(argType)) {
                    return false;
                }
            }

            return true;
        }

        private Class<?> wrapPrimitive(Class<?> cls) {
            if (cls == null) {
                return Object.class;
            }

            if (!cls.isPrimitive()) {
                return cls;
            }

            if (cls == int.class) {
                return Integer.class;
            }

            if (cls == long.class) {
                return Long.class;
            }

            if (cls == boolean.class) {
                return Boolean.class;
            }

            if (cls == byte.class) {
                return Byte.class;
            }

            if (cls == short.class) {
                return Short.class;
            }

            if (cls == float.class) {
                return Float.class;
            }

            if (cls == double.class) {
                return Double.class;
            }

            if (cls == char.class) {
                return Character.class;
            }

            return cls;
        }
    }
}

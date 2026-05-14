package com.camera.Protobuf;

import android.util.Log;

import com.camera.Protobuf.config.ConfigRegistry;
import com.camera.Protobuf.config.ProtobufConfig;
import com.camera.Protobuf.config.ProtobufEditor;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Hook 相机解析 camera_unit_feature_config.protobuf 的入口。
 *
 * 注意：
 * 这里不直接修改 /odm/etc/camera/config/camera_unit_feature_config.protobuf 文件。
 * 它是在相机读取并解析 protobuf 后，修改内存里的 FeatureTable。
 */
public final class ProtobufFeature {

    private static final String FEATURE_TABLE_CLASS =
            "com.oplus.ocs.camera.configure.ProtobufFeatureConfig$FeatureTable";

    private final ProtobufModule host;

    private boolean installed = false;

    public ProtobufFeature(ProtobufModule host) {
        this.host = host;
    }

    public void install() {
        if (installed) {
            host.xlog(Log.ERROR, "ProtobufFeature already installed, skip");
            return;
        }

        installed = true;

        hookFeatureTableParseFrom();
    }

    private void hookFeatureTableParseFrom() {
        try {
            Class<?> featureTableClass = host.loadCameraClass(FEATURE_TABLE_CLASS);

            Method[] methods = featureTableClass.getDeclaredMethods();

            boolean hookedAny = false;

            for (Method method : methods) {
                if (!"parseFrom".equals(method.getName())) {
                    continue;
                }

                Class<?>[] parameterTypes = method.getParameterTypes();

                if (parameterTypes.length != 1) {
                    continue;
                }

                if (parameterTypes[0] != byte[].class) {
                    continue;
                }

                method.setAccessible(true);

                host.hookMethod(method).intercept(chain -> {
                    Object originalResult = chain.proceed();

                    if (originalResult == null) {
                        return null;
                    }

                    try {
                        Object newResult = modifyFeatureTable(originalResult);

                        if (newResult != null) {
                            return newResult;
                        }
                    } catch (Throwable t) {
                        host.xlog(Log.ERROR, "ProtobufFeature modifyFeatureTable failed", t);
                    }

                    return originalResult;
                });

                hookedAny = true;

                host.xlog(Log.ERROR, "hook success: FeatureTable.parseFrom(byte[]) -> " + method);
            }

            if (!hookedAny) {
                host.xlog(Log.ERROR, "hook failed: no FeatureTable.parseFrom(byte[]) found");
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "hook FeatureTable.parseFrom failed", t);
        }
    }

    private Object modifyFeatureTable(Object originalConfig) throws Exception {
        host.xlog(Log.ERROR, "ProtobufFeature intercepted FeatureTable, start modify");

        Object featureTableBuilder = ProtobufEditor.callMethod(originalConfig, "toBuilder");

        ProtobufEditor editor = new ProtobufEditor(host, featureTableBuilder);

        List<ProtobufConfig> configs = ConfigRegistry.loadAll(host);

        if (configs.isEmpty()) {
            host.xlog(Log.ERROR, "ProtobufFeature no config found under com.camera.Protobuf.config");
            return originalConfig;
        }

        boolean changed = false;

        for (ProtobufConfig config : configs) {
            try {
                host.xlog(Log.ERROR, "ProtobufFeature apply config: " + config.name());

                boolean configChanged = config.apply(editor);

                if (configChanged) {
                    changed = true;
                    host.xlog(Log.ERROR, "ProtobufFeature config applied: " + config.name());
                } else {
                    host.xlog(Log.ERROR, "ProtobufFeature config no change: " + config.name());
                }
            } catch (Throwable t) {
                host.xlog(Log.ERROR, "ProtobufFeature config failed: " + config.name(), t);
            }
        }

        if (!changed) {
            host.xlog(Log.ERROR, "ProtobufFeature no change applied");
            return originalConfig;
        }

        Object newConfig = ProtobufEditor.callMethod(featureTableBuilder, "build");

        host.xlog(Log.ERROR, "ProtobufFeature build new FeatureTable success");

        return newConfig;
    }
}
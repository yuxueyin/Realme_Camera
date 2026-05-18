package com.camera.Protobuf;

import android.util.Log;

import com.camera.Protobuf.config.ConfigRegistry;
import com.camera.Protobuf.config.ProtobufConfig;
import com.camera.Protobuf.config.ProtobufEditor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Hook 相机解析 camera_unit_feature_config.protobuf 的入口。
 *
 * V15：强制追加 AiSceneryModeConfig fallback，避免 ConfigRegistry 没覆盖时只加载 GR/Vibe。
 */
public final class ProtobufFeature {

    private static final String FEATURE_TABLE_CLASS =
            "com.oplus.ocs.camera.configure.ProtobufFeatureConfig$FeatureTable";

    private static final String AI_CONFIG_CLASS =
            "com.camera.Protobuf.config.AiSceneryModeConfig";

    private static final String V15_MARK = "AI_SCENERY_PROTO_V15_FORCE_LOAD";

    private final ProtobufModule host;

    private boolean installed = false;

    public ProtobufFeature(ProtobufModule host) {
        this.host = host;
    }

    public void install() {
        if (installed) {
            host.xlog(Log.ERROR, "ProtobufFeature already installed, skip " + V15_MARK);
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
                        host.xlog(Log.ERROR, "ProtobufFeature modifyFeatureTable failed " + V15_MARK, t);
                    }

                    return originalResult;
                });

                hookedAny = true;

                host.xlog(Log.ERROR, "hook success: FeatureTable.parseFrom(byte[]) -> " + method + " " + V15_MARK);
            }

            if (!hookedAny) {
                host.xlog(Log.ERROR, "hook failed: no FeatureTable.parseFrom(byte[]) found " + V15_MARK);
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "hook FeatureTable.parseFrom failed " + V15_MARK, t);
        }
    }

    private Object modifyFeatureTable(Object originalConfig) throws Exception {
        host.xlog(Log.ERROR, "ProtobufFeature intercepted FeatureTable, start modify " + V15_MARK);

        Object featureTableBuilder = ProtobufEditor.callMethod(originalConfig, "toBuilder");

        ProtobufEditor editor = new ProtobufEditor(host, featureTableBuilder);

        List<ProtobufConfig> configs = new ArrayList<>(ConfigRegistry.loadAll(host));
        ensureAiSceneryConfig(configs);

        if (configs.isEmpty()) {
            host.xlog(Log.ERROR, "ProtobufFeature no config found under com.camera.Protobuf.config " + V15_MARK);
            return originalConfig;
        }

        boolean changed = false;

        for (ProtobufConfig config : configs) {
            try {
                String configName = safeConfigName(config);

                host.xlog(Log.ERROR, "ProtobufFeature apply config: " + configName + " " + V15_MARK);

                boolean configChanged = config.apply(editor);

                if (configChanged) {
                    changed = true;
                    host.xlog(Log.ERROR, "ProtobufFeature config applied: " + configName + " " + V15_MARK);
                } else {
                    host.xlog(Log.ERROR, "ProtobufFeature config no change: " + configName + " " + V15_MARK);
                }
            } catch (Throwable t) {
                host.xlog(Log.ERROR, "ProtobufFeature config failed: " + safeConfigName(config) + " " + V15_MARK, t);
            }
        }

        if (!changed) {
            host.xlog(Log.ERROR, "ProtobufFeature no change applied " + V15_MARK);
            return originalConfig;
        }

        Object newConfig = ProtobufEditor.callMethod(featureTableBuilder, "build");

        host.xlog(Log.ERROR, "ProtobufFeature build new FeatureTable success " + V15_MARK);

        return newConfig;
    }

    private void ensureAiSceneryConfig(List<ProtobufConfig> configs) {
        if (configs == null) {
            return;
        }

        for (ProtobufConfig config : configs) {
            String name = safeConfigName(config);
            if ("AiSceneryModeConfig".equals(name)
                    || name.contains("AiScenery")
                    || name.contains("Scenery")) {
                host.xlog(Log.ERROR, "ProtobufFeature AiScenery config already loaded " + V15_MARK);
                return;
            }
        }

        try {
            Class<?> cls = Class.forName(AI_CONFIG_CLASS);
            Object instance = cls.getDeclaredConstructor().newInstance();

            if (instance instanceof ProtobufConfig) {
                configs.add((ProtobufConfig) instance);
                host.xlog(Log.ERROR, "ProtobufFeature fallback add config: " + AI_CONFIG_CLASS + " " + V15_MARK);
            } else {
                host.xlog(Log.ERROR, "ProtobufFeature fallback class not ProtobufConfig: " + AI_CONFIG_CLASS + " " + V15_MARK);
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "ProtobufFeature fallback add AiScenery config failed " + V15_MARK, t);
        }
    }

    private String safeConfigName(ProtobufConfig config) {
        if (config == null) {
            return "null";
        }

        try {
            String name = config.name();
            if (name != null && name.length() > 0) {
                return name;
            }
        } catch (Throwable ignored) {
        }

        return config.getClass().getSimpleName();
    }
}

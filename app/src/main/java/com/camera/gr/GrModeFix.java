package com.camera.gr;

import android.media.ImageReader;
import android.util.Log;
import android.util.Pair;
import android.util.Size;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GrModeFix {

    private static final String EFFECT_STYLE_SUPPORT_TAG =
            "com.oplus.feature.effect.style.support";

    private static final String GR_CASE_NAME = "gr_mode_case";
    private static final String VIBE_CASE_NAME = "vibe_mode_case";
    private static final String AI_SCENERY_CASE_NAME = "sat_street_case";
    private static final String AI_SCENERY_ALT_CASE_NAME = "ai_scenery_mode_case";
    private static final String GR_FALLBACK_CASE_NAME = "sat_photo_case";

    private static final String GR_MODE_NAME = "gr_mode";
    private static final String GR_SHORT_MODE_NAME = "gr";
    private static final String VIBE_MODE_NAME = "vibe_mode";
    private static final String VIBE_SHORT_MODE_NAME = "vibe";
    private static final String AI_SCENERY_MODE_NAME = "ai_scenery_mode";
    private static final String AI_SCENERY_SHORT_MODE_NAME = "aiScenery";
    private static final String PHOTO_MODE_NAME = "photo_mode";
    private static final String COMMON_MODE_NAME = "common";

    private static final String GR_BAD_OPERATION_HEX_UPPER = "80BE";
    private static final String GR_BAD_OPERATION_HEX_LOWER = "80be";
    private static final String GR_FALLBACK_OPERATION_HEX = "8001";

    private static final long MODE_NAME_RESTORE_DELAY_MS = 3500L;
    private static final long GR_PATCH_WINDOW_MS = 60000L;

    private static final int FILTER_MODE_UNKNOWN = 0;
    private static final int FILTER_MODE_GR = 1;
    private static final int FILTER_MODE_MASTER = 2;

    private static final long FILTER_SWITCH_GUARD_MS = 7000L;

    /**
     * GR 最终拍照尺寸。
     *
     * 不能只固定 3072x4096。
     * 横拍必须使用 4096x3072，竖拍必须使用 3072x4096。
     */
    private static final int FULL_LANDSCAPE_WIDTH = 4096;
    private static final int FULL_LANDSCAPE_HEIGHT = 3072;

    private static final int FULL_PORTRAIT_WIDTH = 3072;
    private static final int FULL_PORTRAIT_HEIGHT = 4096;

    /**
     * 本机 HAL 实际给出的高像素输入是 8192x6144 / 6144x8192。
     * 2 亿模式不再做 APS 超分输出；当前设备按真实 8192x6144 / 6144x8192 高像素输入保存为 50MP。
     */
    private static final int AI_SCENERY_HP_INPUT_LANDSCAPE_WIDTH = 8192;
    private static final int AI_SCENERY_HP_INPUT_LANDSCAPE_HEIGHT = 6144;
    private static final int AI_SCENERY_HP_INPUT_PORTRAIT_WIDTH = 6144;
    private static final int AI_SCENERY_HP_INPUT_PORTRAIT_HEIGHT = 8192;


    private static final long AI_SCENERY_HIGH_PIXEL_GUARD_MS = 30000L;

    /**
     * 保留旧常量名，避免其它旧逻辑引用时报错。
     * 旧常量只代表横拍完整尺寸。
     */
    private static final int FULL_WIDTH = FULL_LANDSCAPE_WIDTH;
    private static final int FULL_HEIGHT = FULL_LANDSCAPE_HEIGHT;

    /**
     * 预览安全尺寸。
     * 注意：预览不能被改成 4096x3072 / 3072x4096，否则会模糊、拉伸或卡住。
     */
    private static final int PREVIEW_SAFE_LANDSCAPE_WIDTH = 1920;
    private static final int PREVIEW_SAFE_LANDSCAPE_HEIGHT = 1440;
    private static final int PREVIEW_SAFE_PORTRAIT_WIDTH = 1440;
    private static final int PREVIEW_SAFE_PORTRAIT_HEIGHT = 1920;

    private static final int META_BAD_WIDTH = 720;
    private static final int META_BAD_HEIGHT = 480;
    private static final int META_SAFE_LANDSCAPE_WIDTH = 1440;
    private static final int META_SAFE_LANDSCAPE_HEIGHT = 1080;
    private static final int META_SAFE_PORTRAIT_WIDTH = 1080;
    private static final int META_SAFE_PORTRAIT_HEIGHT = 1440;

    /**
     * 保留旧常量名，避免其它旧逻辑引用时报错。
     * 旧常量只代表横向 metadata 安全尺寸。
     */
    private static final int META_SAFE_WIDTH = META_SAFE_LANDSCAPE_WIDTH;
    private static final int META_SAFE_HEIGHT = META_SAFE_LANDSCAPE_HEIGHT;
    private static final int META_FORMAT_32 = 32;

    private static final int FINAL_PICTURE_FORMAT = 37;

    private final GrModule host;

    private volatile long grPatchUntilMs = 0L;

    /**
     * 当前 GR 最终输出尺寸。
     * 横拍记录为 4096x3072，竖拍记录为 3072x4096。
     * APS output_width/output_height 会读取这里，不能写死。
     */
    private volatile int currentFullWidth = FULL_LANDSCAPE_WIDTH;
    private volatile int currentFullHeight = FULL_LANDSCAPE_HEIGHT;

    private volatile long aiSceneryHighPixelUntilMs = 0L;

    private volatile int currentFilterMode = FILTER_MODE_UNKNOWN;
    private volatile long currentFilterModeUpdateMs = 0L;
    private volatile String currentFilterModeReason = "init";

    private volatile int pendingSwitchMode = FILTER_MODE_UNKNOWN;
    private volatile long pendingSwitchUntilMs = 0L;
    private volatile String pendingSwitchReason = "none";

    private final ThreadLocal<Boolean> grCreateSessionActive = new ThreadLocal<>();

    public GrModeFix(GrModule host) {
        this.host = host;
    }

    public void install() {

        hookAndroidLogForFilterMode();

        hookCameraConfigEffectStyleSupportForGr();

        hookGrCaseStringReturnFallback();
        hookBaseModeUseCaseAndSurfaceSize();
        hookImageReaderForMetadataSurface();
        hookSurfacePoolFinalPictureWrapper();
        hookDisableQuickJpegAndFullOutputInAps();
        hookCamera2ImplCreateNewSessionDelayRestore();
        hookFaceBeautyPhotoChainModeFallback();

        host.xlog(Log.ERROR, "GrModeFix installed package=com.camera.gr v53-ai-scenery-200mp-as-50mp-no-sr");
    }

    private void hookAndroidLogForFilterMode() {
        try {
            Class clazz = Log.class;

            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                String methodName = method.getName();

                boolean isPrintln = "println".equals(methodName);
                boolean isLevelLog = "d".equals(methodName)
                        || "e".equals(methodName)
                        || "w".equals(methodName)
                        || "i".equals(methodName)
                        || "v".equals(methodName);

                if (!isPrintln && !isLevelLog) {
                    continue;
                }

                Class[] parameterTypes = method.getParameterTypes();

                if (parameterTypes == null) {
                    continue;
                }

                if (isPrintln) {
                    if (parameterTypes.length != 3) {
                        continue;
                    }

                    if (parameterTypes[0] != int.class
                            || parameterTypes[1] != String.class
                            || parameterTypes[2] != String.class) {
                        continue;
                    }
                }

                if (isLevelLog) {
                    if (parameterTypes.length < 2) {
                        continue;
                    }

                    if (parameterTypes[0] != String.class
                            || parameterTypes[1] != String.class) {
                        continue;
                    }
                }

                method.setAccessible(true);

                host.hookMethod(method).intercept(chain -> {
                    List argsList = chain.getArgs();

                    if (argsList == null) {
                        return chain.proceed();
                    }

                    Object tagObject;
                    Object msgObject;

                    if ("println".equals(methodName)) {
                        if (argsList.size() < 3) {
                            return chain.proceed();
                        }

                        tagObject = argsList.get(1);
                        msgObject = argsList.get(2);
                    } else {
                        if (argsList.size() < 2) {
                            return chain.proceed();
                        }

                        tagObject = argsList.get(0);
                        msgObject = argsList.get(1);
                    }

                    if (tagObject instanceof String || msgObject instanceof String) {
                        updateFilterModeFromCameraLog(
                                String.valueOf(tagObject),
                                String.valueOf(msgObject)
                        );
                    }

                    return chain.proceed();
                });

                host.xlog(Log.ERROR, "hook success: GrModeFix android.util.Log." + methodName + " v42 -> " + method);
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "hook GrModeFix android.util.Log methods v42 failed: " + String.valueOf(t));
        }
    }

    private void updateFilterModeFromCameraLog(String tag, String msg) {
        if (msg == null) {
            return;
        }

        if (tag != null) {
            String tagLower = tag.toLowerCase(Locale.ROOT);

            if (tagLower.contains("grmodefix")
                    || tagLower.contains("lumoblockfinal")
                    || tagLower.contains("lsposed")) {
                return;
            }
        }

        String text = String.valueOf(tag) + " " + msg;
        String lower = text.toLowerCase(Locale.ROOT);

        if (isAiSceneryHighPixelText(lower)) {
            markAiSceneryHighPixelWindow("camera-log " + safeShort(text));
        }

        if (!isCameraModeLog(lower)) {
            return;
        }

        if (isNoisyOrStaleModeLog(lower)) {
            host.xlog(
                    Log.ERROR,
                    "GR_FILTER_MODE_V42 ignore noisy log text=" + safeShort(text)
            );
            return;
        }

        int strongTargetMode = detectStrongSwitchTarget(lower);

        if (strongTargetMode == FILTER_MODE_GR || strongTargetMode == FILTER_MODE_MASTER) {
            markPendingSwitchMode(
                    strongTargetMode,
                    "strong-camera-log " + safeShort(text)
            );
            return;
        }

        int stableMode = detectStableModeLog(lower);

        if (stableMode == FILTER_MODE_GR || stableMode == FILTER_MODE_MASTER) {
            if (isPendingSwitchActive()) {
                int pendingMode = pendingSwitchMode;

                if (pendingMode != FILTER_MODE_UNKNOWN && pendingMode != stableMode) {
                    host.xlog(
                            Log.ERROR,
                            "GR_FILTER_MODE_V42 ignore stale mode="
                                    + filterModeToString(stableMode)
                                    + " pending="
                                    + filterModeToString(pendingMode)
                                    + " reason="
                                    + pendingSwitchReason
                                    + " text="
                                    + safeShort(text)
                    );
                    return;
                }
            }

            setCurrentFilterMode(
                    stableMode,
                    "stable-camera-log " + safeShort(text)
            );
        }
    }

    private boolean isCameraModeLog(String lower) {
        if (lower == null) {
            return false;
        }

        return lower.contains("ocam_modeswitcher")
                || lower.contains("ocam_cameracontrolui")
                || lower.contains("ocam_ratiopresenter")
                || lower.contains("ocam_screenswitchcontroller")
                || lower.contains("ocam_cameracore")
                || lower.contains("checktoswitchmode")
                || lower.contains("aftermodechanged")
                || lower.contains("onmodechanged")
                || lower.contains("beforemodechanged")
                || lower.contains("onmodeswitchstagechanged")
                || lower.contains("camera mode change start")
                || lower.contains("updatecapmode")
                || lower.contains("next mode")
                || lower.contains("modename")
                || lower.contains("modenameforcameraunit")
                || lower.contains("unitmode")
                || lower.contains("cameratest");
    }

    private boolean isNoisyOrStaleModeLog(String lower) {
        if (lower == null) {
            return true;
        }

        if (lower.contains("initcamerafeaturetable")) {
            return true;
        }

        if (lower.contains("is incorrect")) {
            return true;
        }

        if (lower.contains("modeTableSize".toLowerCase(Locale.ROOT))) {
            return true;
        }

        return false;
    }

    private int detectStrongSwitchTarget(String lower) {
        if (lower == null) {
            return FILTER_MODE_UNKNOWN;
        }

        if (lower.contains("next mode: gr")
                || lower.contains("mode: professional => gr")
                || lower.contains("mode: master => gr")
                || lower.contains("mode: photo => gr")
                || lower.contains("mode: common => gr")
                || lower.contains("curmodename: professional, modename: gr")
                || lower.contains("curmodename: master, modename: gr")
                || lower.contains("curmodename: common, modename: gr")
                || (lower.contains("checktoswitchmode") && lower.contains("modename: gr"))) {
            return FILTER_MODE_GR;
        }

        if (lower.contains("next mode: professional")
                || lower.contains("next mode: master")
                || lower.contains("mode: gr => professional")
                || lower.contains("mode: photo => professional")
                || lower.contains("mode: common => professional")
                || lower.contains("mode: gr => master")
                || lower.contains("mode: photo => master")
                || lower.contains("mode: common => master")
                || lower.contains("curmodename: gr, modename: professional")
                || lower.contains("curmodename: common, modename: professional")
                || lower.contains("curmodename: gr, modename: master")
                || lower.contains("curmodename: common, modename: master")
                || (lower.contains("checktoswitchmode") && lower.contains("modename: professional"))
                || (lower.contains("checktoswitchmode") && lower.contains("modename: master"))) {
            return FILTER_MODE_MASTER;
        }

        return FILTER_MODE_UNKNOWN;
    }

    private int detectStableModeLog(String lower) {
        if (lower == null) {
            return FILTER_MODE_UNKNOWN;
        }

        boolean ratioStage = lower.contains("ocam_ratiopresenter")
                && lower.contains("onmodeswitchstagechanged");
        boolean screenUpdate = lower.contains("ocam_screenswitchcontroller")
                && lower.contains("updatecapmode");
        boolean modeSwitcher = lower.contains("ocam_modeswitcher")
                && lower.contains("checktoswitchmode");
        boolean cameraControl = lower.contains("ocam_cameracontrolui")
                && lower.contains("camera mode change start");

        if (ratioStage || screenUpdate || modeSwitcher || cameraControl) {
            if (lower.contains("modename：gr")
                    || lower.contains("modename: gr")
                    || lower.contains("mode name：gr")
                    || lower.contains("mode name: gr")
                    || lower.contains("next mode: gr")
                    || lower.contains("=> gr")) {
                return FILTER_MODE_GR;
            }

            if (lower.contains("modename：professional")
                    || lower.contains("modename: professional")
                    || lower.contains("mode name：professional")
                    || lower.contains("mode name: professional")
                    || lower.contains("next mode: professional")
                    || lower.contains("=> professional")
                    || lower.contains("modename：master")
                    || lower.contains("modename: master")
                    || lower.contains("mode name：master")
                    || lower.contains("mode name: master")
                    || lower.contains("next mode: master")
                    || lower.contains("=> master")) {
                return FILTER_MODE_MASTER;
            }
        }

        return FILTER_MODE_UNKNOWN;
    }

    private boolean isPendingSwitchActive() {
        return pendingSwitchMode != FILTER_MODE_UNKNOWN
                && System.currentTimeMillis() <= pendingSwitchUntilMs;
    }

    private void markPendingSwitchMode(int mode, String reason) {
        if (mode != FILTER_MODE_GR && mode != FILTER_MODE_MASTER) {
            return;
        }

        pendingSwitchMode = mode;
        pendingSwitchUntilMs = System.currentTimeMillis() + FILTER_SWITCH_GUARD_MS;
        pendingSwitchReason = reason;

        setCurrentFilterMode(mode, reason);

        host.xlog(
                Log.ERROR,
                "GR_FILTER_MODE_V42 pending mode="
                        + filterModeToString(mode)
                        + " untilMs="
                        + pendingSwitchUntilMs
                        + " reason="
                        + reason
        );
    }

    private void setCurrentFilterMode(int mode, String reason) {
        if (mode != FILTER_MODE_GR
                && mode != FILTER_MODE_MASTER
                && mode != FILTER_MODE_UNKNOWN) {
            return;
        }

        if (currentFilterMode == mode && String.valueOf(currentFilterModeReason).equals(reason)) {
            return;
        }

        currentFilterMode = mode;
        currentFilterModeUpdateMs = System.currentTimeMillis();
        currentFilterModeReason = reason;

        String modeName = filterModeToString(mode);

        host.xlog(
                Log.ERROR,
                "GR_FILTER_MODE_V42 set mode="
                        + modeName
                        + " reason="
                        + reason
        );
    }

    private String filterModeToString(int mode) {
        if (mode == FILTER_MODE_GR) {
            return "GR";
        }

        if (mode == FILTER_MODE_MASTER) {
            return "MASTER";
        }

        return "UNKNOWN";
    }

    private void hookCameraConfigEffectStyleSupportForGr() {
        try {
            Class clazz = host.loadCameraClass("com.oplus.camera.configure.CameraConfig");

            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                if (!"getConfigBooleanValue".equals(method.getName())) {
                    continue;
                }

                Class[] parameterTypes = method.getParameterTypes();

                if (parameterTypes == null || parameterTypes.length < 1) {
                    continue;
                }

                if (parameterTypes[0] != String.class) {
                    continue;
                }

                if (method.getReturnType() != boolean.class
                        && method.getReturnType() != Boolean.class) {
                    continue;
                }

                method.setAccessible(true);

                host.hookMethod(method).intercept(chain -> {
                    List argsList = chain.getArgs();

                    if (argsList == null || argsList.isEmpty()) {
                        return chain.proceed();
                    }

                    Object keyObject = argsList.get(0);

                    if (!isEffectStyleSupportKey(keyObject)) {
                        return chain.proceed();
                    }

                    int mode = resolveCurrentFilterModeForEffectStyle();

                    if (mode == FILTER_MODE_GR) {
                        host.xlog(
                                Log.ERROR,
                                "GR_FILTER_FIX_V42 use gr filter-set "
                                        + EFFECT_STYLE_SUPPORT_TAG
                                        + " return=false reason="
                                        + currentFilterModeReason
                                        + " method="
                                        + method
                        );

                        return false;
                    }

                    if (mode == FILTER_MODE_MASTER) {
                        host.xlog(
                                Log.ERROR,
                                "GR_FILTER_FIX_V42 use master filter-set "
                                        + EFFECT_STYLE_SUPPORT_TAG
                                        + " return=true reason="
                                        + currentFilterModeReason
                                        + " method="
                                        + method
                        );

                        return true;
                    }

                    Object result = chain.proceed();

                    host.xlog(
                            Log.ERROR,
                            "GR_FILTER_FIX_V42 keep original "
                                    + EFFECT_STYLE_SUPPORT_TAG
                                    + " result="
                                    + describeValue(result)
                                    + " reason="
                                    + currentFilterModeReason
                                    + " method="
                                    + method
                    );

                    return result;
                });

                host.xlog(
                        Log.ERROR,
                        "hook success: GrModeFix CameraConfig.getConfigBooleanValue effect.style v42 -> "
                                + method
                );
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "hook GrModeFix CameraConfig effect.style v42 failed: " + String.valueOf(t));
        }
    }

    private boolean isEffectStyleSupportKey(Object keyObject) {
        if (keyObject == null) {
            return false;
        }

        String key = String.valueOf(keyObject);

        return EFFECT_STYLE_SUPPORT_TAG.equals(key)
                || key.contains(EFFECT_STYLE_SUPPORT_TAG)
                || key.contains("effect.style.support");
    }

    private int resolveCurrentFilterModeForEffectStyle() {
        if (isPendingSwitchActive()) {
            int mode = pendingSwitchMode;

            if (mode == FILTER_MODE_GR || mode == FILTER_MODE_MASTER) {
                host.xlog(
                        Log.ERROR,
                        "GR_FILTER_FIX_V42 use pending mode="
                                + filterModeToString(mode)
                                + " reason="
                                + pendingSwitchReason
                );
                return mode;
            }
        }

        int modeByStack = detectFilterModeByStack();

        if (modeByStack == FILTER_MODE_MASTER) {
            setCurrentFilterMode(FILTER_MODE_MASTER, "stack professional/master");
            return FILTER_MODE_MASTER;
        }

        if (modeByStack == FILTER_MODE_GR) {
            setCurrentFilterMode(FILTER_MODE_GR, "stack gr");
            return FILTER_MODE_GR;
        }

        int modeByLog = currentFilterMode;

        if (modeByLog == FILTER_MODE_GR || modeByLog == FILTER_MODE_MASTER) {
            host.xlog(
                    Log.ERROR,
                    "GR_FILTER_FIX_V42 use log mode="
                            + filterModeToString(modeByLog)
                            + " ageMs="
                            + (System.currentTimeMillis() - currentFilterModeUpdateMs)
                            + " reason="
                            + currentFilterModeReason
            );

            return modeByLog;
        }

        return FILTER_MODE_UNKNOWN;
    }

    private int detectFilterModeByR7() {

        return FILTER_MODE_UNKNOWN;
    }

    private int detectFilterModeByStack() {
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

            if (stackTrace == null) {
                return FILTER_MODE_UNKNOWN;
            }

            boolean hasGr = false;
            boolean hasMaster = false;

            for (StackTraceElement element : stackTrace) {
                if (element == null) {
                    continue;
                }

                String className = String.valueOf(element.getClassName());
                String methodName = String.valueOf(element.getMethodName());

                String lower = (className + "#" + methodName).toLowerCase(Locale.ROOT);

                if (lower.contains("com.camera.gr.")
                        || lower.contains("lsposed")
                        || lower.contains("xposed")
                        || lower.contains("edxp")) {
                    continue;
                }

                if (lower.contains("grcapmode")
                        || lower.contains("gr_mode")
                        || lower.contains(".gr.")
                        || lower.contains("ricoh")) {
                    hasGr = true;
                }

                if (lower.contains("professional")
                        || lower.contains("master")
                        || lower.contains("promode")
                        || lower.contains("prophoto")) {
                    hasMaster = true;
                }
            }

            if (hasMaster) {
                return FILTER_MODE_MASTER;
            }

            if (hasGr) {
                return FILTER_MODE_GR;
            }
        } catch (Throwable ignored) {
        }

        return FILTER_MODE_UNKNOWN;
    }

    private void hookGrCaseStringReturnFallback() {
        String[] classNames = new String[]{
                "com.oplus.ocs.camera.producer.mode.GRCapMode",
                "com.oplus.ocs.camera.producer.mode.VibeCapMode",
                "com.oplus.ocs.camera.producer.mode.AISceneryMode",
                "com.oplus.ocs.camera.producer.mode.BaseMode"
        };

        for (String className : classNames) {
            try {
                Class clazz = host.loadCameraClass(className);

                Method[] methods = clazz.getDeclaredMethods();

                for (Method method : methods) {
                    if (Modifier.isAbstract(method.getModifiers())) {
                        continue;
                    }

                    if (method.getReturnType() != String.class) {
                        continue;
                    }

                    if (method.getParameterTypes().length > 3) {
                        continue;
                    }

                    method.setAccessible(true);

                    host.hookMethod(method).intercept(chain -> {
                        List argsList = chain.getArgs();

                        Object[] newArgs;

                        if (argsList == null || argsList.isEmpty()) {
                            newArgs = new Object[0];
                        } else {
                            newArgs = argsList.toArray(new Object[0]);
                        }

                        boolean argChanged = replaceGrCaseInArgs(
                                newArgs,
                                "StringReturn." + method.getName()
                        );

                        Object result;

                        if (argChanged) {
                            result = chain.proceed(newArgs);
                        } else {
                            result = chain.proceed();
                        }

                        if (result instanceof String) {
                            String oldValue = (String) result;
                            String newValue = fixPhotoChainTextOnly(oldValue);

                            if (!oldValue.equals(newValue)) {
                                markGrPatchWindow();

                                host.xlog(
                                        Log.ERROR,
                                        "GR_RETURN_STRING_V42 replace class="
                                                + method.getDeclaringClass().getName()
                                                + " method="
                                                + method.getName()
                                                + " old="
                                                + oldValue
                                                + " new="
                                                + newValue
                                );

                                return newValue;
                            }
                        }

                        return result;
                    });

                    host.xlog(
                            Log.ERROR,
                            "hook success: GrModeFix GR String return v42 -> "
                                    + className
                                    + "#"
                                    + method
                    );
                }
            } catch (Throwable t) {
                host.xlog(
                        Log.ERROR,
                        "hook GrModeFix GR String return v42 failed class="
                                + className
                                + " err="
                                + String.valueOf(t)
                );
            }
        }
    }

    private void hookBaseModeUseCaseAndSurfaceSize() {
        try {
            Class clazz = host.loadCameraClass("com.oplus.ocs.camera.producer.mode.BaseMode");

            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                String methodName = method.getName();

                if (!"getUseCaseValues".equals(methodName)
                        && !"buildStreamSurface".equals(methodName)) {
                    continue;
                }

                method.setAccessible(true);

                host.hookMethod(method).intercept(chain -> {
                    Object thisObject = chain.getThisObject();

                    List argsList = chain.getArgs();

                    Object[] newArgs;

                    if (argsList == null || argsList.isEmpty()) {
                        newArgs = new Object[0];
                    } else {
                        newArgs = argsList.toArray(new Object[0]);
                    }

                    boolean changed = false;

                    changed |= replaceGrCaseInArgs(
                            newArgs,
                            "BaseMode." + methodName
                    );

                    boolean aiSceneryRelated = isAiSceneryRelatedObject(thisObject)
                            || containsText(newArgs, AI_SCENERY_CASE_NAME)
                            || containsText(newArgs, AI_SCENERY_ALT_CASE_NAME)
                            || containsText(newArgs, AI_SCENERY_MODE_NAME);

                    if (isGrRelatedObject(thisObject) || aiSceneryRelated) {
                        markGrPatchWindow();

                    }

                    if ("buildStreamSurface".equals(methodName)
                            && (isGrRelatedObject(thisObject) || aiSceneryRelated)
                            && isTargetRearMainRawOutputArgs(newArgs)) {
                        changed |= patchSurfaceSizePairsInArgs(
                                newArgs,
                                "BaseMode.buildStreamSurface"
                        );
                    }

                    if (isGrRelatedObject(thisObject) || isVibeRelatedObject(thisObject) || aiSceneryRelated
                            || containsText(newArgs, GR_CASE_NAME) || containsText(newArgs, VIBE_CASE_NAME)
                            || containsText(newArgs, AI_SCENERY_CASE_NAME) || containsText(newArgs, AI_SCENERY_ALT_CASE_NAME)) {
                        host.xlog(
                                Log.ERROR,
                                "GR_BASEMODE_V42 call method="
                                        + methodName
                                        + " this="
                                        + objectClassName(thisObject)
                                        + " changed="
                                        + changed
                                        + " args="
                                        + describeValue(newArgs)
                        );
                    }

                    if (changed) {
                        return chain.proceed(newArgs);
                    }

                    return chain.proceed();
                });

                host.xlog(Log.ERROR, "hook success: GrModeFix BaseMode." + methodName + " v42 -> " + method);
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "hook GrModeFix BaseMode usecase/surface v42 failed: " + String.valueOf(t));
        }
    }

    private boolean isTargetRearMainRawOutputArgs(Object[] args) {
        String text = describeValue(args);

        if (text == null) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);

        if (!lower.contains("raw_output")) {
            return false;
        }

        if (!lower.contains("rear_main")) {
            return false;
        }

        if (lower.contains("rear_tele")) {
            return false;
        }

        if (lower.contains("capture_yuv")) {
            return false;
        }

        if (lower.contains("raw16_output")) {
            return false;
        }

        return true;
    }

    private boolean patchSurfaceSizePairsInArgs(Object object, String where) {
        return patchSurfaceSizePairsDeep(
                object,
                Collections.newSetFromMap(new IdentityHashMap<>()),
                0,
                where
        );
    }

    private boolean patchSurfaceSizePairsDeep(
            Object object,
            Set<Object> visited,
            int depth,
            String where
    ) {
        if (object == null) {
            return false;
        }

        if (depth > 3) {
            return false;
        }

        if (visited.contains(object)) {
            return false;
        }

        visited.add(object);

        boolean changed = false;

        if (object instanceof Object[]) {
            Object[] array = (Object[]) object;

            for (Object item : array) {
                changed |= patchSurfaceSizePairsDeep(
                        item,
                        visited,
                        depth + 1,
                        where + "[]"
                );
            }

            return changed;
        }

        if (object instanceof List) {
            List list = (List) object;

            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);

                Object first = getPairFirst(item);
                Object second = getPairSecond(item);

                if (first instanceof Size && second instanceof Size) {
                    Size appSize = (Size) first;
                    Size halSize = (Size) second;

                    if (isSmall720(appSize) && isFullSize(halSize)) {
                        Size targetFullSize = chooseFullSizeByOldSize(halSize);

                        updateCurrentFullSize(targetFullSize, "patchSurfaceSizePairsDeep halSize=" + halSize);

                        Pair newPair = new Pair(
                                targetFullSize,
                                targetFullSize
                        );

                        try {
                            list.set(i, newPair);
                            changed = true;

                            host.xlog(
                                    Log.ERROR,
                                    "GR_SURFACE_PAIR_V43 fix where="
                                            + where
                                            + " index="
                                            + i
                                            + " oldApp="
                                            + appSize
                                            + " oldHal="
                                            + halSize
                                            + " newPair="
                                            + newPair
                            );
                        } catch (Throwable t) {
                            host.xlog(Log.ERROR, "GR_SURFACE_PAIR_V43 fix failed: " + String.valueOf(t));
                        }
                    }
                }

                changed |= patchSurfaceSizePairsDeep(
                        item,
                        visited,
                        depth + 1,
                        where + ".list"
                );
            }

            return changed;
        }

        return false;
    }

    private void hookImageReaderForMetadataSurface() {
        try {
            Class clazz = ImageReader.class;

            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                if (!"newInstance".equals(method.getName())) {
                    continue;
                }

                Class[] parameterTypes = method.getParameterTypes();

                if (parameterTypes == null || parameterTypes.length < 4) {
                    continue;
                }

                if (parameterTypes[0] != int.class
                        || parameterTypes[1] != int.class
                        || parameterTypes[2] != int.class
                        || parameterTypes[3] != int.class) {
                    continue;
                }

                method.setAccessible(true);

                host.hookMethod(method).intercept(chain -> {
                    List argsList = chain.getArgs();

                    if (argsList == null || argsList.size() < 4) {
                        return chain.proceed();
                    }

                    Object[] newArgs = argsList.toArray(new Object[0]);

                    int width = toInt(newArgs[0], -1);
                    int height = toInt(newArgs[1], -1);
                    int format = toInt(newArgs[2], -1);
                    int maxImages = toInt(newArgs[3], -1);

                    boolean active = Boolean.TRUE.equals(grCreateSessionActive.get());
                    boolean smallMeta =
                            format == META_FORMAT_32
                                    && (
                                    (width == META_BAD_WIDTH && height == META_BAD_HEIGHT)
                                            || (width == META_BAD_HEIGHT && height == META_BAD_WIDTH)
                            );

                    if (active && smallMeta) {
                        Size safeMetaSize = chooseMetaSafeSizeByOld(width, height);

                        newArgs[0] = safeMetaSize.getWidth();
                        newArgs[1] = safeMetaSize.getHeight();

                        host.xlog(
                                Log.ERROR,
                                "GR_META_SURFACE_V43 fix ImageReader.newInstance"
                                        + " old="
                                        + width
                                        + "x"
                                        + height
                                        + " new="
                                        + safeMetaSize.getWidth()
                                        + "x"
                                        + safeMetaSize.getHeight()
                                        + " format="
                                        + format
                                        + " maxImages="
                                        + maxImages
                        );

                        return chain.proceed(newArgs);
                    }

                    /*
                     * 关键：这里不要把普通预览 / metadata 改成 4096x3072 或 3072x4096。
                     * 大图尺寸只能给最终 picture/capture surface。
                     */
                    return chain.proceed();
                });

                host.xlog(Log.ERROR, "hook success: GrModeFix ImageReader metadata v43 -> " + method);
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "hook GrModeFix ImageReader metadata v43 failed: " + String.valueOf(t));
        }
    }


    private void hookSurfacePoolFinalPictureWrapper() {
        String[] classNames = new String[]{
                "com.oplus.ocs.camera.common.surface.SurfacePool",
                "com.oplus.ocs.camera.producer.device.SurfacePool",
                "com.oplus.ocs.camera.producer.surface.SurfacePool",
                "com.oplus.ocs.camera.producer.device.CameraSurfacePool",
                "com.oplus.camera.common.surface.SurfacePool"
        };

        for (String className : classNames) {
            try {
                Class clazz = host.loadCameraClass(className);

                Method[] methods = clazz.getDeclaredMethods();

                for (Method method : methods) {
                    if (!"createImageReader".equals(method.getName())) {
                        continue;
                    }

                    method.setAccessible(true);

                    host.hookMethod(method).intercept(chain -> {
                        List argsList = chain.getArgs();

                        if (argsList == null || argsList.isEmpty()) {
                            return chain.proceed();
                        }

                        Object[] newArgs = argsList.toArray(new Object[0]);

                        boolean changed = patchFinalPictureWrapperDeep(
                                newArgs,
                                Collections.newSetFromMap(new IdentityHashMap<>()),
                                0,
                                "SurfacePool.createImageReader.args"
                        );

                        if (changed) {
                            host.xlog(
                                    Log.ERROR,
                                    "GR_SURFACE_FINAL_V42 proceed patched method="
                                            + method
                                            + " args="
                                            + describeValue(newArgs)
                            );

                            return chain.proceed(newArgs);
                        }

                        return chain.proceed();
                    });

                    host.xlog(Log.ERROR, "hook success: GrModeFix SurfacePool.createImageReader v42 -> " + method);
                }
            } catch (Throwable t) {
                host.xlog(
                        Log.ERROR,
                        "hook GrModeFix SurfacePool.createImageReader v42 failed class="
                                + className
                                + " err="
                                + String.valueOf(t)
                );
            }
        }
    }

    private boolean patchFinalPictureWrapperDeep(
            Object object,
            Set<Object> visited,
            int depth,
            String where
    ) {
        if (object == null) {
            return false;
        }

        if (depth > 4) {
            return false;
        }

        if (visited.contains(object)) {
            return false;
        }

        visited.add(object);

        boolean changed = false;

        if (object instanceof Object[]) {
            Object[] array = (Object[]) object;

            for (Object item : array) {
                changed |= patchFinalPictureWrapperDeep(
                        item,
                        visited,
                        depth + 1,
                        where + "[]"
                );
            }

            return changed;
        }

        if (object instanceof List) {
            List list = (List) object;

            for (Object item : list) {
                changed |= patchFinalPictureWrapperDeep(
                        item,
                        visited,
                        depth + 1,
                        where + ".list"
                );
            }

            return changed;
        }

        changed |= patchFinalPictureWrapperObjectIfNeeded(object, where);

        String className = objectClassName(object);

        if (!shouldScanSurfaceWrapperClass(className)) {
            return changed;
        }

        Class clazz = object.getClass();

        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                try {
                    int modifiers = field.getModifiers();

                    if (Modifier.isStatic(modifiers)) {
                        continue;
                    }

                    field.setAccessible(true);

                    Object child = field.get(object);

                    if (child == null) {
                        continue;
                    }

                    changed |= patchFinalPictureWrapperDeep(
                            child,
                            visited,
                            depth + 1,
                            where + "." + field.getName()
                    );
                } catch (Throwable ignored) {
                }
            }

            clazz = clazz.getSuperclass();
        }

        return changed;
    }

    private boolean patchFinalPictureWrapperObjectIfNeeded(Object object, String where) {
        if (object == null) {
            return false;
        }

        String text = safeToString(object);

        if (!isFinalRearMainPictureWrapperText(text)) {
            return false;
        }

        Size targetFullSize = chooseFullSizeByText(text);
        boolean changed = false;

        Class clazz = object.getClass();

        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                try {
                    int modifiers = field.getModifiers();

                    if (Modifier.isStatic(modifiers)) {
                        continue;
                    }

                    if (field.getType() != Size.class) {
                        continue;
                    }

                    field.setAccessible(true);

                    Object value = field.get(object);

                    if (!(value instanceof Size)) {
                        continue;
                    }

                    Size oldSize = (Size) value;

                    if (oldSize.getWidth() == targetFullSize.getWidth()
                            && oldSize.getHeight() == targetFullSize.getHeight()) {
                        updateCurrentFullSize(targetFullSize, "FinalPictureWrapper keep " + where);
                        continue;
                    }

                    /*
                     * 只改最终拍照 Surface。
                     * 不要改 preview / metadata / 其它中间流。
                     */
                    if (!isFullSize(oldSize) && !isSmall720(oldSize)) {
                        continue;
                    }

                    field.set(object, targetFullSize);
                    changed = true;

                    updateCurrentFullSize(targetFullSize, "FinalPictureWrapper " + where);

                    host.xlog(
                            Log.ERROR,
                            "GR_SURFACE_FINAL_V43 fix Size where="
                                    + where
                                    + " field="
                                    + field.getName()
                                    + " old="
                                    + oldSize
                                    + " new="
                                    + targetFullSize
                    );
                } catch (Throwable t) {
                    host.xlog(Log.ERROR, "GR_SURFACE_FINAL_V43 fix Size failed: " + String.valueOf(t));
                }
            }

            clazz = clazz.getSuperclass();
        }

        return changed;
    }


    private boolean isFinalRearMainPictureWrapperText(String text) {
        if (text == null) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);

        if (!lower.contains("surface_key_picture")) {
            return false;
        }

        if (lower.contains("surface_key_picture_metadata")) {
            return false;
        }

        if (lower.contains("surface_key_picture_dol")) {
            return false;
        }

        if (lower.contains("rear_tele")) {
            return false;
        }

        if (lower.contains("capture_yuv")) {
            return false;
        }

        if (lower.contains("raw16_output")) {
            return false;
        }

        if (!lower.contains("rear_main")) {
            return false;
        }

        boolean format37 =
                lower.contains("format: " + FINAL_PICTURE_FORMAT)
                        || lower.contains("format=" + FINAL_PICTURE_FORMAT)
                        || lower.contains("&" + FINAL_PICTURE_FORMAT + "&");

        if (!format37) {
            return false;
        }

        /*
         * AI 风光高像素/“2 亿”入口实际输出按 50MP 处理时，
         * 不能再把 raw_output(format 37) 的 halSurfaceSize 从 4096x3072
         * 强制改成 8192x6144。日志里的崩溃前错误：
         * offlinechicamera.cpp GetFrameworkBuffers request hal stream buffer failed
         * 就是在 HAL 申请这个被放大的 raw buffer 时触发的。
         *
         * 50MP 输出真正需要的是 capture(format 35) 的 8192x6144，
         * raw_output / raw16 / raw_mfnr 保持原链路的小 HAL 尺寸即可。
         */
        if (isAiSceneryHighPixelWindowActive()) {
            host.xlog(
                    Log.ERROR,
                    "AI_SCENERY_200MP_V27_50MP_SAFE_RAW skip SurfacePool raw format37 full-size patch text="
                            + safeShort(text)
            );
            return false;
        }

        boolean intention3 =
                lower.contains("mintention: 3")
                        || lower.contains("mintention=3")
                        || lower.contains("intention: 3")
                        || lower.contains("intention=3");

        if (lower.contains("intention") && !intention3) {
            return false;
        }

        return lower.contains("appsurfacesize")
                && lower.contains("halsurfacesize");
    }

    private void hookDisableQuickJpegAndFullOutputInAps() {
        String[] classNames = new String[]{
                "com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsCaptureAdapterImpl",
                "com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsCaptureAdapter",
                "com.oplus.ocs.camera.consumer.apsAdapter.adapter.APSClient",
                "com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsClient",
                "com.oplus.ocs.camera.consumer.apsAdapter.APSClient",
                "com.oplus.ocs.camera.consumer.apsAdapter.ApsClient",
                "com.oplus.camera.aps.adapter.ApsCaptureAdapterImpl",
                "com.oplus.camera.aps.adapter.ApsCaptureAdapter",
                "com.oplus.camera.aps.adapter.APSClient",
                "com.oplus.camera.aps.adapter.ApsClient",
                "com.oplus.camera.aps.APSClient",
                "com.oplus.camera.aps.ApsClient",
                "com.oplus.aps.APSClient",
                "com.oplus.aps.ApsClient"
        };

        for (String className : classNames) {
            try {
                Class clazz = host.loadCameraClass(className);

                Method[] methods = clazz.getDeclaredMethods();

                for (Method method : methods) {
                    String lowerName = method.getName().toLowerCase(Locale.ROOT);

                    boolean targetName =
                            lowerName.contains("algoinit")
                                    || lowerName.contains("startcapture")
                                    || lowerName.contains("addframe")
                                    || lowerName.contains("addimage")
                                    || lowerName.contains("addframebuff")
                                    || lowerName.contains("processimage")
                                    || lowerName.contains("updatewatermark")
                                    || lowerName.contains("notifystartcapture")
                                    || lowerName.contains("countaddframe");

                    boolean targetParam = false;

                    Class[] parameterTypes = method.getParameterTypes();

                    if (parameterTypes != null) {
                        for (Class type : parameterTypes) {
                            if (type == null) {
                                continue;
                            }

                            String typeName = type.getName();

                            if (typeName.contains("Aps")
                                    || typeName.contains("APS")
                                    || typeName.contains("ImageCategory")
                                    || typeName.contains("ImageItemInfo")
                                    || typeName.contains("ApsParameters")
                                    || typeName.contains("Parameter")) {
                                targetParam = true;
                                break;
                            }
                        }
                    }

                    if (!targetName && !targetParam) {
                        continue;
                    }

                    method.setAccessible(true);

                    host.hookMethod(method).intercept(chain -> {
                        List argsList = chain.getArgs();

                        if (argsList == null || argsList.isEmpty()) {
                            return chain.proceed();
                        }

                        Object[] newArgs = argsList.toArray(new Object[0]);

                        boolean isAddFrameBuff =
                                method.getName() != null
                                        && method.getName().toLowerCase(Locale.ROOT).contains("addframebuff");

                        boolean changed = patchApsParamsDeep(
                                newArgs,
                                Collections.newSetFromMap(new IdentityHashMap<>()),
                                0,
                                isAddFrameBuff,
                                "APS_ARGS." + method.getName()
                        );

                        if (changed) {
                            host.xlog(
                                    Log.ERROR,
                                    "GR_APS_FULL_V42 proceed patched method="
                                            + method
                                            + " args="
                                            + describeValue(newArgs)
                            );

                            return chain.proceed(newArgs);
                        }

                        return chain.proceed();
                    });

                    host.xlog(Log.ERROR, "hook success: GrModeFix APS full/quick v42 -> " + method);
                }
            } catch (Throwable t) {
                host.xlog(
                        Log.ERROR,
                        "hook GrModeFix APS full/quick v42 failed class="
                                + className
                                + " err="
                                + String.valueOf(t)
                );
            }
        }
    }

    private boolean patchApsParamsDeep(
            Object object,
            Set<Object> visited,
            int depth,
            boolean allowOutputSizePatch,
            String where
    ) {
        if (object == null) {
            return false;
        }

        if (depth > 6) {
            return false;
        }

        if (visited.contains(object)) {
            return false;
        }

        visited.add(object);

        boolean changed = false;

        Class objectClass = object.getClass();

        if (objectClass.isArray()) {
            if (object instanceof Object[]) {
                Object[] array = (Object[]) object;

                changed |= patchApsKeyValueArray(array, allowOutputSizePatch, where);

                for (Object item : array) {
                    changed |= patchApsParamsDeep(
                            item,
                            visited,
                            depth + 1,
                            allowOutputSizePatch,
                            where + "[]"
                    );
                }
            }

            return changed;
        }

        if (object instanceof List) {
            List list = (List) object;

            changed |= patchApsKeyValueList(list, allowOutputSizePatch, where);
            changed |= patchAiSceneryUnsafeAlgoList(list, where);

            for (Object item : list) {
                changed |= patchApsParamsDeep(
                        item,
                        visited,
                        depth + 1,
                        allowOutputSizePatch,
                        where + ".list"
                );
            }

            return changed;
        }

        if (object instanceof Map) {
            Map map = (Map) object;

            changed |= patchApsKeyValueMap(map, allowOutputSizePatch, where);

            try {
                for (Object value : map.values()) {
                    changed |= patchApsParamsDeep(
                            value,
                            visited,
                            depth + 1,
                            allowOutputSizePatch,
                            where + ".mapValue"
                    );
                }
            } catch (Throwable ignored) {
            }

            return changed;
        }

        if (isPrimitiveLike(object)) {
            return false;
        }

        String objectText = safeToString(object);
        if (isAiSceneryHighPixelApsText(objectText)) {
            markAiSceneryHighPixelWindow("APS_OBJECT " + where + " " + safeShort(objectText));
        }

        String className = objectClassName(object);

        if (!shouldScanApsParamClass(className)) {
            return false;
        }

        Class clazz = object.getClass();

        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                try {
                    int modifiers = field.getModifiers();

                    if (Modifier.isStatic(modifiers)) {
                        continue;
                    }

                    field.setAccessible(true);

                    Object child = field.get(object);

                    if (child == null) {
                        continue;
                    }

                    if (child instanceof Object[] && isAiSceneryHighPixelWindowActive()) {
                        Object filteredArray = filterAiSceneryUnsafeAlgoArray(
                                (Object[]) child,
                                field.getType(),
                                where + "." + field.getName()
                        );

                        if (filteredArray != child) {
                            try {
                                field.set(object, filteredArray);
                                child = filteredArray;
                                changed = true;
                            } catch (Throwable t) {
                                host.xlog(Log.ERROR, "AI_SCENERY_200MP_V27_50MP_SAFE_RAW replace unsafe APS algo array failed where="
                                        + where + "." + field.getName() + " err=" + String.valueOf(t));
                            }
                        }
                    }

                    changed |= patchApsParamsDeep(
                            child,
                            visited,
                            depth + 1,
                            allowOutputSizePatch,
                            where + "." + field.getName()
                    );
                } catch (Throwable ignored) {
                }
            }

            clazz = clazz.getSuperclass();
        }

        return changed;
    }

    private boolean patchApsKeyValueArray(Object[] array, boolean allowOutputSizePatch, String where) {
        if (array == null || array.length < 2) {
            return false;
        }

        if (!shouldPatchApsSequence(array)) {
            return false;
        }

        boolean changed = false;

        for (int i = 0; i < array.length - 1; i++) {
            Object keyObject = array[i];

            if (!(keyObject instanceof String)) {
                continue;
            }

            String key = ((String) keyObject).toLowerCase(Locale.ROOT);

            Object oldValue = array[i + 1];
            Object newValue = patchApsValue(key, oldValue, allowOutputSizePatch);

            if (!isSameValue(oldValue, newValue)) {
                array[i + 1] = newValue;
                changed = true;

                host.xlog(
                        Log.ERROR,
                        "GR_APS_FULL_V42 fix array where="
                                + where
                                + " key="
                                + key
                                + " old="
                                + describeValue(oldValue)
                                + " new="
                                + describeValue(newValue)
                );
            }
        }

        return changed;
    }

    private boolean patchApsKeyValueList(List list, boolean allowOutputSizePatch, String where) {
        if (list == null || list.size() < 2) {
            return false;
        }

        Object[] array = list.toArray(new Object[0]);

        if (!shouldPatchApsSequence(array)) {
            return false;
        }

        boolean changed = false;

        for (int i = 0; i < list.size() - 1; i++) {
            Object keyObject = list.get(i);

            if (!(keyObject instanceof String)) {
                continue;
            }

            String key = ((String) keyObject).toLowerCase(Locale.ROOT);

            Object oldValue = list.get(i + 1);
            Object newValue = patchApsValue(key, oldValue, allowOutputSizePatch);

            if (!isSameValue(oldValue, newValue)) {
                try {
                    list.set(i + 1, newValue);
                    changed = true;

                    host.xlog(
                            Log.ERROR,
                            "GR_APS_FULL_V42 fix list where="
                                    + where
                                    + " key="
                                    + key
                                    + " old="
                                    + describeValue(oldValue)
                                    + " new="
                                    + describeValue(newValue)
                    );
                } catch (Throwable t) {
                    host.xlog(Log.ERROR, "GR_APS_FULL_V42 fix list failed: " + String.valueOf(t));
                }
            }
        }

        return changed;
    }

    private boolean patchApsKeyValueMap(Map map, boolean allowOutputSizePatch, String where) {
        if (map == null || map.isEmpty()) {
            return false;
        }

        if (!shouldPatchApsMap(map)) {
            return false;
        }

        boolean changed = false;

        try {
            List keys = new ArrayList(map.keySet());

            for (Object keyObject : keys) {
                if (!(keyObject instanceof String)) {
                    continue;
                }

                String key = ((String) keyObject).toLowerCase(Locale.ROOT);

                Object oldValue = map.get(keyObject);
                Object newValue = patchApsValue(key, oldValue, allowOutputSizePatch);

                if (!isSameValue(oldValue, newValue)) {
                    map.put(keyObject, newValue);
                    changed = true;

                    host.xlog(
                            Log.ERROR,
                            "GR_APS_FULL_V42 fix map where="
                                    + where
                                    + " key="
                                    + key
                                    + " old="
                                    + describeValue(oldValue)
                                    + " new="
                                    + describeValue(newValue)
                    );
                }
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "GR_APS_FULL_V42 fix map failed: " + String.valueOf(t));
        }

        return changed;
    }

    private boolean shouldPatchApsSequence(Object[] array) {
        if (array == null || array.length < 2) {
            return false;
        }

        boolean hasTargetKey = false;
        boolean hasGrSignal = false;
        boolean hasCaptureSignal = false;

        for (Object item : array) {
            if (item == null) {
                continue;
            }

            String text = String.valueOf(item).toLowerCase(Locale.ROOT);

            if ("quick_jpeg".equals(text)
                    || "quick_jpeg_version".equals(text)
                    || "quick_jpeg_watermark_process_in_aps".equals(text)
                    || "output_width".equals(text)
                    || "output_height".equals(text)
                    || "crop_width".equals(text)
                    || "crop_height".equals(text)
                    || "face_beauty_version".equals(text)
                    || "face_beauty_type".equals(text)
                    || "group_photo_support".equals(text)
                    || "group_photo_on_off".equals(text)
                    || "ai_rectify_enable".equals(text)
                    || "face_rectify_enable".equals(text)
                    || "custom_beauty_param".equals(text)) {
                hasTargetKey = true;
            }

            if (text.contains("gr_mode")
                    || text.contains("ai_scenery_mode")
                    || text.contains("ai.scenery")
                    || text.contains("sat_street_case")
                    || text.contains("aiscenery")
                    || text.contains("ricoh")
                    || text.contains("master_mode_vignette")
                    || text.contains("custom_focal_length")
                    || text.contains("gr_mode_particle")
                    || text.contains("gr_mode_vignette")) {
                hasGrSignal = true;
            }

            if ("picture_title".equals(text)
                    || text.contains("pipeline_capture")
                    || text.contains("capture")
                    || text.contains("jpeg")) {
                hasCaptureSignal = true;
            }
        }

        if (!hasTargetKey) {
            return false;
        }

        return hasGrSignal || (hasCaptureSignal && isGrPatchWindowActive());
    }

    private boolean shouldPatchApsMap(Map map) {
        if (map == null || map.isEmpty()) {
            return false;
        }

        boolean hasTargetKey = false;
        boolean hasGrSignal = false;
        boolean hasCaptureSignal = false;

        try {
            for (Object keyObject : map.keySet()) {
                if (keyObject == null) {
                    continue;
                }

                String key = String.valueOf(keyObject).toLowerCase(Locale.ROOT);
                Object value = map.get(keyObject);
                String valueText = String.valueOf(value).toLowerCase(Locale.ROOT);

                if ("quick_jpeg".equals(key)
                        || "quick_jpeg_version".equals(key)
                        || "quick_jpeg_watermark_process_in_aps".equals(key)
                        || "output_width".equals(key)
                        || "output_height".equals(key)
                        || "crop_width".equals(key)
                        || "crop_height".equals(key)
                        || "face_beauty_version".equals(key)
                        || "face_beauty_type".equals(key)
                        || "group_photo_support".equals(key)
                        || "group_photo_on_off".equals(key)
                        || "ai_rectify_enable".equals(key)
                        || "face_rectify_enable".equals(key)
                        || "custom_beauty_param".equals(key)) {
                    hasTargetKey = true;
                }

                if (key.contains("gr_mode")
                        || key.contains("ai_scenery_mode")
                        || key.contains("ai.scenery")
                        || key.contains("sat_street_case")
                        || key.contains("aiscenery")
                        || key.contains("ricoh")
                        || key.contains("master_mode_vignette")
                        || key.contains("custom_focal_length")
                        || key.contains("gr_mode_particle")
                        || key.contains("gr_mode_vignette")
                        || valueText.contains("gr_mode")
                        || valueText.contains("ai_scenery_mode")
                        || valueText.contains("ai.scenery")
                        || valueText.contains("sat_street_case")
                        || valueText.contains("aiscenery")
                        || valueText.contains("ricoh")) {
                    hasGrSignal = true;
                }

                if ("picture_title".equals(key)
                        || key.contains("capture")
                        || key.contains("jpeg")
                        || valueText.contains("pipeline_capture")) {
                    hasCaptureSignal = true;
                }
            }
        } catch (Throwable ignored) {
        }

        if (!hasTargetKey) {
            return false;
        }

        return hasGrSignal || (hasCaptureSignal && isGrPatchWindowActive());
    }

    private Object patchApsValue(String key, Object oldValue, boolean allowOutputSizePatch) {
        if (key == null) {
            return oldValue;
        }

        if ("quick_jpeg".equals(key)) {
            return castLikeOldValue(oldValue, false);
        }

        if ("quick_jpeg_version".equals(key)) {
            return castLikeOldValue(oldValue, 0);
        }

        if ("quick_jpeg_watermark_process_in_aps".equals(key)) {
            return castLikeOldValue(oldValue, false);
        }

        if (isAiSceneryHighPixelWindowActive()) {
            if ("face_beauty_version".equals(key)
                    || "face_beauty_type".equals(key)
                    || "group_photo_support".equals(key)
                    || "group_photo_on_off".equals(key)
                    || "ai_rectify_enable".equals(key)
                    || "face_rectify_enable".equals(key)) {
                return castLikeOldValue(oldValue, 0);
            }

            if ("custom_beauty_param".equals(key)) {
                return zeroCustomBeautyParam(oldValue);
            }
        }

        if (!allowOutputSizePatch) {
            return oldValue;
        }

        /*
         * 普通 GR/Vibe/AI 风光使用 12MP photo 链路时，APS 输出尺寸跟随 currentFullWidth/currentFullHeight。
         *
         * AI 风光 2 亿入口在当前设备实际只有 8192x6144 / 6144x8192。
         * 这里强制保持真实输入尺寸，让最终保存为 50MP；不再写 200MP 超分尺寸。
         */
        if ("output_width".equals(key) || "crop_width".equals(key)) {
            if (isAiSceneryHighPixelWindowActive()) {
                logAiSceneryKeepInputSize(key, oldValue, true);
                return oldValue;
            }

            return castLikeOldValue(oldValue, currentFullWidth);
        }

        if ("output_height".equals(key) || "crop_height".equals(key)) {
            if (isAiSceneryHighPixelWindowActive()) {
                logAiSceneryKeepInputSize(key, oldValue, false);
                return oldValue;
            }

            return castLikeOldValue(oldValue, currentFullHeight);
        }

        return oldValue;
    }

    private boolean isAiSceneryHighPixelApsText(String text) {
        if (text == null) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);

        boolean highPixelSignal = lower.contains("high_pic_size_enable, 1")
                || lower.contains("high_pic_size_enable=1")
                || lower.contains("high_pixel_200mp_enable, 1")
                || lower.contains("high_pixel_200mp_enable=1")
                || lower.contains("ai_scenery_high_pixel, 200")
                || lower.contains("ai_scenery_high_pixel=200")
                || lower.contains("mpicturesize: 8192x6144")
                || lower.contains("mpicturesize=8192x6144")
                || lower.contains("mpicturesize: 6144x8192")
                || lower.contains("mpicturesize=6144x8192");

        if (!highPixelSignal) {
            return false;
        }

        boolean cameraSignal = lower.contains("ai_scenery")
                || lower.contains("aiscenery")
                || lower.contains("operation_mode, 32769")
                || lower.contains("operation_mode=32769")
                || lower.contains("moperationmode='8001'")
                || lower.contains("moperationmode: 8001")
                || lower.contains("logic_camera_id, 3")
                || lower.contains("logic_camera_id=3");

        return cameraSignal || isGrPatchWindowActive();
    }

    private Object filterAiSceneryUnsafeAlgoArray(Object[] array, Class arrayType, String where) {
        if (array == null || array.length == 0) {
            return array;
        }

        if (!isAiSceneryHighPixelWindowActive()) {
            return array;
        }

        int keepCount = 0;
        boolean changed = false;

        for (Object item : array) {
            if (item instanceof String && isAiSceneryUnsafeAlgo(((String) item).toLowerCase(Locale.ROOT))) {
                changed = true;
                host.xlog(
                        Log.ERROR,
                        "AI_SCENERY_200MP_V27_50MP_SAFE_RAW remove unsafe APS algo array where="
                                + where
                                + " value="
                                + describeValue(item)
                );
                continue;
            }

            keepCount++;
        }

        if (!changed) {
            return array;
        }

        try {
            Class componentType = String.class;

            if (arrayType != null && arrayType.isArray() && arrayType.getComponentType() != null) {
                componentType = arrayType.getComponentType();
            }

            Object newArray = java.lang.reflect.Array.newInstance(componentType, keepCount);
            int out = 0;

            for (Object item : array) {
                if (item instanceof String && isAiSceneryUnsafeAlgo(((String) item).toLowerCase(Locale.ROOT))) {
                    continue;
                }

                java.lang.reflect.Array.set(newArray, out, item);
                out++;
            }

            return newArray;
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "AI_SCENERY_200MP_V27_50MP_SAFE_RAW build filtered APS algo array failed where="
                    + where + " err=" + String.valueOf(t));
            return array;
        }
    }

    private boolean patchAiSceneryUnsafeAlgoList(List list, String where) {
        if (list == null || list.isEmpty()) {
            return false;
        }

        if (!isAiSceneryHighPixelWindowActive()) {
            return false;
        }

        boolean changed = false;

        for (int i = list.size() - 1; i >= 0; i--) {
            Object item = list.get(i);

            if (!(item instanceof String)) {
                continue;
            }

            String value = ((String) item).toLowerCase(Locale.ROOT);

            if (!isAiSceneryUnsafeAlgo(value)) {
                continue;
            }

            try {
                Object removed = list.remove(i);
                changed = true;

                host.xlog(
                        Log.ERROR,
                        "AI_SCENERY_200MP_V27_50MP_SAFE_RAW remove unsafe APS algo where="
                                + where
                                + " value="
                                + describeValue(removed)
                );
            } catch (Throwable t) {
                host.xlog(Log.ERROR, "AI_SCENERY_200MP_V27_50MP_SAFE_RAW remove APS algo failed: " + String.valueOf(t));
            }
        }

        return changed;
    }

    private boolean isAiSceneryUnsafeAlgo(String value) {
        if (value == null) {
            return false;
        }

        return value.contains("aps_algo_face_info")
                || value.contains("aps_algo_face_beauty")
                || value.contains("aps_algo_face_rectify")
                || value.contains("aps_algo_mask_refine")
                || value.contains("aps_algo_facebase_retouch")
                || value.contains("aps_algo_portraitrepair");
    }

    private void logAiSceneryKeepInputSize(String key, Object oldValue, boolean widthKey) {
        int value = toInt(oldValue, -1);

        boolean highPixelInputSize = value == AI_SCENERY_HP_INPUT_LANDSCAPE_WIDTH
                || value == AI_SCENERY_HP_INPUT_LANDSCAPE_HEIGHT
                || value == AI_SCENERY_HP_INPUT_PORTRAIT_WIDTH
                || value == AI_SCENERY_HP_INPUT_PORTRAIT_HEIGHT;

        if (!highPixelInputSize) {
            return;
        }

        host.xlog(
                Log.ERROR,
                "AI_SCENERY_200MP_V27_50MP_SAFE_RAW keep real APS "
                        + (widthKey ? "width" : "height")
                        + " key="
                        + key
                        + " value="
                        + describeValue(oldValue)
                        + " reason=no-native-sr-buffer"
        );
    }

    private Object zeroCustomBeautyParam(Object oldValue) {
        if (oldValue instanceof int[]) {
            int[] oldArray = (int[]) oldValue;
            return new int[oldArray.length];
        }

        if (oldValue instanceof long[]) {
            long[] oldArray = (long[]) oldValue;
            return new long[oldArray.length];
        }

        if (oldValue instanceof float[]) {
            float[] oldArray = (float[]) oldValue;
            return new float[oldArray.length];
        }

        if (oldValue instanceof double[]) {
            double[] oldArray = (double[]) oldValue;
            return new double[oldArray.length];
        }

        if (oldValue instanceof Object[]) {
            Object[] oldArray = (Object[]) oldValue;
            Object[] newArray = new Object[oldArray.length];
            for (int i = 0; i < newArray.length; i++) {
                Object oldItem = oldArray[i];
                if (oldItem instanceof Long) {
                    newArray[i] = 0L;
                } else if (oldItem instanceof Float) {
                    newArray[i] = 0f;
                } else if (oldItem instanceof Double) {
                    newArray[i] = 0d;
                } else if (oldItem instanceof String) {
                    newArray[i] = "0";
                } else {
                    newArray[i] = 0;
                }
            }
            return newArray;
        }

        if (oldValue instanceof String) {
            return "0,0,0,0,0,0,0,0,0,0,0,0,0,0";
        }

        return oldValue;
    }


    private void hookFaceBeautyPhotoChainModeFallback() {
        String[] classNames = new String[]{
                "kb.g",
                "com.oplus.camera.feature.beauty.model.FaceBeautyModel"
        };

        for (String className : classNames) {
            try {
                Class clazz = host.loadCameraClass(className);
                Method[] methods = clazz.getDeclaredMethods();

                for (Method method : methods) {
                    if (Modifier.isAbstract(method.getModifiers())) {
                        continue;
                    }

                    method.setAccessible(true);

                    host.hookMethod(method).intercept(chain -> {
                        List argsList = chain.getArgs();
                        Object[] newArgs = null;
                        boolean changed = false;

                        if (argsList != null && !argsList.isEmpty()) {
                            newArgs = argsList.toArray(new Object[0]);
                            changed = replaceShortModeInFaceBeautyArgs(
                                    newArgs,
                                    "FaceBeautyModel." + method.getName()
                            );
                        }

                        try {
                            if (changed) {
                                return chain.proceed(newArgs);
                            }
                            return chain.proceed();
                        } catch (Throwable t) {
                            if (isFaceBeautyPhotoChainModeCrash(t)) {
                                host.xlog(
                                        Log.ERROR,
                                        "VIBE_GR_PHOTO_CHAIN_V46 suppress FaceBeauty mode crash method="
                                                + method
                                                + " err="
                                                + String.valueOf(t)
                                );
                                return defaultReturnValue(method.getReturnType());
                            }
                            throw t;
                        }
                    });
                }

                host.xlog(Log.ERROR, "hook success: GrModeFix FaceBeautyModel photo-chain fallback v46 -> " + className);
            } catch (Throwable t) {
                host.xlog(
                        Log.ERROR,
                        "hook GrModeFix FaceBeautyModel photo-chain fallback v46 failed class="
                                + className
                                + " err="
                                + String.valueOf(t)
                );
            }
        }
    }

    private boolean replaceShortModeInFaceBeautyArgs(Object[] args, String where) {
        if (args == null || args.length == 0) {
            return false;
        }

        boolean changed = false;

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];

            if (arg instanceof String) {
                String oldValue = (String) arg;
                String newValue = fixFaceBeautyModeOnly(oldValue);

                if (!oldValue.equals(newValue)) {
                    args[i] = newValue;
                    changed = true;

                    host.xlog(
                            Log.ERROR,
                            "VIBE_GR_PHOTO_CHAIN_V46 replace FaceBeauty arg where="
                                    + where
                                    + " index="
                                    + i
                                    + " old="
                                    + oldValue
                                    + " new="
                                    + newValue
                    );
                }
            }
        }

        return changed;
    }

    private String fixFaceBeautyModeOnly(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }

        if (GR_SHORT_MODE_NAME.equals(value)
                || VIBE_SHORT_MODE_NAME.equals(value)
                || AI_SCENERY_SHORT_MODE_NAME.equals(value)
                || "ai_scenery".equals(value)
                || "aiScenery".equals(value)) {
            return COMMON_MODE_NAME;
        }

        if (GR_MODE_NAME.equals(value) || VIBE_MODE_NAME.equals(value) || AI_SCENERY_MODE_NAME.equals(value)) {
            return PHOTO_MODE_NAME;
        }

        String fixed = value;
        fixed = fixed.replace(GR_CASE_NAME, GR_FALLBACK_CASE_NAME);
        fixed = fixed.replace(VIBE_CASE_NAME, GR_FALLBACK_CASE_NAME);
        fixed = fixed.replace(AI_SCENERY_CASE_NAME, GR_FALLBACK_CASE_NAME);
        fixed = fixed.replace(AI_SCENERY_ALT_CASE_NAME, GR_FALLBACK_CASE_NAME);
        fixed = fixed.replace(VIBE_MODE_NAME, PHOTO_MODE_NAME);
        fixed = fixed.replace(AI_SCENERY_MODE_NAME, PHOTO_MODE_NAME);
        return fixed;
    }

    private boolean isFaceBeautyPhotoChainModeCrash(Throwable t) {
        if (t == null) {
            return false;
        }

        Throwable current = t;
        while (current != null) {
            String message = String.valueOf(current.getMessage());
            if (message.contains("FaceBeautyKeys")
                    && (message.contains("mode: vibe") || message.contains("mode: gr")
                    || message.contains("mode: aiScenery") || message.contains("mode: ai_scenery"))) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private Object defaultReturnValue(Class returnType) {
        if (returnType == null || returnType == Void.TYPE) {
            return null;
        }

        if (!returnType.isPrimitive()) {
            return null;
        }

        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0f;
        }
        if (returnType == Double.TYPE) {
            return 0d;
        }
        if (returnType == Character.TYPE) {
            return (char) 0;
        }

        return null;
    }

    private void hookCamera2ImplCreateNewSessionDelayRestore() {
        String className = "com.oplus.ocs.camera.producer.device.Camera2Impl";

        try {
            Class clazz = host.loadCameraClass(className);

            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                if (!"createNewSession".equals(method.getName())) {
                    continue;
                }

                method.setAccessible(true);

                host.hookMethod(method).intercept(chain -> {
                    List argsList = chain.getArgs();

                    if (argsList == null || argsList.isEmpty()) {
                        return chain.proceed();
                    }

                    Object[] newArgs = argsList.toArray(new Object[0]);

                    boolean changed = false;
                    boolean isGrSession = false;
                    boolean isVibeSession = false;
                    boolean isAiScenerySession = false;
                    boolean isMasterSession = false;
                    List<RestoreRecord> restoreRecords = new ArrayList<>();

                    for (Object arg : newArgs) {
                        if (isMasterRelatedObject(arg)) {
                            isMasterSession = true;
                        }

                        boolean grRelated = isGrRelatedObject(arg);
                        boolean vibeRelated = isVibeRelatedObject(arg);
                        boolean aiSceneryRelated = isAiSceneryRelatedObject(arg);

                        if (!grRelated && !vibeRelated && !aiSceneryRelated) {
                            continue;
                        }

                        if (grRelated) {
                            isGrSession = true;
                        }

                        if (vibeRelated) {
                            isVibeSession = true;
                        }

                        if (aiSceneryRelated) {
                            isAiScenerySession = true;
                        }

                        host.xlog(
                                Log.ERROR,
                                "GR_DELAY_V47 before photo-chain gr="
                                        + grRelated
                                        + " vibe="
                                        + vibeRelated
                                        + " aiScenery="
                                        + aiSceneryRelated
                                        + " argClass="
                                        + objectClassName(arg)
                                        + " text="
                                        + safeToString(arg)
                        );

                        String argText = safeToString(arg);

                        if (aiSceneryRelated && isAiSceneryHighPixelText(argText)) {
                            markAiSceneryHighPixelWindow("Camera2Impl.createNewSession " + safeShort(argText));
                        }

                        updateCurrentFullSize(
                                chooseFullSizeByText(argText),
                                "Camera2Impl.createNewSession photo-chain entity"
                        );

                        changed |= fixCameraSessionEntityDelayRestore(
                                arg,
                                restoreRecords
                        );

                        host.xlog(
                                Log.ERROR,
                                "GR_DELAY_V47 after changed="
                                        + changed
                                        + " text="
                                        + safeToString(arg)
                        );
                    }

                    if (isMasterSession) {

                    } else if (isGrSession || isVibeSession || isAiScenerySession) {
                        markGrPatchWindow();

                    }

                    if (!restoreRecords.isEmpty()) {
                        scheduleDelayedRestore(restoreRecords);
                    } else {
                        host.xlog(Log.ERROR, "GR_DELAY_V44 no restore records");
                    }

                    if (isGrSession || isVibeSession || isAiScenerySession) {
                        grCreateSessionActive.set(true);
                    }

                    try {
                        if (changed) {
                            return chain.proceed(newArgs);
                        }

                        return chain.proceed();
                    } finally {
                        if (isGrSession || isVibeSession || isAiScenerySession) {
                            grCreateSessionActive.set(false);
                        }
                    }
                });

                host.xlog(Log.ERROR, "hook success: GrModeFix Camera2Impl.createNewSession v42 -> " + method);
            }
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "hook GrModeFix Camera2Impl.createNewSession v42 failed: " + String.valueOf(t));
        }
    }

    private boolean fixCameraSessionEntityDelayRestore(
            Object object,
            List<RestoreRecord> restoreRecords
    ) {
        if (object == null) {
            return false;
        }

        String className = objectClassName(object);

        if (className == null || !className.contains("CameraSessionEntity")) {
            return false;
        }

        boolean changed = false;

        Class clazz = object.getClass();

        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            try {
                int modifiers = field.getModifiers();

                if (Modifier.isStatic(modifiers)) {
                    continue;
                }

                field.setAccessible(true);

                String fieldName = field.getName();

                if ("mOperationMode".equals(fieldName) && field.getType() == String.class) {
                    Object value = field.get(object);

                    if (value instanceof String) {
                        String oldValue = (String) value;
                        String newValue = replaceOperationHexOnly(oldValue);

                        if (!oldValue.equals(newValue)) {
                            field.set(object, newValue);
                            changed = true;

                            host.xlog(
                                    Log.ERROR,
                                    "GR_DELAY_V42 fix operation field="
                                            + fieldName
                                            + " old="
                                            + oldValue
                                            + " new="
                                            + newValue
                            );
                        }
                    }
                }

                if (field.getType() == String.class) {
                    Object value = field.get(object);

                    if (value instanceof String) {
                        String oldValue = (String) value;
                        String newValue = fixPhotoChainTextOnly(oldValue);

                        if (!oldValue.equals(newValue)) {
                            field.set(object, newValue);
                            changed = true;

                            host.xlog(
                                    Log.ERROR,
                                    "GR_DELAY_V42 fix string field="
                                            + fieldName
                                            + " old="
                                            + oldValue
                                            + " new="
                                            + newValue
                            );
                        }
                    }
                }

                if ("mSurfaceControl".equals(fieldName)) {
                    Object child = field.get(object);
                    changed |= fixSurfaceControlCurrentOperationOnly(child);
                }

                if ("mApsTag".equals(fieldName)) {
                    Object child = field.get(object);
                    changed |= fixModeNameWithRestore(
                            child,
                            restoreRecords,
                            "ApsRequestTag"
                    );
                }

                if ("mConfig".equals(fieldName)) {
                    Object child = field.get(object);
                    changed |= fixModeNameWithRestore(
                            child,
                            restoreRecords,
                            "SdkCameraDeviceConfig"
                    );
                }
            } catch (Throwable ignored) {
            }
        }

        return changed;
    }

    private boolean fixModeNameWithRestore(
            Object object,
            List<RestoreRecord> restoreRecords,
            String label
    ) {
        if (object == null) {
            return false;
        }

        boolean changed = false;

        Class clazz = object.getClass();

        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                try {
                    int modifiers = field.getModifiers();

                    if (Modifier.isStatic(modifiers)) {
                        continue;
                    }

                    if (field.getType() != String.class) {
                        continue;
                    }

                    if (!"mModeName".equals(field.getName())) {
                        continue;
                    }

                    field.setAccessible(true);

                    Object value = field.get(object);

                    if (!(value instanceof String)) {
                        continue;
                    }

                    String oldValue = (String) value;

                    String mappedValue = mapPhotoChainModeName(oldValue);

                    if (oldValue.equals(mappedValue)) {
                        continue;
                    }

                    restoreRecords.add(new RestoreRecord(object, field, oldValue, label));

                    field.set(object, mappedValue);
                    changed = true;

                    host.xlog(
                            Log.ERROR,
                            "GR_DELAY_V45 set modeName TEMP label="
                                    + label
                                    + " class="
                                    + clazz.getName()
                                    + " old="
                                    + oldValue
                                    + " new="
                                    + mappedValue
                    );
                } catch (Throwable ignored) {
                }
            }

            clazz = clazz.getSuperclass();
        }

        return changed;
    }

    private void scheduleDelayedRestore(List<RestoreRecord> records) {
        final List<RestoreRecord> copied = new ArrayList<>(records);

        Thread thread = new Thread(() -> {
            try {
                host.xlog(
                        Log.ERROR,
                        "GR_DELAY_V42 restore scheduled delayMs="
                                + MODE_NAME_RESTORE_DELAY_MS
                                + " count="
                                + copied.size()
                );

                Thread.sleep(MODE_NAME_RESTORE_DELAY_MS);

                restoreChangedFields(copied);
            } catch (Throwable t) {
                host.xlog(Log.ERROR, "GR_DELAY_V42 restore thread failed: " + String.valueOf(t));
            }
        }, "LumoGrDelayRestore");

        try {
            thread.start();
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "GR_DELAY_V42 restore thread start failed: " + String.valueOf(t));
        }
    }

    private void restoreChangedFields(List<RestoreRecord> records) {
        if (records == null || records.isEmpty()) {
            host.xlog(Log.ERROR, "GR_DELAY_V42 restore no records");
            return;
        }

        for (RestoreRecord record : records) {
            if (record == null || record.object == null || record.field == null) {
                continue;
            }

            try {
                record.field.setAccessible(true);

                Object current = record.field.get(record.object);

                if (current instanceof String
                        && (PHOTO_MODE_NAME.equals(current) || COMMON_MODE_NAME.equals(current))) {
                    record.field.set(record.object, record.oldValue);

                    host.xlog(
                            Log.ERROR,
                            "GR_DELAY_V42 restore field label="
                                    + record.label
                                    + " class="
                                    + objectClassName(record.object)
                                    + " field="
                                    + record.field.getName()
                                    + " restored="
                                    + describeValue(record.oldValue)
                    );
                }
            } catch (Throwable t) {
                host.xlog(Log.ERROR, "GR_DELAY_V42 restore failed: " + String.valueOf(t));
            }
        }
    }

    private boolean fixSurfaceControlCurrentOperationOnly(Object object) {
        if (object == null) {
            return false;
        }

        boolean changed = false;

        Class clazz = object.getClass();

        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                try {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }

                    if (field.getType() != String.class) {
                        continue;
                    }

                    if (!"mCurrentOperationMode".equals(field.getName())) {
                        continue;
                    }

                    field.setAccessible(true);

                    Object value = field.get(object);

                    if (!(value instanceof String)) {
                        continue;
                    }

                    String oldValue = (String) value;
                    String newValue = replaceOperationHexOnly(oldValue);

                    if (!oldValue.equals(newValue)) {
                        field.set(object, newValue);
                        changed = true;

                        host.xlog(
                                Log.ERROR,
                                "GR_DELAY_V42 fix surface operation old="
                                        + oldValue
                                        + " new="
                                        + newValue
                        );
                    }
                } catch (Throwable ignored) {
                }
            }

            clazz = clazz.getSuperclass();
        }

        return changed;
    }

    private boolean replaceGrCaseInArgs(Object[] args, String where) {
        if (args == null || args.length == 0) {
            return false;
        }

        boolean changed = false;

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];

            if (arg instanceof String) {
                String oldValue = (String) arg;
                String newValue = fixPhotoChainTextOnly(oldValue);

                if (!oldValue.equals(newValue)) {
                    args[i] = newValue;
                    changed = true;

                    host.xlog(
                            Log.ERROR,
                            "GR_ARG_V42 replace where="
                                    + where
                                    + " index="
                                    + i
                                    + " old="
                                    + oldValue
                                    + " new="
                                    + newValue
                    );
                }
            }
        }

        return changed;
    }

    private String fixPhotoChainTextOnly(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }

        String fixed = fixPhotoChainCaseOnly(value);

        fixed = fixed.replace(VIBE_MODE_NAME, PHOTO_MODE_NAME);
        fixed = fixed.replace(AI_SCENERY_MODE_NAME, PHOTO_MODE_NAME);

        if (VIBE_SHORT_MODE_NAME.equals(fixed)
                || AI_SCENERY_SHORT_MODE_NAME.equals(fixed)
                || "ai_scenery".equals(fixed)
                || "aiScenery".equals(fixed)) {
            return COMMON_MODE_NAME;
        }

        return fixed;
    }

    private String mapPhotoChainModeName(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }

        if (GR_MODE_NAME.equals(value) || VIBE_MODE_NAME.equals(value) || AI_SCENERY_MODE_NAME.equals(value)) {
            return PHOTO_MODE_NAME;
        }

        if (VIBE_SHORT_MODE_NAME.equals(value)
                || AI_SCENERY_SHORT_MODE_NAME.equals(value)
                || "ai_scenery".equals(value)
                || "aiScenery".equals(value)) {
            return COMMON_MODE_NAME;
        }

        return value;
    }

    private String fixPhotoChainCaseOnly(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }

        String fixed = value;
        fixed = fixed.replace(GR_CASE_NAME, GR_FALLBACK_CASE_NAME);
        fixed = fixed.replace(VIBE_CASE_NAME, GR_FALLBACK_CASE_NAME);
        fixed = fixed.replace(AI_SCENERY_CASE_NAME, GR_FALLBACK_CASE_NAME);
        fixed = fixed.replace(AI_SCENERY_ALT_CASE_NAME, GR_FALLBACK_CASE_NAME);
        return fixed;
    }

    private String replaceOperationHexOnly(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }

        String fixed = value;

        fixed = fixed.replace(GR_BAD_OPERATION_HEX_UPPER, GR_FALLBACK_OPERATION_HEX);
        fixed = fixed.replace(GR_BAD_OPERATION_HEX_LOWER, GR_FALLBACK_OPERATION_HEX);

        return fixed;
    }

    private boolean isGrRelatedObject(Object object) {
        if (object == null) {
            return false;
        }

        String className = object.getClass().getName();

        if (className != null) {
            String lowerClass = className.toLowerCase(Locale.ROOT);

            if (lowerClass.contains("grcapmode")
                    || lowerClass.contains(".gr.")
                    || lowerClass.contains("ricoh")) {
                return true;
            }
        }

        String text = safeToString(object);

        if (text == null) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);

        return lower.contains("grcapmode")
                || lower.contains("gr_mode")
                || lower.contains("gr_mode_case")
                || lower.contains("ricoh")
                || lower.contains("moperationmode='80be'")
                || lower.contains("moperationmode: 80be")
                || lower.contains("moperationmode='80BE'")
                || lower.contains("moperationmode: 80BE");
    }

    private boolean isVibeRelatedObject(Object object) {
        if (object == null) {
            return false;
        }

        String className = object.getClass().getName();

        if (className != null) {
            String lowerClass = className.toLowerCase(Locale.ROOT);

            if (lowerClass.contains("vibecapmode")
                    || lowerClass.contains("vibe")) {
                return true;
            }
        }

        String text = safeToString(object);

        if (text == null) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);

        return lower.contains("vibecapmode")
                || lower.contains(VIBE_MODE_NAME)
                || lower.contains(VIBE_CASE_NAME)
                || lower.contains("mode: vibe")
                || lower.contains("modename：vibe")
                || lower.contains("modename: vibe")
                || lower.contains("vibe_photo");
    }

    private boolean isAiSceneryRelatedObject(Object object) {
        if (object == null) {
            return false;
        }

        String className = object.getClass().getName();

        if (className != null) {
            String lowerClass = className.toLowerCase(Locale.ROOT);

            if (lowerClass.contains("aiscenery")
                    || lowerClass.contains("ai_scenery")
                    || lowerClass.contains("scenery")) {
                return true;
            }
        }

        String text = safeToString(object);

        if (text == null) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);

        return lower.contains(AI_SCENERY_MODE_NAME)
                || lower.contains(AI_SCENERY_CASE_NAME)
                || lower.contains(AI_SCENERY_ALT_CASE_NAME)
                || lower.contains("aiscenery")
                || lower.contains("ai_scenery")
                || lower.contains("ai scenery")
                || lower.contains("ai.scenery")
                || lower.contains("sat_street_case");
    }

    private boolean isMasterRelatedObject(Object object) {
        if (object == null) {
            return false;
        }

        String text = safeToString(object);

        if (text == null) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);

        return lower.contains("professional")
                || lower.contains("professional_mode")
                || lower.contains("master")
                || lower.contains("master_mode")
                || lower.contains("moperationmode='8009'")
                || lower.contains("moperationmode: 8009");
    }

    private boolean containsText(Object object, String keyword) {
        if (object == null || keyword == null) {
            return false;
        }

        if (object instanceof Object[]) {
            Object[] array = (Object[]) object;

            for (Object item : array) {
                if (containsText(item, keyword)) {
                    return true;
                }
            }

            return false;
        }

        String text = safeToString(object);

        return text != null && text.contains(keyword);
    }

    private Object getPairFirst(Object pair) {
        if (pair == null) {
            return null;
        }

        if (pair instanceof Pair) {
            return ((Pair) pair).first;
        }

        try {
            Field field = pair.getClass().getDeclaredField("first");
            field.setAccessible(true);
            return field.get(pair);
        } catch (Throwable ignored) {
        }

        try {
            Field field = pair.getClass().getDeclaredField("mFirst");
            field.setAccessible(true);
            return field.get(pair);
        } catch (Throwable ignored) {
        }

        return null;
    }

    private Object getPairSecond(Object pair) {
        if (pair == null) {
            return null;
        }

        if (pair instanceof Pair) {
            return ((Pair) pair).second;
        }

        try {
            Field field = pair.getClass().getDeclaredField("second");
            field.setAccessible(true);
            return field.get(pair);
        } catch (Throwable ignored) {
        }

        try {
            Field field = pair.getClass().getDeclaredField("mSecond");
            field.setAccessible(true);
            return field.get(pair);
        } catch (Throwable ignored) {
        }

        return null;
    }

    private Object castLikeOldValue(Object oldValue, int newValue) {
        if (oldValue instanceof Integer) {
            return newValue;
        }

        if (oldValue instanceof Long) {
            return (long) newValue;
        }

        if (oldValue instanceof Float) {
            return (float) newValue;
        }

        if (oldValue instanceof Double) {
            return (double) newValue;
        }

        if (oldValue instanceof String) {
            return String.valueOf(newValue);
        }

        return newValue;
    }

    private Object castLikeOldValue(Object oldValue, boolean newValue) {
        if (oldValue instanceof Boolean) {
            return newValue;
        }

        if (oldValue instanceof Integer) {
            return newValue ? 1 : 0;
        }

        if (oldValue instanceof Long) {
            return newValue ? 1L : 0L;
        }

        if (oldValue instanceof String) {
            return String.valueOf(newValue);
        }

        return newValue;
    }

    private boolean isSameValue(Object oldValue, Object newValue) {
        if (oldValue == newValue) {
            return true;
        }

        if (oldValue == null || newValue == null) {
            return false;
        }

        return String.valueOf(oldValue).equals(String.valueOf(newValue));
    }

    private boolean isPrimitiveLike(Object object) {
        if (object == null) {
            return true;
        }

        return object instanceof String
                || object instanceof Number
                || object instanceof Boolean
                || object instanceof Character
                || object instanceof Enum;
    }

    private boolean shouldScanApsParamClass(String className) {
        if (className == null) {
            return false;
        }

        return className.contains("apsAdapter")
                || className.contains("Aps")
                || className.contains("APS")
                || className.contains("ImageCategory")
                || className.contains("ImageItemInfo")
                || className.contains("ImageBuffer")
                || className.contains("ApsParameters")
                || className.contains("Parameter");
    }

    private boolean shouldScanSurfaceWrapperClass(String className) {
        if (className == null) {
            return false;
        }

        return className.startsWith("com.oplus.")
                || className.startsWith("com.coloros.")
                || className.startsWith("java.util.");
    }

    private void markGrPatchWindow() {
        long until = System.currentTimeMillis() + GR_PATCH_WINDOW_MS;
        grPatchUntilMs = until;

        host.xlog(
                Log.ERROR,
                "GR_PATCH_WINDOW_V42 mark untilMs="
                        + until
                        + " durationMs="
                        + GR_PATCH_WINDOW_MS
        );
    }

    private boolean isGrPatchWindowActive() {
        return System.currentTimeMillis() <= grPatchUntilMs;
    }

    private void markAiSceneryHighPixelWindow(String reason) {
        long until = System.currentTimeMillis() + AI_SCENERY_HIGH_PIXEL_GUARD_MS;
        aiSceneryHighPixelUntilMs = until;

        host.xlog(
                Log.ERROR,
                "AI_SCENERY_200MP_V27_50MP_SAFE_RAW mark high-pixel untilMs="
                        + until
                        + " reason="
                        + reason
        );
    }

    private boolean isAiSceneryHighPixelWindowActive() {
        return System.currentTimeMillis() <= aiSceneryHighPixelUntilMs;
    }

    private boolean isAiSceneryHighPixelText(String text) {
        if (text == null) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);

        boolean aiScenerySignal = lower.contains("aiscenery")
                || lower.contains("ai_scenery")
                || lower.contains("ai scenery")
                || lower.contains("ai.scenery");

        if (!aiScenerySignal) {
            return false;
        }

        boolean explicit200m = lower.contains("high_pixel_200m")
                || lower.contains("200mp is on")
                || lower.contains("high_pixel_200mp_enable, 1")
                || lower.contains("ai_scenery_high_pixel, 200")
                || lower.contains("capture_200m_defer_job_type")
                || lower.contains("defer_high_pixel_200mp_enable");

        boolean highPictureSession =
                (lower.contains("mbhighpicturesizeenable: true")
                        || lower.contains("mbhighpicturesizeenable=true"))
                        && (lower.contains("mpicturesize: 8192x6144")
                        || lower.contains("mpicturesize=8192x6144")
                        || lower.contains("mpicturesize: 6144x8192")
                        || lower.contains("mpicturesize=6144x8192"));

        return explicit200m || highPictureSession;
    }

    private boolean isSmall720(Size size) {
        if (size == null) {
            return false;
        }

        try {
            int w = size.getWidth();
            int h = size.getHeight();

            return (w == 720 && h == 480)
                    || (w == 480 && h == 720);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isLandscapeSize(int width, int height) {
        return width >= height;
    }

    private boolean isPortraitSize(int width, int height) {
        return height > width;
    }

    private boolean isFullLandscape(int width, int height) {
        return (width == FULL_LANDSCAPE_WIDTH && height == FULL_LANDSCAPE_HEIGHT)
                || (width == AI_SCENERY_HP_INPUT_LANDSCAPE_WIDTH
                && height == AI_SCENERY_HP_INPUT_LANDSCAPE_HEIGHT);
    }

    private boolean isFullPortrait(int width, int height) {
        return (width == FULL_PORTRAIT_WIDTH && height == FULL_PORTRAIT_HEIGHT)
                || (width == AI_SCENERY_HP_INPUT_PORTRAIT_WIDTH
                && height == AI_SCENERY_HP_INPUT_PORTRAIT_HEIGHT);
    }

    private boolean isFullSize(int width, int height) {
        return isFullLandscape(width, height) || isFullPortrait(width, height);
    }

    private boolean isFullLandscape(Size size) {
        if (size == null) {
            return false;
        }

        try {
            return isFullLandscape(size.getWidth(), size.getHeight());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isFullPortrait(Size size) {
        if (size == null) {
            return false;
        }

        try {
            return isFullPortrait(size.getWidth(), size.getHeight());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isFullSize(Size size) {
        if (size == null) {
            return false;
        }

        try {
            return isFullSize(size.getWidth(), size.getHeight());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Size chooseFullSizeByOldSize(Size oldSize) {
        if (oldSize == null) {
            return new Size(currentFullWidth, currentFullHeight);
        }

        try {
            int width = oldSize.getWidth();
            int height = oldSize.getHeight();

            if (isPortraitSize(width, height)) {
                return new Size(FULL_PORTRAIT_WIDTH, FULL_PORTRAIT_HEIGHT);
            }

            return new Size(FULL_LANDSCAPE_WIDTH, FULL_LANDSCAPE_HEIGHT);
        } catch (Throwable ignored) {
            return new Size(currentFullWidth, currentFullHeight);
        }
    }

    private Size chooseFullSizeByText(String text) {
        if (text == null) {
            return new Size(currentFullWidth, currentFullHeight);
        }

        String lower = text.toLowerCase(Locale.ROOT);

        if (lower.contains("8192x6144")
                || lower.contains("8192 x 6144")
                || lower.contains("mpicturesize: 8192x6144")
                || lower.contains("mpicturesize=8192x6144")) {
            markAiSceneryHighPixelWindow("chooseFullSizeByText " + safeShort(text));
            return new Size(AI_SCENERY_HP_INPUT_LANDSCAPE_WIDTH, AI_SCENERY_HP_INPUT_LANDSCAPE_HEIGHT);
        }

        if (lower.contains("6144x8192")
                || lower.contains("6144 x 8192")
                || lower.contains("mpicturesize: 6144x8192")
                || lower.contains("mpicturesize=6144x8192")) {
            markAiSceneryHighPixelWindow("chooseFullSizeByText " + safeShort(text));
            return new Size(AI_SCENERY_HP_INPUT_PORTRAIT_WIDTH, AI_SCENERY_HP_INPUT_PORTRAIT_HEIGHT);
        }

        if (lower.contains("4096x3072")
                || lower.contains("4096 x 3072")
                || lower.contains("mpicturesize: 4096x3072")
                || lower.contains("mpicturesize=4096x3072")) {
            return new Size(FULL_LANDSCAPE_WIDTH, FULL_LANDSCAPE_HEIGHT);
        }

        if (lower.contains("3072x4096")
                || lower.contains("3072 x 4096")
                || lower.contains("mpicturesize: 3072x4096")
                || lower.contains("mpicturesize=3072x4096")) {
            return new Size(FULL_PORTRAIT_WIDTH, FULL_PORTRAIT_HEIGHT);
        }

        if (lower.contains("1920x1440")
                || lower.contains("1920 x 1440")
                || lower.contains("1440x1080")
                || lower.contains("1440 x 1080")) {
            return new Size(FULL_LANDSCAPE_WIDTH, FULL_LANDSCAPE_HEIGHT);
        }

        if (lower.contains("1440x1920")
                || lower.contains("1440 x 1920")
                || lower.contains("1080x1440")
                || lower.contains("1080 x 1440")) {
            return new Size(FULL_PORTRAIT_WIDTH, FULL_PORTRAIT_HEIGHT);
        }

        return new Size(currentFullWidth, currentFullHeight);
    }

    private void updateCurrentFullSize(Size size, String reason) {
        if (size == null) {
            return;
        }

        int width = size.getWidth();
        int height = size.getHeight();

        if (!isFullSize(width, height)) {
            return;
        }

        currentFullWidth = width;
        currentFullHeight = height;

        host.xlog(
                Log.ERROR,
                "GR_SIZE_FIX_V43 update currentFullSize="
                        + width
                        + "x"
                        + height
                        + " reason="
                        + reason
        );
    }

    private Size choosePreviewSafeSizeByOld(int width, int height) {
        if (isPortraitSize(width, height)) {
            return new Size(PREVIEW_SAFE_PORTRAIT_WIDTH, PREVIEW_SAFE_PORTRAIT_HEIGHT);
        }

        return new Size(PREVIEW_SAFE_LANDSCAPE_WIDTH, PREVIEW_SAFE_LANDSCAPE_HEIGHT);
    }

    private Size chooseMetaSafeSizeByOld(int width, int height) {
        if (isPortraitSize(width, height)) {
            return new Size(META_SAFE_PORTRAIT_WIDTH, META_SAFE_PORTRAIT_HEIGHT);
        }

        return new Size(META_SAFE_LANDSCAPE_WIDTH, META_SAFE_LANDSCAPE_HEIGHT);
    }


    private int toInt(Object value, int defValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (Throwable ignored) {
            }
        }

        return defValue;
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

    private String safeToString(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return String.valueOf(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String safeShort(String value) {
        if (value == null) {
            return "null";
        }

        if (value.length() <= 180) {
            return value;
        }

        return value.substring(0, 180);
    }

    private String objectClassName(Object value) {
        if (value == null) {
            return "null";
        }

        try {
            return value.getClass().getName();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static class RestoreRecord {
        final Object object;
        final Field field;
        final Object oldValue;
        final String label;

        RestoreRecord(Object object, Field field, Object oldValue, String label) {
            this.object = object;
            this.field = field;
            this.oldValue = oldValue;
            this.label = label;
        }
    }
}

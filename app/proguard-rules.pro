# LSPosed / libxposed 模块入口。
# java_init.list 必须能找到入口类，所以入口类名不能被改。
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,Signature,InnerClasses,EnclosingMethod

-keep,allowoptimization class com.min.lite.ModuleMain { *; }
-keep,allowoptimization class com.camera.CameraUnit.CameraUnitConfigModule { *; }
-keep,allowoptimization class com.camera.Protobuf.ProtobufModule { *; }
-keep,allowoptimization class com.camera.VendorTag.VendorTagModule { *; }
-keep,allowoptimization class com.camera.gr.GrModule { *; }

-keep,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public <init>(...);
    public void onModuleLoaded(...);
    public void onPackageLoaded(...);
    public void onPackageReady(...);
    public void onSystemServerStarting(...);
}

# Android 组件入口。
-keep,allowoptimization class com.camera.VendorTag.ui.VendorTagSettingsActivity { *; }
-keep,allowoptimization class com.camera.VendorTag.ui.VendorTagSettingsProvider { *; }

# Protobuf 配置类通过 ConfigRegistry 的字符串 Class.forName 加载，类名必须保留。
-keep,allowoptimization class com.camera.Protobuf.config.ProtobufConfig { *; }
-keep,allowoptimization class com.camera.Protobuf.config.FeatureInfo { *; }
-keep,allowoptimization class com.camera.Protobuf.config.GrModeConfig { *; }
-keep,allowoptimization class com.camera.Protobuf.config.VibeModeConfig { *; }
-keep,allowoptimization class com.camera.Protobuf.config.AiSceneryModeConfig { *; }

# AiSceneryModeConfig 会反射调用 ProtobufEditor#createFeature 和读取 featureTableBuilder 字段。
-keepclassmembers,allowoptimization class com.camera.Protobuf.config.ProtobufEditor {
    private java.lang.Object featureTableBuilder;
    private java.lang.Object createFeature(com.camera.Protobuf.config.FeatureInfo);
    public static java.lang.Object callMethod(java.lang.Object, java.lang.String, java.lang.Object[]);
}

# 运行时会反射调用 xlog / loadCameraClass 等模块方法，保留这些名字。
-keepclassmembers class com.camera.**.*Module {
    public *** loadCameraClass(...);
    public *** hookMethod(...);
    public void xlog(...);
}

# 外部宿主 / 系统隐藏类，编译期和 R8 不需要强制解析。
-dontwarn io.github.libxposed.**
-dontwarn com.oplus.**
-dontwarn com.google.oplus.protobuf.**
-dontwarn android.os.SystemProperties

# 去掉低级别 Android Log 调用；保留 Log.e，方便继续抓 CameraUnitXp / ProtobufFeature 错误日志。
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

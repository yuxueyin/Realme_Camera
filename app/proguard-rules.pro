-adaptresourcefilecontents META-INF/xposed/java_init.list

-keepattributes RuntimeVisibleAnnotations

-keep public class * extends io.github.libxposed.api.XposedModule {
    public <init>(...);
    public void onModuleLoaded(...);
    public void onPackageLoaded(...);
    public void onPackageReady(...);
    public void onSystemServerStarting(...);
}

-keep class com.min.lite.ModuleMain {
    *;
}

-keep class com.min.lite.MainActivity {
    *;
}
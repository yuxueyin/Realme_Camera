package com.camera.Protobuf.config;

/**
 * config 目录下的所有配置类都实现这个接口。
 *
 * 以后你要新增别的 protobuf 修改：
 * 1. 在 app/src/main/java/com/camera/Protobuf/config 下新建一个类
 * 2. implements ProtobufConfig
 * 3. 写 public 无参构造
 * 4. apply() 里调用 editor.addFeatures(...)
 *
 * ConfigRegistry 会自动扫描 config 包下面所有实现 ProtobufConfig 的类。
 */
public interface ProtobufConfig {

    String name();

    boolean apply(ProtobufEditor editor) throws Exception;
}
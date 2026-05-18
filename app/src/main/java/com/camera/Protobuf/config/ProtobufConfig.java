package com.camera.Protobuf.config;

public interface ProtobufConfig {

    String name();

    boolean apply(ProtobufEditor editor) throws Exception;
}
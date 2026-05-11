package com.camera.Protobuf.config;

/**
 * 对应 protobuf 里的 Feature。
 */
public final class FeatureInfo {

    public final String featureName;

    public final String featureKeyName;

    public final String featureValueRange;

    public final String featureDefaultValue;

    public final String entryType;

    public final String featureValueType;

    public FeatureInfo(
            String featureName,
            String featureKeyName,
            String featureValueRange,
            String featureDefaultValue,
            String entryType,
            String featureValueType
    ) {
        this.featureName = featureName == null ? "" : featureName;
        this.featureKeyName = featureKeyName == null ? "" : featureKeyName;
        this.featureValueRange = featureValueRange == null ? "" : featureValueRange;
        this.featureDefaultValue = featureDefaultValue == null ? "" : featureDefaultValue;
        this.entryType = entryType == null ? "" : entryType;
        this.featureValueType = featureValueType == null ? "" : featureValueType;
    }
}
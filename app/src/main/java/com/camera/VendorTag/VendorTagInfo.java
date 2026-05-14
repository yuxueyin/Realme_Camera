package com.camera.VendorTag;

/**
 * 对应 oplus_camera_config JSON 里的单条 VendorTag。
 */
public final class VendorTagInfo {

    public final String vendorTag;

    public final String type;

    public final String count;

    public final String value;

    public VendorTagInfo(
            String vendorTag,
            String type,
            String count,
            String value
    ) {
        this.vendorTag = vendorTag == null ? "" : vendorTag;
        this.type = type == null ? "" : type;
        this.count = count == null ? "" : count;
        this.value = value == null ? "" : value;
    }
}
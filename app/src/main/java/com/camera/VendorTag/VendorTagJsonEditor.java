package com.camera.VendorTag;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VendorTagJsonEditor {

    private static final String TAG_PREVIEW_HDR_CAP_MODE =
            "com.oplus.camera.preview.hdr.cap.mode.value";

    private static final String TAG_CAPTURE_HDR_CAP_MODE =
            "com.oplus.camera.capture.hdr.cap.mode.value";

    private static final String TAG_VIBE_HYBRID_RAW_NO_SUPPORT =
            "com.oplus.feature.vibe.hybridraw.no.support";

    private static final String TAG_VIBE_MODE_SUPPORT =
            "com.oplus.vibe.mode.support";

    private static final String TAG_VIBE_TELE_ZOOM_POINT_SHOW =
            "com.oplus.vibe.tele.zoom.point.show";

    private static final String TAG_AI_SCENERY_MODE_SUPPORT =
            "com.oplus.feature.ai.scenery.mode.support";

    private static final String TAG_AI_SCENERY_MODE_HIGH_PIXEL_SUPPORT =
            "com.oplus.feature.ai.scenery.mode.high.pixel.support";

    private static final String TAG_AI_SCENERY_MODE_DE_HAZY_SWITCH =
            "com.oplus.feature.ai.scenery.mode.de.hazy.switch";

    /*
     * 关键修复：
     *
     * HDR 这两个 tag 不能删除。
     * 删除后 HdrAllRoutePresenter 内部拿到 null，
     * 调用 List.contains() 就会空指针闪退。
     *
     * 也不能把它们写成单独的 vibe。
     * 如果发现旧版本把 Value 污染成 vibe，就修回原始列表。
     */
    private static final String DEFAULT_PREVIEW_HDR_CAP_MODE_VALUE =
            "common,professional,night,highPixel,xpan,underWater,telephoto";

    private static final String DEFAULT_CAPTURE_HDR_CAP_MODE_VALUE =
            "common,portrait,professional,night,highPixel,xpan,underWater,telephoto";

    private VendorTagJsonEditor() {
    }

    public static String modifyOplusCameraConfig(String originalJson, VendorTagModule host) {
        if (originalJson == null || originalJson.trim().length() == 0) {
            xlog(host, "VendorTagJsonEditor originalJson empty");
            return originalJson;
        }

        try {
            String trimJson = originalJson.trim();

            boolean objectWithFileData =
                    !trimJson.startsWith("[")
                            && trimJson.contains("\"file_data\"");

            JSONObject rootObject = null;
            JSONArray fileDataArray;

            if (objectWithFileData) {
                rootObject = new JSONObject(originalJson);
                fileDataArray = rootObject.getJSONArray("file_data");
                xlog(host, "VendorTagJsonEditor parse object file_data length=" + fileDataArray.length());
            } else {
                fileDataArray = new JSONArray(originalJson);
                xlog(host, "VendorTagJsonEditor parse array length=" + fileDataArray.length());
            }

            /*
             * 不删除 HDR。
             * 这里只删除错误的 VendorTag=String type=1 count=1 value=vibe，
             * 并把被污染成 vibe 的 HDR 值修回原始列表。
             */
            fileDataArray = repairBadVendorTags(fileDataArray, host);

            Map<String, Integer> tagIndexMap = buildTagIndexMap(fileDataArray);

            VendorTagRuntimeSettings.Settings settings =
                    VendorTagRuntimeSettings.load(host);

            List<VendorTagInfo> tags = new ArrayList<>();

            tags.addAll(getGrVendorTags(settings));

            if (settings.vibeEnabled) {
                tags.addAll(getVibeVendorTags());
            }

            tags.addAll(getAiSceneryVendorTags(settings));

            int addCount = 0;
            int updateCount = 0;

            for (VendorTagInfo tagInfo : tags) {
                if (tagInfo == null || tagInfo.vendorTag == null || tagInfo.vendorTag.length() == 0) {
                    continue;
                }

                if (isHdrCapModeTag(tagInfo.vendorTag)) {
                    xlog(host, "VendorTagJsonEditor skip HDR VendorTag=" + tagInfo.vendorTag);
                    continue;
                }

                if (isBrokenStringVibeEntry(tagInfo)) {
                    xlog(
                            host,
                            "VendorTagJsonEditor skip broken String vibe entry type="
                                    + tagInfo.type
                                    + " count="
                                    + tagInfo.count
                                    + " value="
                                    + tagInfo.value
                    );
                    continue;
                }

                JSONObject tagObject = createJsonObject(tagInfo);

                Integer oldIndex = tagIndexMap.get(tagInfo.vendorTag);

                if (oldIndex != null && oldIndex >= 0 && oldIndex < fileDataArray.length()) {
                    fileDataArray.put(oldIndex, tagObject);
                    updateCount++;

                    xlog(
                            host,
                            "VendorTagJsonEditor update VendorTag="
                                    + tagInfo.vendorTag
                                    + " type="
                                    + tagInfo.type
                                    + " count="
                                    + tagInfo.count
                                    + " value="
                                    + tagInfo.value
                    );
                } else {
                    fileDataArray.put(tagObject);
                    tagIndexMap.put(tagInfo.vendorTag, fileDataArray.length() - 1);
                    addCount++;

                    xlog(
                            host,
                            "VendorTagJsonEditor add VendorTag="
                                    + tagInfo.vendorTag
                                    + " type="
                                    + tagInfo.type
                                    + " count="
                                    + tagInfo.count
                                    + " value="
                                    + tagInfo.value
                    );
                }
            }

            xlog(
                    host,
                    "VendorTagJsonEditor modify done add="
                            + addCount
                            + " update="
                            + updateCount
                            + " vibe="
                            + (settings.vibeEnabled ? "1" : "0")
                            + " aiScenery="
                            + (settings.aiSceneryEnabled ? "1" : "0")
            );

            if (rootObject != null) {
                rootObject.put("file_data", fileDataArray);
                return rootObject.toString();
            }

            return fileDataArray.toString();
        } catch (Throwable t) {
            xlog(host, "VendorTagJsonEditor modify failed, return original " + t);
            return originalJson;
        }
    }

    private static JSONArray repairBadVendorTags(JSONArray source, VendorTagModule host) {
        JSONArray result = new JSONArray();

        if (source == null) {
            return result;
        }

        int removedCount = 0;
        int repairedCount = 0;

        for (int i = 0; i < source.length(); i++) {
            try {
                Object item = source.opt(i);

                if (!(item instanceof JSONObject)) {
                    result.put(item);
                    continue;
                }

                JSONObject object = (JSONObject) item;

                String vendorTag = object.optString("VendorTag", "");
                String type = object.optString("Type", "");
                String count = object.optString("Count", "");
                String value = object.optString("Value", "");

                if (isBrokenStringVibeEntry(vendorTag, type, count, value)) {
                    removedCount++;
                    xlog(
                            host,
                            "VendorTagJsonEditor remove broken entry VendorTag="
                                    + vendorTag
                                    + " type="
                                    + type
                                    + " count="
                                    + count
                                    + " value="
                                    + value
                    );
                    continue;
                }

                if (TAG_PREVIEW_HDR_CAP_MODE.equals(vendorTag)) {
                    if (needRepairHdrValue(type, count, value)) {
                        object.put("Type", "String");
                        object.put("Count", "1");
                        object.put("Value", DEFAULT_PREVIEW_HDR_CAP_MODE_VALUE);
                        repairedCount++;

                        xlog(
                                host,
                                "VendorTagJsonEditor repair preview HDR oldType="
                                        + type
                                        + " oldCount="
                                        + count
                                        + " oldValue="
                                        + value
                                        + " newValue="
                                        + DEFAULT_PREVIEW_HDR_CAP_MODE_VALUE
                        );
                    }

                    result.put(object);
                    continue;
                }

                if (TAG_CAPTURE_HDR_CAP_MODE.equals(vendorTag)) {
                    if (needRepairHdrValue(type, count, value)) {
                        object.put("Type", "String");
                        object.put("Count", "1");
                        object.put("Value", DEFAULT_CAPTURE_HDR_CAP_MODE_VALUE);
                        repairedCount++;

                        xlog(
                                host,
                                "VendorTagJsonEditor repair capture HDR oldType="
                                        + type
                                        + " oldCount="
                                        + count
                                        + " oldValue="
                                        + value
                                        + " newValue="
                                        + DEFAULT_CAPTURE_HDR_CAP_MODE_VALUE
                        );
                    }

                    result.put(object);
                    continue;
                }

                result.put(object);
            } catch (Throwable t) {
                try {
                    result.put(source.opt(i));
                } catch (Throwable ignored) {
                }
            }
        }

        if (removedCount > 0 || repairedCount > 0) {
            xlog(
                    host,
                    "VendorTagJsonEditor repairBadVendorTags removed="
                            + removedCount
                            + " repaired="
                            + repairedCount
            );
        }

        return result;
    }

    private static boolean needRepairHdrValue(String type, String count, String value) {
        if (!"String".equals(type)) {
            return true;
        }

        if (!"1".equals(count)) {
            return true;
        }

        if (value == null) {
            return true;
        }

        String trim = value.trim();

        if (trim.length() == 0) {
            return true;
        }

        if ("vibe".equals(trim)) {
            return true;
        }

        if ("String".equals(trim)) {
            return true;
        }

        /*
         * 正常 HDR 列表应该包含 common。
         * 如果完全没有 common，大概率就是被污染了。
         */
        return !trim.contains("common");
    }

    private static Map<String, Integer> buildTagIndexMap(JSONArray array) {
        Map<String, Integer> map = new LinkedHashMap<>();

        if (array == null) {
            return map;
        }

        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject object = array.getJSONObject(i);

                String vendorTag = object.optString("VendorTag", "");

                if (vendorTag.length() > 0) {
                    map.put(vendorTag, Integer.valueOf(i));
                }
            } catch (Throwable ignored) {
            }
        }

        return map;
    }

    private static JSONObject createJsonObject(VendorTagInfo info) throws Exception {
        JSONObject object = new JSONObject();

        object.put("VendorTag", info.vendorTag);
        object.put("Type", info.type);
        object.put("Count", info.count);
        object.put("Value", info.value);

        return object;
    }

    private static List<VendorTagInfo> getGrVendorTags(
            VendorTagRuntimeSettings.Settings settings
    ) {
        List<VendorTagInfo> list = new ArrayList<>();

        list.add(new VendorTagInfo(
                "com.oplus.gr.mode.support",
                "Byte",
                "1",
                settings.grEnabled ? "1" : "0"
        ));

        list.add(new VendorTagInfo(
                "com.oplus.available.gr.mode.zoomvalues",
                "String",
                String.valueOf(settings.availableZoomCount),
                settings.availableZoomValues
        ));

        list.add(new VendorTagInfo(
                "com.oplus.gr.mode.marked.zoomvalues",
                "String",
                String.valueOf(settings.markedZoomCount),
                settings.markedZoomValues
        ));

        return list;
    }

    private static List<VendorTagInfo> getVibeVendorTags() {
        List<VendorTagInfo> list = new ArrayList<>();

        /*
         * Vibe 只保留这三个。
         * 不再写 HDR cap mode。
         */
        list.add(new VendorTagInfo(
                TAG_VIBE_HYBRID_RAW_NO_SUPPORT,
                "Byte",
                "1",
                "1"
        ));

        list.add(new VendorTagInfo(
                TAG_VIBE_MODE_SUPPORT,
                "Byte",
                "1",
                "1"
        ));

        list.add(new VendorTagInfo(
                TAG_VIBE_TELE_ZOOM_POINT_SHOW,
                "Byte",
                "1",
                "1"
        ));

        return list;
    }


    private static List<VendorTagInfo> getAiSceneryVendorTags(
            VendorTagRuntimeSettings.Settings settings
    ) {
        List<VendorTagInfo> list = new ArrayList<>();
        String enabledValue = settings.aiSceneryEnabled ? "1" : "0";

        list.add(new VendorTagInfo(
                TAG_AI_SCENERY_MODE_SUPPORT,
                "Byte",
                "1",
                enabledValue
        ));

        list.add(new VendorTagInfo(
                TAG_AI_SCENERY_MODE_HIGH_PIXEL_SUPPORT,
                "Byte",
                "1",
                enabledValue
        ));

        list.add(new VendorTagInfo(
                TAG_AI_SCENERY_MODE_DE_HAZY_SWITCH,
                "Byte",
                "1",
                enabledValue
        ));

        return list;
    }

    private static boolean isHdrCapModeTag(String vendorTag) {
        if (vendorTag == null) {
            return false;
        }

        return TAG_PREVIEW_HDR_CAP_MODE.equals(vendorTag)
                || TAG_CAPTURE_HDR_CAP_MODE.equals(vendorTag);
    }

    private static boolean isBrokenStringVibeEntry(VendorTagInfo info) {
        if (info == null) {
            return false;
        }

        return isBrokenStringVibeEntry(
                info.vendorTag,
                info.type,
                info.count,
                info.value
        );
    }

    private static boolean isBrokenStringVibeEntry(
            String vendorTag,
            String type,
            String count,
            String value
    ) {
        return "String".equals(vendorTag)
                && "1".equals(type)
                && "1".equals(count)
                && "vibe".equals(value);
    }

    private static void xlog(VendorTagModule host, String msg) {
        try {
            Log.e("VendorTagJsonEditor", msg);
        } catch (Throwable ignored) {
        }

        if (host == null) {
            return;
        }

        try {
            host.xlog(Log.ERROR, msg);
        } catch (Throwable ignored) {
        }
    }
}
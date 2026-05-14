package com.camera.VendorTag;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Method;

public final class VendorTagRuntimeSettings {

    private static final String SETTINGS_URI_STRING =
            "content://com.min.lite.vendortag/settings";

    private static final String COLUMN_GR_ENABLED = "gr_enabled";
    private static final String COLUMN_VIBE_ENABLED = "vibe_enabled";

    private static final String COLUMN_AVAILABLE_ZOOM_VALUES = "available_zoom_values";
    private static final String COLUMN_AVAILABLE_ZOOM_COUNT = "available_zoom_count";

    private static final String COLUMN_MARKED_ZOOM_VALUES = "marked_zoom_values";
    private static final String COLUMN_MARKED_ZOOM_COUNT = "marked_zoom_count";

    private VendorTagRuntimeSettings() {
    }

    public static Settings load(VendorTagModule host) {
        Settings defaults = Settings.defaultSettings();

        Context context = getCurrentApplicationContext();

        if (context == null) {
            host.xlog(Log.ERROR, "VendorTagRuntimeSettings context null, use default");
            return defaults;
        }

        Cursor cursor = null;

        try {
            Uri uri = Uri.parse(SETTINGS_URI_STRING);

            cursor = context.getContentResolver().query(
                    uri,
                    null,
                    null,
                    null,
                    null
            );

            if (cursor == null) {
                host.xlog(Log.ERROR, "VendorTagRuntimeSettings cursor null, use default");
                return defaults;
            }

            if (!cursor.moveToFirst()) {
                host.xlog(Log.ERROR, "VendorTagRuntimeSettings cursor empty, use default");
                return defaults;
            }

            String grEnabled = getString(cursor, COLUMN_GR_ENABLED, defaults.grEnabled ? "1" : "0");
            String vibeEnabled = getString(cursor, COLUMN_VIBE_ENABLED, defaults.vibeEnabled ? "1" : "0");

            String availableZoom = getString(cursor, COLUMN_AVAILABLE_ZOOM_VALUES, defaults.availableZoomValues);
            String availableCount = getString(cursor, COLUMN_AVAILABLE_ZOOM_COUNT, String.valueOf(defaults.availableZoomCount));

            String markedZoom = getString(cursor, COLUMN_MARKED_ZOOM_VALUES, defaults.markedZoomValues);
            String markedCount = getString(cursor, COLUMN_MARKED_ZOOM_COUNT, String.valueOf(defaults.markedZoomCount));

            Settings settings = new Settings(
                    "1".equals(grEnabled),
                    "1".equals(vibeEnabled),
                    safeTrim(availableZoom),
                    parseIntSafe(availableCount, countCommaItems(availableZoom)),
                    safeTrim(markedZoom),
                    parseIntSafe(markedCount, countCommaItems(markedZoom))
            );

            host.xlog(
                    Log.ERROR,
                    "VendorTagRuntimeSettings loaded"
                            + " gr=" + (settings.grEnabled ? "1" : "0")
                            + " vibe=" + (settings.vibeEnabled ? "1" : "0")
                            + " availableCount=" + settings.availableZoomCount
                            + " available=" + settings.availableZoomValues
                            + " markedCount=" + settings.markedZoomCount
                            + " marked=" + settings.markedZoomValues
            );

            return settings;
        } catch (Throwable t) {
            host.xlog(Log.ERROR, "VendorTagRuntimeSettings load failed, use default", t);
            return defaults;
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Context getCurrentApplicationContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");

            Method currentApplicationMethod =
                    activityThreadClass.getDeclaredMethod("currentApplication");

            currentApplicationMethod.setAccessible(true);

            Object application = currentApplicationMethod.invoke(null);

            if (application instanceof Context) {
                return (Context) application;
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> appGlobalsClass = Class.forName("android.app.AppGlobals");

            Method getInitialApplicationMethod =
                    appGlobalsClass.getDeclaredMethod("getInitialApplication");

            getInitialApplicationMethod.setAccessible(true);

            Object application = getInitialApplicationMethod.invoke(null);

            if (application instanceof Context) {
                return (Context) application;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static String getString(Cursor cursor, String columnName, String defaultValue) {
        try {
            int index = cursor.getColumnIndex(columnName);

            if (index < 0) {
                return defaultValue;
            }

            String value = cursor.getString(index);

            if (value == null) {
                return defaultValue;
            }

            return value;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    public static int countCommaItems(String value) {
        String safe = safeTrim(value);

        if (safe.length() == 0) {
            return 0;
        }

        String[] parts = safe.split(",");

        int count = 0;

        for (String part : parts) {
            if (safeTrim(part).length() > 0) {
                count++;
            }
        }

        return count;
    }

    private static int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(safeTrim(value));
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private static String safeTrim(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    public static final class Settings {

        public final boolean grEnabled;
        public final boolean vibeEnabled;

        public final String availableZoomValues;
        public final int availableZoomCount;

        public final String markedZoomValues;
        public final int markedZoomCount;

        public Settings(
                boolean grEnabled,
                boolean vibeEnabled,
                String availableZoomValues,
                int availableZoomCount,
                String markedZoomValues,
                int markedZoomCount
        ) {
            this.grEnabled = grEnabled;
            this.vibeEnabled = vibeEnabled;
            this.availableZoomValues = availableZoomValues == null ? "" : availableZoomValues;
            this.availableZoomCount = Math.max(0, availableZoomCount);
            this.markedZoomValues = markedZoomValues == null ? "" : markedZoomValues;
            this.markedZoomCount = Math.max(0, markedZoomCount);
        }

        public static Settings defaultSettings() {
            String available =
                    "1.26(28),1.575(35),2.0(40),2.50(50)";

            String marked =
                    "1.26(28),2.0(40)";

            return new Settings(
                    true,
                    false,
                    available,
                    countCommaItems(available),
                    marked,
                    countCommaItems(marked)
            );
        }
    }
}
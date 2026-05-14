package com.camera.VendorTag.ui;

import android.content.Context;
import android.content.SharedPreferences;

public final class VendorTagSettings {

    public static final String PREF_NAME = "vendor_tag_settings";

    public static final String KEY_GR_ENABLED = "gr_enabled";
    public static final String KEY_VIBE_ENABLED = "vibe_enabled";

    public static final String KEY_AVAILABLE_ZOOM_VALUES = "available_zoom_values";
    public static final String KEY_MARKED_ZOOM_VALUES = "marked_zoom_values";

    public static final String KEY_LSP_ACTIVE = "lsp_active";
    public static final String KEY_LSP_LAST_ACTIVE_TIME = "lsp_last_active_time";

    public static final boolean DEFAULT_GR_ENABLED = true;
    public static final boolean DEFAULT_VIBE_ENABLED = false;

    public static final String DEFAULT_AVAILABLE_ZOOM_VALUES =
            "1.26(28),1.575(35),2.0(40),2.50(50)";

    public static final String DEFAULT_MARKED_ZOOM_VALUES =
            "1.26(28),2.0(40)";

    private static final long LSP_ACTIVE_TIMEOUT_MS =
            30L * 24L * 60L * 60L * 1000L;

    private VendorTagSettings() {
    }

    public static boolean isGrEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_GR_ENABLED, DEFAULT_GR_ENABLED);
    }

    public static boolean isVibeEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_VIBE_ENABLED, DEFAULT_VIBE_ENABLED);
    }

    public static String getAvailableZoomValues(Context context) {
        return getPrefs(context).getString(
                KEY_AVAILABLE_ZOOM_VALUES,
                DEFAULT_AVAILABLE_ZOOM_VALUES
        );
    }

    public static String getMarkedZoomValues(Context context) {
        return getPrefs(context).getString(
                KEY_MARKED_ZOOM_VALUES,
                DEFAULT_MARKED_ZOOM_VALUES
        );
    }

    public static int getAvailableZoomCount(Context context) {
        return countCommaItems(getAvailableZoomValues(context));
    }

    public static int getMarkedZoomCount(Context context) {
        return countCommaItems(getMarkedZoomValues(context));
    }

    public static void saveGrEnabled(Context context, boolean enabled) {
        getPrefs(context)
                .edit()
                .putBoolean(KEY_GR_ENABLED, enabled)
                .apply();
    }

    public static void saveVibeEnabled(Context context, boolean enabled) {
        getPrefs(context)
                .edit()
                .putBoolean(KEY_VIBE_ENABLED, enabled)
                .apply();
    }

    public static void save(
            Context context,
            boolean grEnabled,
            boolean vibeEnabled,
            String availableZoomValues,
            String markedZoomValues
    ) {
        getPrefs(context)
                .edit()
                .putBoolean(KEY_GR_ENABLED, grEnabled)
                .putBoolean(KEY_VIBE_ENABLED, vibeEnabled)
                .putString(KEY_AVAILABLE_ZOOM_VALUES, safeTrim(availableZoomValues))
                .putString(KEY_MARKED_ZOOM_VALUES, safeTrim(markedZoomValues))
                .apply();
    }

    public static void reset(Context context) {
        save(
                context,
                DEFAULT_GR_ENABLED,
                DEFAULT_VIBE_ENABLED,
                DEFAULT_AVAILABLE_ZOOM_VALUES,
                DEFAULT_MARKED_ZOOM_VALUES
        );
    }

    public static void markLspActive(Context context) {
        getPrefs(context)
                .edit()
                .putBoolean(KEY_LSP_ACTIVE, true)
                .putLong(KEY_LSP_LAST_ACTIVE_TIME, System.currentTimeMillis())
                .apply();

        try {
            LspStatusMarker.markBySu();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isLspActive(Context context) {
        boolean prefsActive = isLspActiveByPrefs(context);
        boolean markerActive = false;

        try {
            markerActive = LspStatusMarker.isActiveBySu();
        } catch (Throwable ignored) {
        }

        return prefsActive || markerActive;
    }

    private static boolean isLspActiveByPrefs(Context context) {
        SharedPreferences prefs = getPrefs(context);

        boolean active = prefs.getBoolean(KEY_LSP_ACTIVE, false);
        long lastActiveTime = prefs.getLong(KEY_LSP_LAST_ACTIVE_TIME, 0L);

        if (!active || lastActiveTime <= 0L) {
            return false;
        }

        long now = System.currentTimeMillis();

        return now - lastActiveTime <= LSP_ACTIVE_TIMEOUT_MS;
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

    public static String safeTrim(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
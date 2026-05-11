package com.camera.VendorTag.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public final class VendorTagSettingsProvider extends ContentProvider {

    public static final String AUTHORITY = "com.camera.lumo.vendortag";

    public static final Uri SETTINGS_URI =
            Uri.parse("content://" + AUTHORITY + "/settings");

    public static final Uri STATUS_URI =
            Uri.parse("content://" + AUTHORITY + "/status");

    public static final String COLUMN_GR_ENABLED = "gr_enabled";
    public static final String COLUMN_VIBE_ENABLED = "vibe_enabled";

    public static final String COLUMN_AVAILABLE_ZOOM_VALUES = "available_zoom_values";
    public static final String COLUMN_AVAILABLE_ZOOM_COUNT = "available_zoom_count";
    public static final String COLUMN_MARKED_ZOOM_VALUES = "marked_zoom_values";
    public static final String COLUMN_MARKED_ZOOM_COUNT = "marked_zoom_count";

    public static final String COLUMN_LSP_ACTIVE = "lsp_active";
    public static final String COLUMN_LSP_LAST_ACTIVE_TIME = "lsp_last_active_time";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        if (getContext() == null) {
            return null;
        }

        String path = uri == null ? "" : String.valueOf(uri.getPath());

        if (path.contains("status")) {
            return queryStatus();
        }

        return querySettings();
    }

    private Cursor querySettings() {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                COLUMN_GR_ENABLED,
                COLUMN_VIBE_ENABLED,
                COLUMN_AVAILABLE_ZOOM_VALUES,
                COLUMN_AVAILABLE_ZOOM_COUNT,
                COLUMN_MARKED_ZOOM_VALUES,
                COLUMN_MARKED_ZOOM_COUNT
        });

        boolean grEnabled = VendorTagSettings.isGrEnabled(getContext());
        boolean vibeEnabled = VendorTagSettings.isVibeEnabled(getContext());

        String availableZoom = VendorTagSettings.getAvailableZoomValues(getContext());
        int availableCount = VendorTagSettings.getAvailableZoomCount(getContext());

        String markedZoom = VendorTagSettings.getMarkedZoomValues(getContext());
        int markedCount = VendorTagSettings.getMarkedZoomCount(getContext());

        cursor.addRow(new Object[]{
                grEnabled ? "1" : "0",
                vibeEnabled ? "1" : "0",
                availableZoom,
                String.valueOf(availableCount),
                markedZoom,
                String.valueOf(markedCount)
        });

        return cursor;
    }

    private Cursor queryStatus() {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                COLUMN_LSP_ACTIVE,
                COLUMN_LSP_LAST_ACTIVE_TIME
        });

        boolean lspActive = VendorTagSettings.isLspActive(getContext());

        long lastActiveTime = getContext()
                .getSharedPreferences(VendorTagSettings.PREF_NAME, android.content.Context.MODE_PRIVATE)
                .getLong(VendorTagSettings.KEY_LSP_LAST_ACTIVE_TIME, 0L);

        cursor.addRow(new Object[]{
                lspActive ? "1" : "0",
                String.valueOf(lastActiveTime)
        });

        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.com.camera.lumo.vendortag";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (getContext() == null) {
            return null;
        }

        String path = uri == null ? "" : String.valueOf(uri.getPath());

        if (path.contains("status")) {
            VendorTagSettings.markLspActive(getContext());
            return uri;
        }

        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (getContext() == null) {
            return 0;
        }

        String path = uri == null ? "" : String.valueOf(uri.getPath());

        if (path.contains("status")) {
            VendorTagSettings.markLspActive(getContext());
            return 1;
        }

        return 0;
    }
}
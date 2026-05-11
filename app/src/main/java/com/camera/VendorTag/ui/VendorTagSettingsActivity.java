package com.camera.VendorTag.ui;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.camera.lumo.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public final class VendorTagSettingsActivity extends Activity {

    private static final int PAGE_HOME = 0;
    private static final int PAGE_CAMERA = 1;
    private static final int PAGE_ALBUM = 2;
    private static final int PAGE_MODULE = 3;

    private Switch switchGrSupport;
    private Switch switchVibeSupport;

    private LinearLayout cardModuleStatus;

    private LinearLayout pageHome;
    private LinearLayout pageCamera;
    private LinearLayout pageAlbum;
    private LinearLayout pageModule;

    private LinearLayout tabHome;
    private LinearLayout tabCamera;
    private LinearLayout tabAlbum;
    private LinearLayout tabModule;

    private ImageView iconHome;
    private ImageView iconCamera;
    private ImageView iconAlbum;
    private ImageView iconModule;

    private TextView textHome;
    private TextView textCamera;
    private TextView textAlbum;
    private TextView textModule;

    private TextView textPageTitle;
    private TextView textPageSubTitle;

    private TextView textModuleStatus;
    private TextView textModuleDetail;
    private TextView textFeatureCount;
    private TextView textModuleVersion;
    private TextView textDeviceModel;
    private TextView textAndroidVersion;
    private TextView textSystemVersion;
    private TextView textCameraVersion;

    private Button buttonRestartCamera;
    private Button buttonClearCameraData;

    private boolean loadingSettings = false;

    private boolean rootActive = false;
    private boolean lspActive = false;

    private final int activeColor = 0xFF4A7DE1;
    private final int inactiveColor = 0xFF222222;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vendor_tag_settings);

        initViews();
        loadSettingsToUi();
        loadHomeInfo();
        bindEvents();

        showPage(PAGE_HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();

        lspActive = VendorTagSettings.isLspActive(this);
        updateModuleStatusCard();
    }

    private void initViews() {
        switchGrSupport = findViewById(R.id.switchGrSupport);
        switchVibeSupport = findViewById(R.id.switchVibeSupport);

        cardModuleStatus = findViewById(R.id.cardModuleStatus);

        pageHome = findViewById(R.id.pageHome);
        pageCamera = findViewById(R.id.pageCamera);
        pageAlbum = findViewById(R.id.pageAlbum);
        pageModule = findViewById(R.id.pageModule);

        tabHome = findViewById(R.id.tabHome);
        tabCamera = findViewById(R.id.tabCamera);
        tabAlbum = findViewById(R.id.tabAlbum);
        tabModule = findViewById(R.id.tabModule);

        iconHome = findViewById(R.id.iconHome);
        iconCamera = findViewById(R.id.iconCamera);
        iconAlbum = findViewById(R.id.iconAlbum);
        iconModule = findViewById(R.id.iconModule);

        textHome = findViewById(R.id.textHome);
        textCamera = findViewById(R.id.textCamera);
        textAlbum = findViewById(R.id.textAlbum);
        textModule = findViewById(R.id.textModule);

        textPageTitle = findViewById(R.id.textPageTitle);
        textPageSubTitle = findViewById(R.id.textPageSubTitle);

        textModuleStatus = findViewById(R.id.textModuleStatus);
        textModuleDetail = findViewById(R.id.textModuleDetail);
        textFeatureCount = findViewById(R.id.textFeatureCount);
        textModuleVersion = findViewById(R.id.textModuleVersion);
        textDeviceModel = findViewById(R.id.textDeviceModel);
        textAndroidVersion = findViewById(R.id.textAndroidVersion);
        textSystemVersion = findViewById(R.id.textSystemVersion);
        textCameraVersion = findViewById(R.id.textCameraVersion);

        buttonRestartCamera = findViewById(R.id.buttonRestartCamera);
        buttonClearCameraData = findViewById(R.id.buttonClearCameraData);

        if (buttonRestartCamera != null) {
            buttonRestartCamera.setTextColor(0xFFFFFFFF);
        }

        if (buttonClearCameraData != null) {
            buttonClearCameraData.setTextColor(0xFF5E43A5);
        }
    }

    private void loadSettingsToUi() {
        loadingSettings = true;

        boolean grEnabled = VendorTagSettings.isGrEnabled(this);
        boolean vibeEnabled = VendorTagSettings.isVibeEnabled(this);

        if (switchGrSupport != null) {
            switchGrSupport.setChecked(grEnabled);
        }

        if (switchVibeSupport != null) {
            switchVibeSupport.setChecked(vibeEnabled);
        }

        updateFeatureCount(grEnabled, vibeEnabled);

        loadingSettings = false;
    }

    private void loadHomeInfo() {
        lspActive = VendorTagSettings.isLspActive(this);

        textModuleVersion.setText(getSelfVersionName());
        textDeviceModel.setText(getDeviceModelByOplusMarketName());
        textAndroidVersion.setText(getAndroidVersion());
        textSystemVersion.setText(getSystemVersion());
        textCameraVersion.setText(getPackageVersion("com.oplus.camera"));

        updateModuleStatusCard();
        checkRootAsync();
    }

    private void bindEvents() {
        tabHome.setOnClickListener(v -> showPage(PAGE_HOME));
        tabCamera.setOnClickListener(v -> showPage(PAGE_CAMERA));
        tabAlbum.setOnClickListener(v -> showPage(PAGE_ALBUM));
        tabModule.setOnClickListener(v -> showPage(PAGE_MODULE));

        if (switchGrSupport != null) {
            switchGrSupport.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (loadingSettings) {
                    return;
                }

                boolean vibeEnabled = switchVibeSupport != null && switchVibeSupport.isChecked();

                VendorTagSettings.save(
                        this,
                        isChecked,
                        vibeEnabled,
                        VendorTagSettings.DEFAULT_AVAILABLE_ZOOM_VALUES,
                        VendorTagSettings.DEFAULT_MARKED_ZOOM_VALUES
                );

                updateFeatureCount(isChecked, vibeEnabled);

                if (isChecked) {
                    Toast.makeText(this, "已开启真我 GT8 系列专属理光 GR", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "已关闭真我 GT8 系列专属理光 GR", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (switchVibeSupport != null) {
            switchVibeSupport.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (loadingSettings) {
                    return;
                }

                boolean grEnabled = switchGrSupport != null && switchGrSupport.isChecked();

                VendorTagSettings.save(
                        this,
                        grEnabled,
                        isChecked,
                        VendorTagSettings.DEFAULT_AVAILABLE_ZOOM_VALUES,
                        VendorTagSettings.DEFAULT_MARKED_ZOOM_VALUES
                );

                updateFeatureCount(grEnabled, isChecked);

                if (isChecked) {
                    Toast.makeText(this, "已开启影调：Neo8 专属功能", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "已关闭影调", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (buttonRestartCamera != null) {
            buttonRestartCamera.setOnClickListener(v -> restartCamera());
        }

        if (buttonClearCameraData != null) {
            buttonClearCameraData.setOnClickListener(v -> clearCameraData());
        }
    }

    private void showPage(int page) {
        pageHome.setVisibility(page == PAGE_HOME ? LinearLayout.VISIBLE : LinearLayout.GONE);
        pageCamera.setVisibility(page == PAGE_CAMERA ? LinearLayout.VISIBLE : LinearLayout.GONE);
        pageAlbum.setVisibility(page == PAGE_ALBUM ? LinearLayout.VISIBLE : LinearLayout.GONE);
        pageModule.setVisibility(page == PAGE_MODULE ? LinearLayout.VISIBLE : LinearLayout.GONE);

        setTabState(PAGE_HOME, page == PAGE_HOME);
        setTabState(PAGE_CAMERA, page == PAGE_CAMERA);
        setTabState(PAGE_ALBUM, page == PAGE_ALBUM);
        setTabState(PAGE_MODULE, page == PAGE_MODULE);

        if (page == PAGE_HOME) {
            textPageTitle.setText("首页");
            textPageSubTitle.setText("真我相机补全");
        } else if (page == PAGE_CAMERA) {
            textPageTitle.setText("相机设置");
            textPageSubTitle.setText("真我相机补全");
        } else if (page == PAGE_ALBUM) {
            textPageTitle.setText("相册设置");
            textPageSubTitle.setText("真我相机补全");
        } else {
            textPageTitle.setText("模块设置");
            textPageSubTitle.setText("真我相机补全");
        }
    }

    private void setTabState(int page, boolean active) {
        int color = active ? activeColor : inactiveColor;

        if (page == PAGE_HOME) {
            iconHome.setColorFilter(color);
            textHome.setTextColor(color);
        } else if (page == PAGE_CAMERA) {
            iconCamera.setColorFilter(color);
            textCamera.setTextColor(color);
        } else if (page == PAGE_ALBUM) {
            iconAlbum.setColorFilter(color);
            textAlbum.setTextColor(color);
        } else if (page == PAGE_MODULE) {
            iconModule.setColorFilter(color);
            textModule.setTextColor(color);
        }
    }

    private void updateFeatureCount(boolean grEnabled, boolean vibeEnabled) {
        int count = 0;

        if (grEnabled) {
            count++;
        }

        if (vibeEnabled) {
            count++;
        }

        textFeatureCount.setText(String.valueOf(count));
    }

    private void updateModuleStatusCard() {
        boolean allActive = rootActive && lspActive;

        if (allActive) {
            cardModuleStatus.setBackgroundResource(R.drawable.bg_status_card);
            textModuleStatus.setText("工作中");
            textModuleStatus.setTextColor(0xFF111111);
            textModuleDetail.setTextColor(0xFF111111);
        } else {
            cardModuleStatus.setBackgroundResource(R.drawable.bg_status_card_gray);
            textModuleStatus.setText("未激活");
            textModuleStatus.setTextColor(0xFF6F7278);
            textModuleDetail.setTextColor(0xFF6F7278);
        }

        String lspText = lspActive ? "LSP 模块已激活" : "LSP 模块未激活";
        String rootText = rootActive ? "Root 已授权" : "Root 未授权";

        textModuleDetail.setText(lspText + "\n" + rootText);
    }

    private void restartCamera() {
        Toast.makeText(this, "正在重启相机", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            CameraAppCleaner.Result result = CameraAppCleaner.forceStopCamera();

            runOnUiThread(() -> {
                if (result.success) {
                    Toast.makeText(this, "相机已重启", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "重启失败，请检查 Root 权限", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void clearCameraData() {
        Toast.makeText(this, "正在清理相机数据", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            CameraAppCleaner.Result result = CameraAppCleaner.clearCameraDataAndForceStopCamera();

            runOnUiThread(() -> {
                if (result.success) {
                    Toast.makeText(this, "相机数据已清理", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "清理失败，请检查 Root 权限", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private String getSelfVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);

            if (info.versionName != null && info.versionName.length() > 0) {
                return info.versionName;
            }
        } catch (Throwable ignored) {
        }

        return "1.0";
    }

    private String getPackageVersion(String packageName) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);

            if (info.versionName != null && info.versionName.length() > 0) {
                return info.versionName;
            }
        } catch (Throwable ignored) {
        }

        return "未知";
    }

    private String getDeviceModelByOplusMarketName() {
        String marketName = getSystemProperty("ro.vendor.oplus.market.name", "");

        if (marketName.length() == 0) {
            marketName = runGetProp("ro.vendor.oplus.market.name");
        }

        if (marketName.length() == 0) {
            marketName = getSystemProperty("ro.product.marketname", "");
        }

        if (marketName.length() == 0) {
            marketName = runGetProp("ro.product.marketname");
        }

        if (marketName.length() > 0) {
            return marketName;
        }

        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String model = Build.MODEL == null ? "" : Build.MODEL;

        if (model.toLowerCase().contains(manufacturer.toLowerCase())) {
            return model;
        }

        return (manufacturer + " " + model).trim();
    }

    private String getAndroidVersion() {
        String release = Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE;

        if (release.length() > 0) {
            return "Android " + release;
        }

        return "Android";
    }

    private String getSystemVersion() {
        String colorOsVersion = getSystemProperty("ro.build.version.oplusrom", "");

        if (colorOsVersion.length() == 0) {
            colorOsVersion = runGetProp("ro.build.version.oplusrom");
        }

        String displayId = Build.DISPLAY == null ? "" : Build.DISPLAY;

        if (colorOsVersion.length() > 0 && displayId.length() > 0) {
            return "ColorOS " + colorOsVersion + " / " + displayId;
        }

        if (colorOsVersion.length() > 0) {
            return "ColorOS " + colorOsVersion;
        }

        if (displayId.length() > 0) {
            return displayId;
        }

        String incremental = Build.VERSION.INCREMENTAL == null ? "" : Build.VERSION.INCREMENTAL;

        if (incremental.length() > 0) {
            return incremental;
        }

        return "未知";
    }

    private String getSystemProperty(String key, String defaultValue) {
        try {
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");

            Method getMethod =
                    systemPropertiesClass.getDeclaredMethod("get", String.class, String.class);

            getMethod.setAccessible(true);

            Object result = getMethod.invoke(null, key, defaultValue);

            if (result != null) {
                return String.valueOf(result).trim();
            }
        } catch (Throwable ignored) {
        }

        return defaultValue == null ? "" : defaultValue.trim();
    }

    private String runGetProp(String key) {
        BufferedReader reader = null;

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"getprop", key});

            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line = reader.readLine();

            process.waitFor();
            process.destroy();

            if (line != null) {
                return line.trim();
            }
        } catch (Throwable ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }

        return "";
    }

    private void checkRootAsync() {
        new Thread(() -> {
            boolean root = false;

            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                int exit = process.waitFor();
                root = exit == 0;
                process.destroy();
            } catch (Throwable ignored) {
            }

            boolean finalRoot = root;

            runOnUiThread(() -> {
                rootActive = finalRoot;
                lspActive = VendorTagSettings.isLspActive(this);
                updateModuleStatusCard();
            });
        }).start();
    }
}
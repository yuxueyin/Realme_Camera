package com.camera.VendorTag.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.min.lite.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

object LumoMaterial3Ui {
    private val controllers = WeakHashMap<Activity, Material3Controller>()

    @JvmStatic
    fun attach(activity: ComponentActivity) {
        val controller = Material3Controller(activity)
        controllers[activity] = controller

        activity.setContent {
            LumoMaterial3App(controller)
        }

        controller.refreshAll()
    }

    @JvmStatic
    fun refresh(activity: Activity) {
        controllers[activity]?.refreshAll()
    }

    @JvmStatic
    fun handleBack(activity: Activity): Boolean {
        return controllers[activity]?.handleBack() == true
    }
}

private enum class LumoPage(
    val title: String,
    val subtitle: String,
    val navLabel: String,
    val iconRes: Int,
    val selectedIconRes: Int = iconRes
) {
    Home("首页", "真我相机补全", "首页", R.drawable.ic_nav_home),
    Camera("相机设置", "", "相机", R.drawable.ic_nav_camera),
    Album("相册设置", "", "相册", R.drawable.ic_nav_album),
    Module("模块设置", "", "模块", R.drawable.ic_nav_module),
    ModuleLog("日志功能", "抓取 Hook 与相机运行日志", "日志", R.drawable.ic_nav_module)
}

private enum class UiThemeMode(
    val value: String,
    val label: String
) {
    Light("light", "浅色主题"),
    Dark("dark", "深色主题"),
    System("system", "跟随系统主题");

    companion object {
        fun from(value: String?): UiThemeMode {
            return entries.firstOrNull { it.value == value } ?: System
        }
    }
}

private enum class ModuleDialog {
    ThemeMode
}

private data class InfoRow(
    val label: String,
    val value: String
)

private class Material3Controller(activity: Activity) {
    private val activityRef = WeakReference(activity)

    var page by mutableStateOf(LumoPage.Home)
        private set
    var grEnabled by mutableStateOf(false)
        private set
    var vibeEnabled by mutableStateOf(false)
        private set
    var aiSceneryEnabled by mutableStateOf(false)
        private set
    var rootActive by mutableStateOf(false)
        private set
    var lspActive by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var logRunning by mutableStateOf(false)
        private set
    var logText by mutableStateOf("正在读取日志状态…")
        private set
    var moduleVersion by mutableStateOf("读取中")
        private set
    var deviceModel by mutableStateOf("读取中")
        private set
    var androidVersion by mutableStateOf("读取中")
        private set
    var systemVersion by mutableStateOf("读取中")
        private set
    var cameraVersion by mutableStateOf("读取中")
        private set
    var lspVersion by mutableStateOf("读取中")
        private set
    var rootManagerVersion by mutableStateOf("读取中")
        private set
    var themeMode by mutableStateOf(UiThemeMode.System)
        private set
    var activeDialog by mutableStateOf<ModuleDialog?>(null)
        private set

    val themeModeLabel: String
        get() = themeMode.label

    val canConsumeBack: Boolean
        get() = activeDialog != null || page == LumoPage.ModuleLog

    val featureCount: Int
        get() = listOf(grEnabled, vibeEnabled, aiSceneryEnabled).count { it }

    fun refreshAll() {
        val activity = activityRef.get() ?: return
        grEnabled = VendorTagSettings.isGrEnabled(activity)
        vibeEnabled = VendorTagSettings.isVibeEnabled(activity)
        aiSceneryEnabled = VendorTagSettings.isAiSceneryEnabled(activity)
        lspActive = VendorTagSettings.isLspActive(activity)
        moduleVersion = getSelfVersionName(activity)
        deviceModel = getDeviceModelByOplusMarketName()
        androidVersion = getAndroidVersion()
        systemVersion = getSystemVersion()
        cameraVersion = getPackageVersion(activity, "com.oplus.camera")
        lspVersion = if (lspActive) "已激活" else "未激活"
        rootManagerVersion = "检测中"
        loadUiPreferences(activity)
        updateLogStatusAsync()
        updateEnvironmentVersionsAsync()
        checkRootAsync()
    }

    fun showPage(target: LumoPage) {
        page = target
        if (target == LumoPage.ModuleLog) {
            updateLogStatusAsync()
        }
    }

    fun handleBack(): Boolean {
        if (activeDialog != null) {
            activeDialog = null
            return true
        }
        if (page == LumoPage.ModuleLog) {
            page = LumoPage.Module
            return true
        }
        return false
    }

    fun showThemeDialog() {
        activeDialog = ModuleDialog.ThemeMode
    }

    fun dismissDialog() {
        activeDialog = null
    }

    fun chooseThemeMode(mode: UiThemeMode) {
        val activity = activityRef.get() ?: return
        themeMode = mode
        saveUiPreferences(activity)
    }

    fun toggleGr() {
        val activity = activityRef.get() ?: return
        grEnabled = !grEnabled
        saveSettings(activity)
        toast(activity, if (grEnabled) "已开启真我 GT8 系列专属理光 GR" else "已关闭真我 GT8 系列专属理光 GR")
    }

    fun toggleVibe() {
        val activity = activityRef.get() ?: return
        vibeEnabled = !vibeEnabled
        saveSettings(activity)
        toast(activity, if (vibeEnabled) "已开启影调：Neo8 专属功能" else "已关闭影调")
    }

    fun toggleAiScenery() {
        val activity = activityRef.get() ?: return
        aiSceneryEnabled = !aiSceneryEnabled
        saveSettings(activity)
        toast(activity, if (aiSceneryEnabled) "已开启真我专属AI风光" else "已关闭AI风光")
    }

    fun restartCamera() {
        val activity = activityRef.get() ?: return
        busy = true
        toast(activity, "正在强制停止相机、清理数据并重启")
        Thread {
            val result = CameraAppCleaner.clearCameraDataAndForceStopCamera()
            activity.runOnUiThread {
                busy = false
                toast(activity, if (result.success) "相机数据已清理并重新启动" else "处理失败，请检查 Root 权限")
            }
        }.start()
    }

    fun startLogCapture() {
        val activity = activityRef.get() ?: return
        busy = true
        logText = "清理数据并重启相机…"
        toast(activity, "清理数据并重启相机")
        Thread {
            val cleanResult = CameraAppCleaner.clearCameraDataAndForceStopCamera()
            if (!cleanResult.success) {
                val status = ModuleLogManager.getStatus()
                activity.runOnUiThread {
                    busy = false
                    applyLogStatus(status)
                    toast(activity, "清理相机数据失败，请检查 Root 权限")
                }
                return@Thread
            }

            val result = ModuleLogManager.startCapture()
            val status = ModuleLogManager.getStatus()
            activity.runOnUiThread {
                busy = false
                applyLogStatus(status)
                toast(activity, if (result.success) "正在抓取日志，请复现问题" else "启动日志失败，请检查 Root 权限")
            }
        }.start()
    }

    fun stopLogCaptureAndGenerate() {
        val activity = activityRef.get() ?: return
        busy = true
        toast(activity, "正在停止抓取并生成 Hook 日志")
        Thread {
            val result = ModuleLogManager.stopCaptureAndGenerateLogs()
            val status = ModuleLogManager.getStatus()
            activity.runOnUiThread {
                busy = false
                applyLogStatus(status)
                toast(activity, if (result.success) "日志已生成" else "停止日志失败，请检查 Root 权限")
            }
        }.start()
    }

    fun clearModuleLogs() {
        val activity = activityRef.get() ?: return
        busy = true
        toast(activity, "正在清理日志")
        Thread {
            val result = ModuleLogManager.clearLogs()
            val status = ModuleLogManager.getStatus()
            activity.runOnUiThread {
                busy = false
                applyLogStatus(status)
                toast(activity, if (result.success) "日志已清理" else "清理日志失败，请检查 Root 权限")
            }
        }.start()
    }

    fun updateLogStatusAsync() {
        val activity = activityRef.get() ?: return
        Thread {
            val status = ModuleLogManager.getStatus()
            activity.runOnUiThread { applyLogStatus(status) }
        }.start()
    }

    private fun loadUiPreferences(activity: Activity) {
        val prefs = activity.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        themeMode = UiThemeMode.from(prefs.getString(KEY_THEME_MODE, UiThemeMode.System.value))
    }

    private fun saveUiPreferences(activity: Activity) {
        activity.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, themeMode.value)
            .apply()
    }

    private fun saveSettings(activity: Activity) {
        VendorTagSettings.save(
            activity,
            grEnabled,
            vibeEnabled,
            aiSceneryEnabled,
            VendorTagSettings.DEFAULT_AVAILABLE_ZOOM_VALUES,
            VendorTagSettings.DEFAULT_MARKED_ZOOM_VALUES
        )
    }

    private fun applyLogStatus(status: ModuleLogManager.Status) {
        logRunning = status.running
        logText = when {
            status.running -> "正在抓取日志，请复现问题。\n\n${status.listOutput}"
            status.listOutput.isNullOrEmpty() -> "暂无日志文件"
            else -> status.listOutput
        }
    }

    private fun updateEnvironmentVersionsAsync() {
        val activity = activityRef.get() ?: return
        Thread {
            val active = VendorTagSettings.isLspActive(activity)
            val detectedRootManager = getRootManagerVersion(activity)
            activity.runOnUiThread {
                lspActive = active
                lspVersion = if (active) "已激活" else "未激活"
                rootManagerVersion = detectedRootManager
            }
        }.start()
    }

    private fun checkRootAsync() {
        val activity = activityRef.get() ?: return
        Thread {
            var root = false
            var process: Process? = null
            try {
                process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                root = process.waitFor(900, TimeUnit.MILLISECONDS) && process.exitValue() == 0
            } catch (_: Throwable) {
            } finally {
                try {
                    process?.destroy()
                } catch (_: Throwable) {
                }
            }

            activity.runOnUiThread {
                rootActive = root
                lspActive = VendorTagSettings.isLspActive(activity)
                lspVersion = if (lspActive) "已激活" else "未激活"
                if (rootManagerVersion == "检测中") {
                    rootManagerVersion = getRootManagerVersion(activity)
                }
            }
        }.start()
    }

    private fun toast(activity: Activity, text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun LumoMaterial3App(controller: Material3Controller) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (controller.themeMode) {
        UiThemeMode.Light -> false
        UiThemeMode.Dark -> true
        UiThemeMode.System -> systemDark
    }
    val colorScheme = remember(context, darkTheme) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography
    ) {
        BackHandler(enabled = controller.canConsumeBack) {
            controller.handleBack()
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LumoScaffold(controller = controller)
            ModuleSettingsDialog(controller = controller)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LumoScaffold(controller: Material3Controller) {
    val navPages = remember { listOf(LumoPage.Home, LumoPage.Camera, LumoPage.Album, LumoPage.Module) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = controller.page.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
                        )
                        if (controller.page.subtitle.isNotBlank()) {
                            Text(
                                text = controller.page.subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 5.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            ) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    navPages.forEach { item ->
                        val selected = when (item) {
                            LumoPage.Module -> controller.page == LumoPage.Module || controller.page == LumoPage.ModuleLog
                            else -> controller.page == item
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { controller.showPage(item) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            icon = {
                                Icon(
                                    painter = painterResource(id = if (selected) item.selectedIconRes else item.iconRes),
                                    contentDescription = item.navLabel,
                                    modifier = Modifier.size(if (selected) 22.dp else 20.dp)
                                )
                            },
                            label = { Text(item.navLabel, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = controller.page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "page"
        ) { page ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (page) {
                    LumoPage.Home -> homePage(controller)
                    LumoPage.Camera -> cameraPage(controller)
                    LumoPage.Album -> albumPage()
                    LumoPage.Module -> modulePage(controller)
                    LumoPage.ModuleLog -> moduleLogPage(controller)
                }
            }
        }
    }
}

private fun LazyListScope.homePage(controller: Material3Controller) {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusHeroCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                title = "模块状态",
                value = if (controller.rootActive && controller.lspActive) "工作中" else "未激活",
                detail = listOf(
                    if (controller.lspActive) "LSP 模块已激活" else "LSP 模块未激活",
                    if (controller.rootActive) "Root 已授权" else "Root 未授权"
                ).joinToString("\n"),
                active = controller.rootActive && controller.lspActive
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "功能",
                    primary = controller.featureCount.toString(),
                    secondary = "已开启功能"
                )
                DualValueCard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    title = "环境版本",
                    firstLabel = "LSP 状态",
                    firstValue = controller.lspVersion,
                    secondLabel = "Root 管理器",
                    secondValue = controller.rootManagerVersion
                )
            }
        }
    }

    item {
        Material3InfoCard(
            title = "设备信息",
            subtitle = "当前设备与版本信息",
            rows = listOf(
                InfoRow("模块版本", controller.moduleVersion),
                InfoRow("设备型号", controller.deviceModel),
                InfoRow("Android", controller.androidVersion),
                InfoRow("系统版本", controller.systemVersion),
                InfoRow("相机版本", controller.cameraVersion)
            )
        )
    }
}

private fun LazyListScope.cameraPage(controller: Material3Controller) {
    item {
        Material3SwitchCard(
            title = "理光GR",
            body = "开启真我GT8系列专属理光GR\n注意：开启后有概率会导致界面卡住/照片黑屏，请更换合适的相机",
            checked = controller.grEnabled,
            onClick = controller::toggleGr
        )
    }

    item {
        Material3SwitchCard(
            title = "影调",
            body = "开启Neo8专属功能\n注意：会和自带的大师模式冲突，大师模式会变成长按相机启动",
            checked = controller.vibeEnabled,
            onClick = controller::toggleVibe
        )
    }

    item {
        Material3SwitchCard(
            title = "AI风光",
            body = "开启真我专属AI风光",
            checked = controller.aiSceneryEnabled,
            onClick = controller::toggleAiScenery
        )
    }
}

private fun LazyListScope.albumPage() {
    item {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("相册设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "当前版本暂未写入相册侧开关。后续需要补充相册能力时，可以继续在这个 Material3 页面基础上扩展。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                AssistChip(onClick = {}, label = { Text("Material3 Ready") })
            }
        }
    }
}

@Composable
private fun ModuleSettingsDialog(controller: Material3Controller) {
    when (controller.activeDialog) {
        ModuleDialog.ThemeMode -> ChoiceDialog(
            title = "选择主题模式",
            options = UiThemeMode.entries.toList(),
            selected = controller.themeMode,
            labelOf = { it.label },
            onSelect = controller::chooseThemeMode,
            onDismiss = controller::dismissDialog
        )
        null -> Unit
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) }
                        )
                        Text(
                            text = labelOf(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ModuleSettingChoiceCard(
    title: String,
    subtitle: String,
    value: String,
    iconText: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(iconText, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = value,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun LazyListScope.modulePage(controller: Material3Controller) {
    item {
        ModuleSettingChoiceCard(
            title = "主题模式",
            subtitle = "Google Material 3",
            value = controller.themeModeLabel,
            iconText = "◌",
            onClick = controller::showThemeDialog
        )
    }

    item {
        Material3ActionCard(
            title = "日志功能",
            body = "清理相机数据、重启相机并抓取 Hook 日志，用于排查功能未生效问题。",
            buttonText = "进入日志页面",
            enabled = true,
            onClick = { controller.showPage(LumoPage.ModuleLog) }
        )
    }

    item {
        Material3ActionCard(
            title = "重启相机",
            body = "修改配置后建议重启相机，让新的配置和 Hook 状态即时生效。",
            buttonText = if (controller.busy) "处理中…" else "清理并重启相机",
            enabled = !controller.busy,
            onClick = controller::restartCamera
        )
    }
}

private fun LazyListScope.moduleLogPage(controller: Material3Controller) {
    item {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("日志抓取", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            ModuleLogManager.LOG_DIR,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(if (controller.logRunning) "抓取中" else "空闲") }
                    )
                }

                LogButtons(controller)

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = controller.logText,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogButtons(controller: Material3Controller) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FilledTonalButton(
            enabled = !controller.busy && !controller.logRunning,
            onClick = controller::startLogCapture
        ) {
            Text(if (controller.logRunning) "正在抓取" else "开始抓取")
        }
        OutlinedButton(
            enabled = !controller.busy && controller.logRunning,
            onClick = controller::stopLogCaptureAndGenerate
        ) {
            Text("停止并生成")
        }
        OutlinedButton(
            enabled = !controller.busy,
            onClick = controller::updateLogStatusAsync
        ) {
            Text("刷新")
        }
        TextButton(
            enabled = !controller.busy,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            onClick = controller::clearModuleLogs
        ) {
            Text("清理")
        }
    }
}

@Composable
private fun StatusHeroCard(
    modifier: Modifier,
    title: String,
    value: String,
    detail: String,
    active: Boolean
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun CompactInfoCard(
    modifier: Modifier = Modifier,
    title: String,
    primary: String,
    secondary: String
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DualValueCard(
    modifier: Modifier = Modifier,
    title: String,
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            VersionRow(label = firstLabel, value = firstValue)
            VersionRow(label = secondLabel, value = secondValue)
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Material3InfoCard(
    title: String,
    subtitle: String,
    rows: List<InfoRow>
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(row.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        row.value,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun Material3SwitchCard(
    title: String,
    body: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = { onClick() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFBBD7FF),
                    checkedBorderColor = Color(0xFFBBD7FF),
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun Material3ActionCard(
    title: String,
    body: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            FilledTonalButton(
                enabled = enabled,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}

private const val UI_PREFS_NAME = "lumo_ui_prefs"
private const val KEY_THEME_MODE = "theme_mode"

private fun getSelfVersionName(context: Context): String {
    return try {
        val info: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName?.takeIf { it.isNotBlank() } ?: "1.0"
    } catch (_: Throwable) {
        "1.0"
    }
}

private fun getPackageVersion(context: Context, packageName: String): String {
    return try {
        val info: PackageInfo = context.packageManager.getPackageInfo(packageName, 0)
        info.versionName?.takeIf { it.isNotBlank() } ?: "未知"
    } catch (_: Throwable) {
        "未知"
    }
}

private fun getRootManagerVersion(context: Context): String {
    val candidates = listOf(
        "com.topjohnwu.magisk" to "Magisk",
        "io.github.huskydg.magisk" to "Magisk",
        "me.weishu.kernelsu" to "KernelSU",
        "me.bmax.apatch" to "APatch"
    )
    for ((pkg, label) in candidates) {
        val version = getInstalledVersionOrNull(context, pkg)
        if (!version.isNullOrBlank()) {
            return "$label $version"
        }
        if (isPackageInstalled(context, pkg)) {
            return "$label 已安装"
        }
    }
    return "未检测到"
}

private fun getInstalledVersionOrNull(context: Context, packageName: String): String? {
    return try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        info.versionName?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Throwable) {
        null
    }
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Throwable) {
        false
    }
}

private fun getDeviceModelByOplusMarketName(): String {
    val keys = listOf("ro.vendor.oplus.market.name", "ro.product.marketname")
    for (key in keys) {
        val propertyValue = getSystemProperty(key, "")
        if (propertyValue.isNotBlank()) return propertyValue
        val getPropValue = runGetProp(key)
        if (getPropValue.isNotBlank()) return getPropValue
    }

    val manufacturer = Build.MANUFACTURER.orEmpty()
    val model = Build.MODEL.orEmpty()
    return if (model.lowercase().contains(manufacturer.lowercase())) {
        model
    } else {
        "$manufacturer $model".trim()
    }.ifBlank { "未知" }
}

private fun getAndroidVersion(): String {
    val release = Build.VERSION.RELEASE.orEmpty()
    return if (release.isNotBlank()) "Android $release" else "Android"
}

private fun getSystemVersion(): String {
    var colorOsVersion = getSystemProperty("ro.build.version.oplusrom", "")
    if (colorOsVersion.isBlank()) {
        colorOsVersion = runGetProp("ro.build.version.oplusrom")
    }
    val displayId = Build.DISPLAY.orEmpty()

    return when {
        colorOsVersion.isNotBlank() && displayId.isNotBlank() -> "ColorOS $colorOsVersion / $displayId"
        colorOsVersion.isNotBlank() -> "ColorOS $colorOsVersion"
        displayId.isNotBlank() -> displayId
        Build.VERSION.INCREMENTAL.orEmpty().isNotBlank() -> Build.VERSION.INCREMENTAL.orEmpty()
        else -> "未知"
    }
}

private fun getSystemProperty(key: String, defaultValue: String): String {
    return try {
        val systemPropertiesClass = Class.forName("android.os.SystemProperties")
        val getMethod: Method = systemPropertiesClass.getDeclaredMethod("get", String::class.java, String::class.java)
        getMethod.isAccessible = true
        getMethod.invoke(null, key, defaultValue)?.toString()?.trim().orEmpty()
    } catch (_: Throwable) {
        defaultValue.trim()
    }
}

private fun runGetProp(key: String): String {
    var reader: BufferedReader? = null
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
        reader = BufferedReader(InputStreamReader(process.inputStream))
        val line = reader.readLine()
        process.waitFor()
        process.destroy()
        line?.trim().orEmpty()
    } catch (_: Throwable) {
        ""
    } finally {
        try {
            reader?.close()
        } catch (_: Throwable) {
        }
    }
}

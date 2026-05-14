package com.camera.VendorTag.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 模块日志抓取工具。
 *
 * 作用：
 * 1. 使用模块自己的 root 权限在手机本机启动完整 logcat 抓取。
 * 2. 清理相机数据和强制停止相机不在这里重复写，统一走 CameraAppCleaner.clearCameraDataAndForceStopCamera()。
 * 3. 启动相机后继续保存完整日志，等价于电脑端执行：
 *    adb -s e4de68a3 logcat -b all -v threadtime > camera_log.txt
 * 4. 将完整日志、Hook 过滤日志、模块运行日志保存到 /storage/emulated/0/Android/Gurd/。
 */
public final class ModuleLogManager {

    public static final String CAMERA_PACKAGE = "com.oplus.camera";

    public static final String LOG_DIR = "/storage/emulated/0/Android/Gurd";

    public static final String CAMERA_FULL_LOG = "camera_log.txt";

    public static final String HOOK_STATUS_LOG = "hook_status.txt";

    public static final String MODULE_LOG = "module_log.txt";

    private static final String PID_FILE = "camera_logcat.pid";

    private static final String FULL_PATH = LOG_DIR + "/" + CAMERA_FULL_LOG;

    private static final String HOOK_PATH = LOG_DIR + "/" + HOOK_STATUS_LOG;

    private static final String MODULE_PATH = LOG_DIR + "/" + MODULE_LOG;

    private static final String PID_PATH = LOG_DIR + "/" + PID_FILE;

    private static final String HOOK_FILTER =
            "VendorTagHook|VendorTagJsonEditor|CameraUnitXp|ProtobufFeature|GrModeFix|LumoBlockFinal|"
                    + "hook success|hook failed|hook hit|hit oplus_camera_config|modify config|return modify|"
                    + "patch success|patched|failed|Throwable|Exception";

    private ModuleLogManager() {
    }

    public static OperationResult startCapture() {
        String command =
                "DIR='" + LOG_DIR + "'\n"
                        + "FULL='" + FULL_PATH + "'\n"
                        + "HOOK='" + HOOK_PATH + "'\n"
                        + "MODULE='" + MODULE_PATH + "'\n"
                        + "PID='" + PID_PATH + "'\n"
                        + "PKG='" + CAMERA_PACKAGE + "'\n"
                        + "mkdir -p \"$DIR\"\n"
                        + "chmod 777 \"$DIR\" 2>/dev/null || true\n"
                        + "run_cmd() {\n"
                        + "  DESC=\"$1\"\n"
                        + "  CMD=\"$2\"\n"
                        + "  echo '$ ' \"$DESC\" >> \"$MODULE\"\n"
                        + "  sh -c \"$CMD\" >> \"$MODULE\" 2>&1\n"
                        + "  CODE=$?\n"
                        + "  echo 'exitCode='\"$CODE\" >> \"$MODULE\"\n"
                        + "  return $CODE\n"
                        + "}\n"
                        + "stop_old_logcat() {\n"
                        + "  if [ -f \"$PID\" ]; then\n"
                        + "    OLD_PID=$(cat \"$PID\" 2>/dev/null)\n"
                        + "    if [ -n \"$OLD_PID\" ] && kill -0 \"$OLD_PID\" 2>/dev/null; then\n"
                        + "      echo 'kill old logcat pid='\"$OLD_PID\" >> \"$MODULE\"\n"
                        + "      kill \"$OLD_PID\" 2>/dev/null || true\n"
                        + "      sleep 1\n"
                        + "      kill -9 \"$OLD_PID\" 2>/dev/null || true\n"
                        + "    fi\n"
                        + "    rm -f \"$PID\"\n"
                        + "  fi\n"
                        + "}\n"
                        + "start_camera() {\n"
                        + "  START_CODE=1\n"
                        + "  LAUNCH=$(cmd package resolve-activity --brief \"$PKG\" 2>/dev/null | tail -n 1)\n"
                        + "  if echo \"$LAUNCH\" | grep -q '/'; then\n"
                        + "    run_cmd \"adb -s e4de68a3 shell am start -n $LAUNCH\" \"am start -n $LAUNCH\"\n"
                        + "    START_CODE=$?\n"
                        + "  fi\n"
                        + "  if [ $START_CODE -ne 0 ]; then\n"
                        + "    run_cmd \"adb -s e4de68a3 shell monkey -p $PKG -c android.intent.category.LAUNCHER 1\" \"monkey -p $PKG -c android.intent.category.LAUNCHER 1\"\n"
                        + "    START_CODE=$?\n"
                        + "  fi\n"
                        + "  return $START_CODE\n"
                        + "}\n"
                        + "stop_old_logcat\n"
                        + ": > \"$FULL\"\n"
                        + ": > \"$HOOK\"\n"
                        + ": > \"$MODULE\"\n"
                        + "echo '==== 模块日志抓取开始 '$(date '+%F %T')' ====' >> \"$MODULE\"\n"
                        + "echo '状态：相机数据已清理并强制停止，准备启动日志抓取' >> \"$MODULE\"\n"
                        + "echo '日志路径：" + LOG_DIR + "' >> \"$MODULE\"\n"
                        + "run_cmd \"adb -s e4de68a3 shell logcat -b all -c\" \"logcat -b all -c\"\n"
                        + "echo '$ adb -s e4de68a3 logcat -b all -v threadtime > " + CAMERA_FULL_LOG + "' >> \"$MODULE\"\n"
                        + "(logcat -b all -v threadtime >> \"$FULL\" 2>&1) &\n"
                        + "echo $! > \"$PID\"\n"
                        + "sleep 1\n"
                        + "start_camera\n"
                        + "START_RESULT=$?\n"
                        + "if [ $START_RESULT -ne 0 ]; then\n"
                        + "  echo '警告：日志正在抓取，但自动启动相机失败，请手动打开相机复现问题。' >> \"$MODULE\"\n"
                        + "else\n"
                        + "  echo '状态：正在抓取日志，请复现问题' >> \"$MODULE\"\n"
                        + "fi\n"
                        + "chmod 666 \"$FULL\" \"$HOOK\" \"$MODULE\" \"$PID\" 2>/dev/null || true\n"
                        + "echo '__MODULE_LOG_CAPTURE_STARTED__'\n";

        return runSuCommand(command);
    }

    public static OperationResult stopCaptureAndGenerateLogs() {
        String command =
                "DIR='" + LOG_DIR + "'\n"
                        + "FULL='" + FULL_PATH + "'\n"
                        + "HOOK='" + HOOK_PATH + "'\n"
                        + "MODULE='" + MODULE_PATH + "'\n"
                        + "PID='" + PID_PATH + "'\n"
                        + "mkdir -p \"$DIR\"\n"
                        + "echo '==== 停止抓取并生成 Hook 日志 '$(date '+%F %T')' ====' >> \"$MODULE\"\n"
                        + "if [ -f \"$PID\" ]; then\n"
                        + "  OLD_PID=$(cat \"$PID\" 2>/dev/null)\n"
                        + "  if [ -n \"$OLD_PID\" ] && kill -0 \"$OLD_PID\" 2>/dev/null; then\n"
                        + "    echo 'kill logcat pid='\"$OLD_PID\" >> \"$MODULE\"\n"
                        + "    kill \"$OLD_PID\" 2>/dev/null || true\n"
                        + "    sleep 1\n"
                        + "    kill -9 \"$OLD_PID\" 2>/dev/null || true\n"
                        + "  fi\n"
                        + "  rm -f \"$PID\"\n"
                        + "fi\n"
                        + "if [ -f \"$FULL\" ]; then\n"
                        + "  grep -aEi '" + HOOK_FILTER + "' \"$FULL\" > \"$HOOK\" 2>/dev/null || true\n"
                        + "else\n"
                        + "  : > \"$HOOK\"\n"
                        + "fi\n"
                        + "echo '' >> \"$MODULE\"\n"
                        + "echo '==== Hook 过滤日志追加开始 ====' >> \"$MODULE\"\n"
                        + "cat \"$HOOK\" >> \"$MODULE\" 2>/dev/null || true\n"
                        + "echo '==== Hook 过滤日志追加结束 ====' >> \"$MODULE\"\n"
                        + "chmod 666 \"$FULL\" \"$HOOK\" \"$MODULE\" 2>/dev/null || true\n"
                        + "echo '__MODULE_LOG_CAPTURE_STOPPED__'\n";

        return runSuCommand(command);
    }

    public static OperationResult clearLogs() {
        String command =
                "DIR='" + LOG_DIR + "'\n"
                        + "PID='" + PID_PATH + "'\n"
                        + "if [ -f \"$PID\" ]; then\n"
                        + "  OLD_PID=$(cat \"$PID\" 2>/dev/null)\n"
                        + "  if [ -n \"$OLD_PID\" ] && kill -0 \"$OLD_PID\" 2>/dev/null; then\n"
                        + "    kill \"$OLD_PID\" 2>/dev/null || true\n"
                        + "    sleep 1\n"
                        + "    kill -9 \"$OLD_PID\" 2>/dev/null || true\n"
                        + "  fi\n"
                        + "fi\n"
                        + "mkdir -p \"$DIR\"\n"
                        + "rm -f \"$DIR/" + CAMERA_FULL_LOG + "\" \"$DIR/" + HOOK_STATUS_LOG + "\" \"$DIR/" + MODULE_LOG + "\" \"$DIR/" + PID_FILE + "\"\n"
                        + "logcat -b all -c 2>/dev/null || true\n"
                        + "chmod 777 \"$DIR\" 2>/dev/null || true\n"
                        + "echo '__MODULE_LOG_CLEARED__'\n";

        return runSuCommand(command);
    }

    public static Status getStatus() {
        String command =
                "DIR='" + LOG_DIR + "'\n"
                        + "PID='" + PID_PATH + "'\n"
                        + "mkdir -p \"$DIR\"\n"
                        + "chmod 777 \"$DIR\" 2>/dev/null || true\n"
                        + "RUNNING=0\n"
                        + "if [ -f \"$PID\" ]; then\n"
                        + "  OLD_PID=$(cat \"$PID\" 2>/dev/null)\n"
                        + "  if [ -n \"$OLD_PID\" ] && kill -0 \"$OLD_PID\" 2>/dev/null; then RUNNING=1; fi\n"
                        + "fi\n"
                        + "echo '__RUNNING__='\"$RUNNING\"\n"
                        + "for NAME in '" + CAMERA_FULL_LOG + "' '" + HOOK_STATUS_LOG + "' '" + MODULE_LOG + "'; do\n"
                        + "  FILE=\"$DIR/$NAME\"\n"
                        + "  if [ -f \"$FILE\" ]; then\n"
                        + "    SIZE=$(wc -c < \"$FILE\" 2>/dev/null | tr -d ' ')\n"
                        + "    echo '__FILE__='\"$NAME|1|$SIZE\"\n"
                        + "  else\n"
                        + "    echo '__FILE__='\"$NAME|0|0\"\n"
                        + "  fi\n"
                        + "done\n"
                        + "echo '__LS_BEGIN__'\n"
                        + "ls -l \"$DIR\" 2>&1\n"
                        + "echo '__LS_END__'\n";

        OperationResult result = runSuCommand(command);
        return parseStatus(result);
    }

    private static Status parseStatus(OperationResult result) {
        Status status = new Status();
        status.rootSuccess = result.success;
        status.rawOutput = result.output;
        status.error = result.error;

        boolean readLs = false;
        StringBuilder ls = new StringBuilder();

        String[] lines = result.output.split("\\r?\\n");

        for (String line : lines) {
            if (line == null) {
                continue;
            }

            if (line.startsWith("__RUNNING__=")) {
                status.running = "1".equals(line.substring("__RUNNING__=".length()).trim());
                continue;
            }

            if (line.startsWith("__FILE__=")) {
                parseFileLine(status, line.substring("__FILE__=".length()));
                continue;
            }

            if ("__LS_BEGIN__".equals(line)) {
                readLs = true;
                continue;
            }

            if ("__LS_END__".equals(line)) {
                readLs = false;
                continue;
            }

            if (readLs) {
                ls.append(line).append('\n');
            }
        }

        status.listOutput = ls.toString().trim();
        status.summaryText = buildSummaryText(status);

        return status;
    }

    private static void parseFileLine(Status status, String text) {
        String[] parts = text.split("\\|");

        if (parts.length < 3) {
            return;
        }

        String name = parts[0];
        boolean exists = "1".equals(parts[1]);
        long size = 0L;

        try {
            size = Long.parseLong(parts[2].trim());
        } catch (Throwable ignored) {
        }

        if (CAMERA_FULL_LOG.equals(name)) {
            status.cameraLogExists = exists;
            status.cameraLogBytes = size;
        } else if (HOOK_STATUS_LOG.equals(name)) {
            status.hookLogExists = exists;
            status.hookLogBytes = size;
        } else if (MODULE_LOG.equals(name)) {
            status.moduleLogExists = exists;
            status.moduleLogBytes = size;
        }
    }

    private static String buildSummaryText(Status status) {
        StringBuilder builder = new StringBuilder();

        builder.append(CAMERA_FULL_LOG)
                .append(' ')
                .append(formatBytes(status.cameraLogBytes))
                .append('\n');

        builder.append(HOOK_STATUS_LOG)
                .append(' ')
                .append(formatBytes(status.hookLogBytes))
                .append('\n');

        builder.append(MODULE_LOG)
                .append(' ')
                .append(formatBytes(status.moduleLogBytes));

        return builder.toString();
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "0B";
        }

        if (bytes < 1024L) {
            return bytes + "B";
        }

        long kb = bytes / 1024L;

        if (kb < 1024L) {
            return kb + "K";
        }

        long mb = kb / 1024L;

        return mb + "M";
    }

    private static OperationResult runSuCommand(String command) {
        Process process = null;
        StringBuilder output = new StringBuilder();

        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }

            int exitCode = process.waitFor();

            return new OperationResult(
                    exitCode == 0,
                    exitCode,
                    output.toString(),
                    ""
            );
        } catch (Throwable t) {
            return new OperationResult(
                    false,
                    -1,
                    output.toString(),
                    String.valueOf(t)
            );
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static final class Status {

        public boolean rootSuccess;

        public boolean running;

        public boolean cameraLogExists;

        public boolean hookLogExists;

        public boolean moduleLogExists;

        public long cameraLogBytes;

        public long hookLogBytes;

        public long moduleLogBytes;

        public String summaryText = "";

        public String listOutput = "";

        public String rawOutput = "";

        public String error = "";
    }

    public static final class OperationResult {

        public final boolean success;

        public final int exitCode;

        public final String output;

        public final String error;

        public OperationResult(
                boolean success,
                int exitCode,
                String output,
                String error
        ) {
            this.success = success;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
        }
    }
}

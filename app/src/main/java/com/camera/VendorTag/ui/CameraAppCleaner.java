package com.camera.VendorTag.ui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * 相机操作工具。
 *
 * 需要 root / su 权限。
 */
public final class CameraAppCleaner {

    private static final String CAMERA_PACKAGE = "com.oplus.camera";

    private CameraAppCleaner() {
    }

    /**
     * 只强制停止相机。
     */
    public static Result forceStopCamera() {
        String command =
                "PKG='" + CAMERA_PACKAGE + "'\n"
                        + "am force-stop --user 0 $PKG 2>/dev/null || am force-stop $PKG 2>/dev/null || cmd activity force-stop $PKG\n";

        return runSuCommand(command);
    }

    /**
     * 强制停止相机、清理相机数据、再次强制停止并启动相机。
     */
    public static Result clearCameraDataAndForceStopCamera() {
        String command =
                "PKG='" + CAMERA_PACKAGE + "'\n"
                        + "OUT=''\n"
                        + "run_cmd() {\n"
                        + "  CMD=\"$1\"\n"
                        + "  echo '$ ' \"$CMD\"\n"
                        + "  sh -c \"$CMD\"\n"
                        + "  CODE=$?\n"
                        + "  echo 'exitCode='\"$CODE\"\n"
                        + "  return $CODE\n"
                        + "}\n"
                        + "force_stop_camera() {\n"
                        + "  run_cmd \"am force-stop --user 0 $PKG\"\n"
                        + "  CODE=$?\n"
                        + "  if [ $CODE -ne 0 ]; then run_cmd \"am force-stop $PKG\"; CODE=$?; fi\n"
                        + "  if [ $CODE -ne 0 ]; then run_cmd \"cmd activity force-stop $PKG\"; CODE=$?; fi\n"
                        + "  return $CODE\n"
                        + "}\n"
                        + "clear_camera_data() {\n"
                        + "  run_cmd \"pm clear --user 0 $PKG\"\n"
                        + "  CODE=$?\n"
                        + "  if [ $CODE -ne 0 ]; then run_cmd \"pm clear $PKG\"; CODE=$?; fi\n"
                        + "  if [ $CODE -ne 0 ]; then run_cmd \"cmd package clear --user 0 $PKG\"; CODE=$?; fi\n"
                        + "  if [ $CODE -ne 0 ]; then\n"
                        + "    echo 'pm clear 失败，开始 root 删除相机数据目录兜底'\n"
                        + "    run_cmd \"rm -rf /data/user/0/$PKG /data/user_de/0/$PKG /data/misc/profiles/cur/0/$PKG /data/misc/profiles/ref/$PKG /data/dalvik-cache/profiles/$PKG\"\n"
                        + "    CODE=$?\n"
                        + "  fi\n"
                        + "  return $CODE\n"
                        + "}\n"
                        + "start_camera() {\n"
                        + "  START_CODE=1\n"
                        + "  LAUNCH=$(cmd package resolve-activity --brief $PKG 2>/dev/null | tail -n 1)\n"
                        + "  if echo \"$LAUNCH\" | grep -q '/'; then\n"
                        + "    run_cmd \"am start -n $LAUNCH\"\n"
                        + "    START_CODE=$?\n"
                        + "  fi\n"
                        + "  if [ $START_CODE -ne 0 ]; then\n"
                        + "    run_cmd \"monkey -p $PKG -c android.intent.category.LAUNCHER 1\"\n"
                        + "    START_CODE=$?\n"
                        + "  fi\n"
                        + "  return $START_CODE\n"
                        + "}\n"
                        + "OK=1\n"
                        + "force_stop_camera || OK=0\n"
                        + "sleep 1\n"
                        + "clear_camera_data || OK=0\n"
                        + "sleep 1\n"
                        + "force_stop_camera || OK=0\n"
                        + "sleep 1\n"
                        + "if [ \"$OK\" != '1' ]; then\n"
                        + "  echo '__CAMERA_CLEAN_FAILED__'\n"
                        + "  exit 11\n"
                        + "fi\n"
                        + "start_camera || true\n"
                        + "echo '__CAMERA_CLEAN_AND_RESTART_DONE__'\n"
                        + "exit 0\n";

        return runSuCommand(command);
    }

    /**
     * 保留旧方法名，避免其他地方还在调用时报错。
     */
    public static Result clearAndForceStopCamera() {
        return clearCameraDataAndForceStopCamera();
    }

    private static Result runSuCommand(String command) {
        Result first = runProcess(new String[]{"su", "-c", command}, null);

        if (first.success) {
            return first;
        }

        Result second = runProcess(new String[]{"su", "0", "sh", "-c", command}, null);

        if (second.success) {
            return second.withOutput(first.output + second.output, first.error + second.error);
        }

        Result third = runProcess(new String[]{"su"}, command + "\nexit\n");

        if (third.success) {
            return third.withOutput(first.output + second.output + third.output, first.error + second.error + third.error);
        }

        return third.withOutput(first.output + second.output + third.output, first.error + second.error + third.error);
    }

    private static Result runProcess(String[] command, String stdin) {
        Process process = null;
        StringBuilder output = new StringBuilder();

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();

            if (stdin != null) {
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream())
                );
                writer.write(stdin);
                writer.flush();
                writer.close();
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }

            int exitCode = process.waitFor();

            return new Result(
                    exitCode == 0,
                    exitCode,
                    output.toString(),
                    ""
            );
        } catch (Throwable t) {
            return new Result(
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

    public static final class Result {

        public final boolean success;

        public final int exitCode;

        public final String output;

        public final String error;

        public Result(
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

        public Result withOutput(String newOutput, String newError) {
            return new Result(
                    success,
                    exitCode,
                    newOutput == null ? "" : newOutput,
                    newError == null ? "" : newError
            );
        }
    }
}

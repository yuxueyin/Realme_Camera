package com.camera.VendorTag.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 相机维护操作。
 *
 * 作用：
 * 1. 强停 com.oplus.camera。
 * 2. 清理 com.oplus.camera 数据。
 * 3. 对 com.oplus.camera 执行包优化。
 *
 * 注意：
 * 这些命令需要 root 权限。
 */
public final class CameraMaintenance {

    private static final String CAMERA_PACKAGE = "com.oplus.camera";

    private CameraMaintenance() {
    }

    public static Result clearAndOptimizeCamera() {
        StringBuilder log = new StringBuilder();

        boolean forceStopOk = runRootCommand(
                "am force-stop " + CAMERA_PACKAGE,
                log
        );

        boolean clearOk = runRootCommand(
                "pm clear " + CAMERA_PACKAGE,
                log
        );

        boolean compileOk = runRootCommand(
                "cmd package compile -m speed-profile -f " + CAMERA_PACKAGE,
                log
        );

        if (!compileOk) {
            compileOk = runRootCommand(
                    "cmd package compile -m speed -f " + CAMERA_PACKAGE,
                    log
            );
        }

        runRootCommand(
                "am force-stop " + CAMERA_PACKAGE,
                log
        );

        boolean success = forceStopOk && clearOk;

        return new Result(success, compileOk, log.toString());
    }

    private static boolean runRootCommand(String command, StringBuilder log) {
        Process process = null;

        try {
            log.append("\n$ su -c ").append(command).append("\n");

            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;

            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            log.append("exitCode=").append(exitCode).append("\n");

            return exitCode == 0;
        } catch (Throwable t) {
            log.append("error=").append(String.valueOf(t)).append("\n");
            return false;
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

        public final boolean optimizeSuccess;

        public final String log;

        public Result(boolean success, boolean optimizeSuccess, String log) {
            this.success = success;
            this.optimizeSuccess = optimizeSuccess;
            this.log = log == null ? "" : log;
        }
    }
}
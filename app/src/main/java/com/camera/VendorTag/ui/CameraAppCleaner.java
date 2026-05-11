package com.camera.VendorTag.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;

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
                "am force-stop " + CAMERA_PACKAGE;

        return runSuCommand(command);
    }

    /**
     * 清理相机数据，然后强制停止相机。
     */
    public static Result clearCameraDataAndForceStopCamera() {
        String command =
                "am force-stop " + CAMERA_PACKAGE
                        + " ; "
                        + "pm clear " + CAMERA_PACKAGE
                        + " ; "
                        + "am force-stop " + CAMERA_PACKAGE;

        return runSuCommand(command);
    }

    /**
     * 保留旧方法名，避免其他地方还在调用时报错。
     */
    public static Result clearAndForceStopCamera() {
        return clearCameraDataAndForceStopCamera();
    }

    private static Result runSuCommand(String command) {
        Process process = null;

        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});

            BufferedReader outputReader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            BufferedReader errorReader =
                    new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;

            while ((line = outputReader.readLine()) != null) {
                output.append(line).append('\n');
            }

            while ((line = errorReader.readLine()) != null) {
                error.append(line).append('\n');
            }

            int exitCode = process.waitFor();

            return new Result(
                    exitCode == 0,
                    exitCode,
                    output.toString(),
                    error.toString()
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
    }
}
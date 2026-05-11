package com.camera.VendorTag.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * LSP 激活状态 root 标记。
 *
 * 用途：
 * 1. Provider 写状态失败时，仍然可以通过 root 标记判断 LSP 是否真的加载过。
 * 2. 只要 XP 模块在 com.oplus.camera 进程加载，就写入 /data/local/tmp/lumo_lsp_active。
 */
public final class LspStatusMarker {

    private static final String MARKER_PATH = "/data/local/tmp/lumo_lsp_active";

    private static final long ACTIVE_TIMEOUT_MS =
            30L * 24L * 60L * 60L * 1000L;

    private LspStatusMarker() {
    }

    public static boolean markBySu() {
        long now = System.currentTimeMillis();

        String command =
                "echo "
                        + now
                        + " > "
                        + MARKER_PATH
                        + " ; chmod 666 "
                        + MARKER_PATH;

        Result result = runSuCommand(command);

        return result.success;
    }

    public static boolean isActiveBySu() {
        Result result = runSuCommand("cat " + MARKER_PATH);

        if (!result.success) {
            return false;
        }

        String value = result.output.trim();

        if (value.length() == 0) {
            return false;
        }

        try {
            long lastTime = Long.parseLong(value);
            long now = System.currentTimeMillis();

            return now - lastTime <= ACTIVE_TIMEOUT_MS;
        } catch (Throwable ignored) {
            return false;
        }
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

    private static final class Result {

        final boolean success;

        final int exitCode;

        final String output;

        final String error;

        Result(
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
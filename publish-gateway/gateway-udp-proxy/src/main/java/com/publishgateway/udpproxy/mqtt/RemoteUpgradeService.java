package com.publishgateway.udpproxy.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MQTT 远程升级执行器。
 */
@Slf4j
@Service
public class RemoteUpgradeService {

    @Value("${upgrade.executor.enabled:false}")
    private boolean executorEnabled;

    @Value("${upgrade.executor.work-dir:/opt/monitor-platform/upgrade}")
    private String workDir;

    @Value("${upgrade.executor.install-command:}")
    private String installCommand;

    @Value("${upgrade.executor.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${upgrade.executor.read-timeout-ms:60000}")
    private int readTimeoutMs;

    public Map<String, Object> execute(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("upgrade payload is empty");
        }
        String taskId = value(payload, "taskId");
        String packageId = value(payload, "packageId");
        String downloadUrl = value(payload, "downloadUrl");
        String sha256 = value(payload, "sha256");
        String targetVersion = value(payload, "targetVersion");
        if (isBlank(downloadUrl)) {
            throw new IllegalArgumentException("downloadUrl is required");
        }

        File taskDir = new File(workDir, safeName(isBlank(taskId) ? packageId : taskId));
        if (!taskDir.exists() && !taskDir.mkdirs()) {
            throw new IllegalStateException("cannot create upgrade work dir: " + taskDir.getAbsolutePath());
        }
        File packageFile = new File(taskDir, safeName(isBlank(packageId) ? "upgrade-package.bin" : packageId + ".pkg"));

        log.info("[REMOTE-UPGRADE] start download: taskId={}, packageId={}, url={}", taskId, packageId, downloadUrl);
        download(downloadUrl, packageFile);
        String actualSha256 = sha256(packageFile);
        if (!isBlank(sha256) && !sha256.equalsIgnoreCase(actualSha256)) {
            throw new IllegalStateException("sha256 mismatch, expected=" + sha256 + ", actual=" + actualSha256);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("packageId", packageId);
        result.put("targetVersion", targetVersion);
        result.put("file", packageFile.getAbsolutePath());
        result.put("sha256", actualSha256);
        result.put("executorEnabled", executorEnabled);

        if (!executorEnabled) {
            result.put("stage", "VERIFY_ONLY");
            result.put("message", "升级包已下载并校验，执行器未启用");
            log.warn("[REMOTE-UPGRADE] executor disabled, skip install: taskId={}, file={}",
                    taskId, packageFile.getAbsolutePath());
            return result;
        }

        if (isBlank(installCommand)) {
            throw new IllegalStateException("upgrade.executor.install-command is required when executor enabled");
        }
        int exitCode = install(packageFile, payload);
        if (exitCode != 0) {
            throw new IllegalStateException("install command exitCode=" + exitCode);
        }
        result.put("stage", "INSTALLED");
        result.put("message", "升级命令执行成功");
        result.put("exitCode", exitCode);
        return result;
    }

    private void download(String downloadUrl, File target) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(downloadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("download failed, httpStatus=" + code);
            }
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, len);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("download upgrade package failed: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private int install(File packageFile, Map<String, Object> payload) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    installCommand,
                    packageFile.getAbsolutePath(),
                    value(payload, "targetVersion"));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            return process.waitFor();
        } catch (Exception e) {
            throw new IllegalStateException("execute install command failed: " + e.getMessage(), e);
        }
    }

    private String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int len;
            try (FileInputStream inputStream = new FileInputStream(file)) {
                while ((len = inputStream.read(buffer)) > 0) {
                    digest.update(buffer, 0, len);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("calculate sha256 failed: " + e.getMessage(), e);
        }
    }

    private String value(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private String safeName(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.replace("\\", "_").replace("/", "_").replace("..", "_");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

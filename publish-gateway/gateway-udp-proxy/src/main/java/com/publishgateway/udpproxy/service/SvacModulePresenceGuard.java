package com.publishgateway.udpproxy.service;

import com.publishgateway.udpproxy.config.VAuthConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Independent physical gate for an external SVAC USB module.
 *
 * The guard only observes USB presence. It does not open, authenticate, close,
 * or otherwise alter the UKey used by VAuth.
 */
@Slf4j
@Component
public class SvacModulePresenceGuard {

    private static final long DEFAULT_SCAN_INTERVAL_MS = 200L;

    @Resource
    private VAuthConfig vAuthConfig;

    private volatile Snapshot lastSnapshot = Snapshot.disabled();
    private volatile long lastScanAtMs = 0L;

    public boolean ensureReady(String operation) {
        Snapshot snapshot = snapshot();
        if (snapshot.ready) {
            return true;
        }
        log.error("SVAC module gate refused {}: status={}, detail={}",
                operation, snapshot.status, snapshot.detail);
        return false;
    }

    public boolean isReady() {
        return snapshot().ready;
    }

    public String getStatus() {
        Snapshot snapshot = snapshot();
        return snapshot.status + ":" + snapshot.detail;
    }

    private Snapshot snapshot() {
        VAuthConfig.SvacModule config = moduleConfig();
        if (!Boolean.TRUE.equals(config.getRequired())) {
            return Snapshot.disabled();
        }

        long now = System.currentTimeMillis();
        long interval = config.getScanIntervalMs() != null && config.getScanIntervalMs() > 0
                ? config.getScanIntervalMs() : DEFAULT_SCAN_INTERVAL_MS;
        Snapshot cached = lastSnapshot;
        if (now - lastScanAtMs < interval) {
            return cached;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - lastScanAtMs < interval) {
                return lastSnapshot;
            }
            lastSnapshot = detect(config);
            lastScanAtMs = now;
            return lastSnapshot;
        }
    }

    private VAuthConfig.SvacModule moduleConfig() {
        if (vAuthConfig.getSvacModule() == null) {
            vAuthConfig.setSvacModule(new VAuthConfig.SvacModule());
        }
        return vAuthConfig.getSvacModule();
    }

    private Snapshot detect(VAuthConfig.SvacModule config) {
        if (!hasSelector(config)) {
            return Snapshot.blocked("SELECTOR_REQUIRED",
                    "configure vauth.svac-module.device-path or vendor-id+product-id/serial");
        }

        String devicePath = trim(config.getDevicePath());
        if (!devicePath.isEmpty()) {
            Path path = Paths.get(devicePath);
            if (Files.exists(path)) {
                return Snapshot.ready("DEVICE_PATH_PRESENT", devicePath);
            }
            return Snapshot.blocked("DEVICE_PATH_MISSING", devicePath);
        }

        Path root = Paths.get(defaultIfBlank(config.getSysfsRoot(), "/sys/bus/usb/devices"));
        if (!Files.isDirectory(root)) {
            return Snapshot.blocked("SYSFS_UNAVAILABLE", root.toString());
        }

        try (DirectoryStream<Path> devices = Files.newDirectoryStream(root)) {
            for (Path device : devices) {
                if (!Files.isDirectory(device)) {
                    continue;
                }
                if (matchesDevice(config, device)) {
                    return Snapshot.ready("USB_DEVICE_PRESENT", device.toString());
                }
            }
        } catch (IOException e) {
            return Snapshot.blocked("SYSFS_SCAN_FAILED", e.getMessage());
        }

        return Snapshot.blocked("USB_DEVICE_NOT_FOUND", selectorSummary(config));
    }

    private boolean hasSelector(VAuthConfig.SvacModule config) {
        if (!trim(config.getDevicePath()).isEmpty()) {
            return true;
        }
        if (!trim(config.getSerial()).isEmpty()) {
            return true;
        }
        return !trim(config.getVendorId()).isEmpty() && !trim(config.getProductId()).isEmpty();
    }

    private boolean matchesDevice(VAuthConfig.SvacModule config, Path device) {
        String vendorId = readFirstLine(device.resolve("idVendor"));
        String productId = readFirstLine(device.resolve("idProduct"));
        String serial = readFirstLine(device.resolve("serial"));

        if (!matchesHex(config.getVendorId(), vendorId)) {
            return false;
        }
        if (!matchesHex(config.getProductId(), productId)) {
            return false;
        }
        return matchesText(config.getSerial(), serial);
    }

    private String readFirstLine(Path path) {
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            String value = new String(bytes, StandardCharsets.UTF_8);
            int newline = value.indexOf('\n');
            if (newline >= 0) {
                value = value.substring(0, newline);
            }
            return value.trim();
        } catch (IOException e) {
            return "";
        }
    }

    private boolean matchesHex(String expected, String actual) {
        String expectedValue = normalizeHex(expected);
        if (expectedValue.isEmpty()) {
            return true;
        }
        return expectedValue.equals(normalizeHex(actual));
    }

    private boolean matchesText(String expected, String actual) {
        String expectedValue = trim(expected);
        if (expectedValue.isEmpty()) {
            return true;
        }
        return expectedValue.equals(trim(actual));
    }

    private String normalizeHex(String value) {
        String text = trim(value).toLowerCase();
        if (text.startsWith("0x")) {
            text = text.substring(2);
        }
        return text;
    }

    private String selectorSummary(VAuthConfig.SvacModule config) {
        return "vendorId=" + trim(config.getVendorId())
                + ", productId=" + trim(config.getProductId())
                + ", serial=" + trim(config.getSerial());
    }

    private String defaultIfBlank(String value, String fallback) {
        String text = trim(value);
        return text.isEmpty() ? fallback : text;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Snapshot {
        private final boolean ready;
        private final String status;
        private final String detail;

        private Snapshot(boolean ready, String status, String detail) {
            this.ready = ready;
            this.status = status;
            this.detail = detail;
        }

        private static Snapshot disabled() {
            return new Snapshot(true, "DISABLED", "svac module gate disabled");
        }

        private static Snapshot ready(String status, String detail) {
            return new Snapshot(true, status, detail);
        }

        private static Snapshot blocked(String status, String detail) {
            return new Snapshot(false, status, detail);
        }
    }
}

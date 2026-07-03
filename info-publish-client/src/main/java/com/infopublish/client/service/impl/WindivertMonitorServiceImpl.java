package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig.WindivertProperties;
import com.infopublish.client.service.UdpPortOwnerService;
import com.infopublish.client.service.WindivertMonitorService;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WinDivert FLOW 层旁路监听实现。
 *
 * <p>FLOW 层能提供 ProcessId，但不能拿到包体，也不能注入流量，符合 Phase 1
 * “只替换 PID 获取，不改变流量路径”的要求。
 */
@Slf4j
@Service
public class WindivertMonitorServiceImpl
        implements WindivertMonitorService, ApplicationRunner, Ordered {

    private static final int WINDIVERT_LAYER_FLOW = 2;
    private static final long WINDIVERT_FLAG_SNIFF = 1L;
    private static final long WINDIVERT_FLAG_RECV_ONLY = 4L;
    private static final int WINDIVERT_PARAM_QUEUE_LENGTH = 0;
    private static final int WINDIVERT_PARAM_QUEUE_TIME = 1;
    private static final int WINDIVERT_EVENT_FLOW_ESTABLISHED = 1;
    private static final int WINDIVERT_EVENT_FLOW_DELETED = 2;
    private static final int IPPROTO_UDP = 17;

    private static final int ADDRESS_SIZE = 128;
    private static final int FLOW_OFFSET = 16;
    private static final int FLOW_PROCESS_ID_OFFSET = FLOW_OFFSET + 16;
    private static final int FLOW_LOCAL_ADDR_OFFSET = FLOW_OFFSET + 20;
    private static final int FLOW_REMOTE_ADDR_OFFSET = FLOW_OFFSET + 36;
    private static final int FLOW_LOCAL_PORT_OFFSET = FLOW_OFFSET + 52;
    private static final int FLOW_REMOTE_PORT_OFFSET = FLOW_OFFSET + 54;
    private static final int FLOW_PROTOCOL_OFFSET = FLOW_OFFSET + 56;

    private static final int MAX_CACHE_SIZE = 20000;

    private interface WinDivertLibrary extends Library {
        Pointer WinDivertOpen(String filter, int layer, short priority, long flags);
        boolean WinDivertRecv(Pointer handle, Pointer packet, int packetLen, Pointer recvLen, Pointer addr);
        boolean WinDivertClose(Pointer handle);
        boolean WinDivertSetParam(Pointer handle, int param, long value);
        boolean WinDivertHelperFormatIPv6Address(Pointer addr, byte[] buffer, int bufferLen);
    }

    @Resource
    private WindivertProperties properties;

    @Resource
    private UdpPortOwnerService udpPortOwnerService;

    private final Map<String, CacheEntry> pidCache = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong eventsSeen = new AtomicLong();
    private final AtomicLong eventsRecorded = new AtomicLong();
    private final AtomicLong eventsDeleted = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong udpTableFallbackHits = new AtomicLong();
    private final AtomicLong udpTableFallbackMisses = new AtomicLong();
    private final AtomicLong recvErrors = new AtomicLong();

    private volatile WinDivertLibrary library;
    private volatile Pointer handle;
    private volatile ExecutorService executorService;
    private volatile Future<?> workerFuture;
    private volatile boolean available = false;
    private volatile long startedAt = 0L;
    private volatile long lastEventAt = 0L;
    private volatile String lastReason = "NOT_STARTED";
    private volatile String lastError;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isEnabled()) {
            startMonitor();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public PidLookupResult findPid(String sourceIp, int sourcePort) {
        if (!properties.isEnabled()) {
            return PidLookupResult.unavailable("WINDIVERT_DISABLED");
        }
        if (!available || !running.get()) {
            return PidLookupResult.unavailable(lastReason != null ? lastReason : "WINDIVERT_UNAVAILABLE");
        }
        if (sourceIp == null || sourcePort <= 0 || sourcePort > 65535) {
            cacheMisses.incrementAndGet();
            return PidLookupResult.miss("INVALID_ENDPOINT");
        }

        cleanupExpired(false);
        CacheEntry entry = pidCache.get(cacheKey(sourceIp, sourcePort));
        long now = System.currentTimeMillis();
        if (entry != null && entry.expireAt > now) {
            cacheHits.incrementAndGet();
            return PidLookupResult.hit(entry.localIp, entry.localPort,
                    entry.remoteIp, entry.remotePort, entry.pid, entry.eventTime);
        }
        PidLookupResult fallback = findPidFromUdpTable(sourceIp, sourcePort, now);
        if (fallback != null && fallback.isHit()) {
            return fallback;
        }
        cacheMisses.incrementAndGet();
        return PidLookupResult.miss("WINDIVERT_CACHE_MISS");
    }

    @Override
    public boolean isFallbackToUdpTableEnabled() {
        return properties.isFallbackToUdpTable();
    }

    @Override
    public Map<String, Object> getStatus() {
        cleanupExpired(false);
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("mode", properties.getMode());
        status.put("filter", properties.getFilter());
        status.put("dllPath", properties.getDllPath());
        status.put("running", running.get());
        status.put("available", available);
        status.put("fallbackToUdpTable", properties.isFallbackToUdpTable());
        status.put("cacheTtlMs", properties.getCacheTtlMs());
        status.put("deleteRetainMs", properties.getDeleteRetainMs());
        status.put("cacheSize", pidCache.size());
        status.put("eventsSeen", eventsSeen.get());
        status.put("eventsRecorded", eventsRecorded.get());
        status.put("eventsDeleted", eventsDeleted.get());
        status.put("cacheHits", cacheHits.get());
        status.put("cacheMisses", cacheMisses.get());
        status.put("udpTableFallbackHits", udpTableFallbackHits.get());
        status.put("udpTableFallbackMisses", udpTableFallbackMisses.get());
        status.put("recvErrors", recvErrors.get());
        status.put("startedAt", startedAt);
        status.put("lastEventAt", lastEventAt);
        status.put("lastReason", lastReason);
        status.put("lastError", lastError);
        return status;
    }

    @Override
    public void clear() {
        pidCache.clear();
        eventsSeen.set(0L);
        eventsRecorded.set(0L);
        eventsDeleted.set(0L);
        cacheHits.set(0L);
        cacheMisses.set(0L);
        udpTableFallbackHits.set(0L);
        udpTableFallbackMisses.set(0L);
        recvErrors.set(0L);
        lastError = null;
        log.info("[WinDivert] PID 缓存和统计计数已清空");
    }

    @PreDestroy
    public void destroy() {
        stopMonitor();
    }

    private void startMonitor() {
        if (!isWindows()) {
            markUnavailable("NOT_WINDOWS", "WinDivert 仅支持 Windows");
            return;
        }
        if (!isSupportedMonitorMode(properties.getMode())) {
            markUnavailable("UNSUPPORTED_MODE", "Phase 1 仅支持 monitor 模式: " + properties.getMode());
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            library = loadLibrary();
            String filter = normalizeFilter(properties.getFilter());
            Pointer opened = library.WinDivertOpen(
                    filter,
                    WINDIVERT_LAYER_FLOW,
                    (short) 0,
                    WINDIVERT_FLAG_SNIFF | WINDIVERT_FLAG_RECV_ONLY);
            if (opened == null || Pointer.nativeValue(opened) == -1L) {
                int err = Native.getLastError();
                throw new IllegalStateException("WinDivertOpen failed, lastError=" + err);
            }

            handle = opened;
            setParamQuietly(WINDIVERT_PARAM_QUEUE_LENGTH, properties.getQueueLength());
            setParamQuietly(WINDIVERT_PARAM_QUEUE_TIME, properties.getQueueTimeMs());

            available = true;
            startedAt = System.currentTimeMillis();
            lastReason = "RUNNING";
            lastError = null;
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "windivert-monitor");
                thread.setDaemon(true);
                return thread;
            });
            workerFuture = executorService.submit(this::receiveLoop);
            log.info("[WinDivert] Monitor 已启动: filter={}, cacheTtlMs={}", filter, properties.getCacheTtlMs());
        } catch (Throwable t) {
            running.set(false);
            closeHandleQuietly();
            markUnavailable("START_FAILED", t.getMessage());
            log.warn("[WinDivert] Monitor 启动失败，将使用回退来源校验: {}", t.getMessage());
        }
    }

    private void stopMonitor() {
        running.set(false);
        Future<?> future = workerFuture;
        if (future != null) {
            future.cancel(true);
        }
        ExecutorService service = executorService;
        if (service != null) {
            service.shutdownNow();
        }
        closeHandleQuietly();
        available = false;
        lastReason = "STOPPED";
    }

    private void receiveLoop() {
        Memory address = new Memory(ADDRESS_SIZE);
        while (running.get()) {
            try {
                address.clear();
                boolean ok = library.WinDivertRecv(handle, null, 0, null, address);
                if (!ok) {
                    int err = Native.getLastError();
                    recvErrors.incrementAndGet();
                    if (running.get()) {
                        lastError = "WinDivertRecv failed, lastError=" + err;
                        log.debug("[WinDivert] Recv 返回失败: {}", lastError);
                        sleepQuietly(100L);
                    }
                    continue;
                }
                eventsSeen.incrementAndGet();
                handleFlowEvent(address);
            } catch (Throwable t) {
                recvErrors.incrementAndGet();
                lastError = t.getMessage();
                log.warn("[WinDivert] 处理 FLOW 事件异常: {}", t.getMessage());
                sleepQuietly(100L);
            }
        }
    }

    private void handleFlowEvent(Memory address) {
        long flags = address.getLong(8);
        int layer = (int) (flags & 0xFFL);
        int event = (int) ((flags >> 8) & 0xFFL);
        boolean outbound = ((flags >> 17) & 0x01L) != 0;
        if (layer != WINDIVERT_LAYER_FLOW || !outbound) {
            return;
        }

        int protocol = address.getByte(FLOW_PROTOCOL_OFFSET) & 0xFF;
        if (protocol != IPPROTO_UDP) {
            return;
        }

        String localIp = formatAddress(address.share(FLOW_LOCAL_ADDR_OFFSET));
        String remoteIp = formatAddress(address.share(FLOW_REMOTE_ADDR_OFFSET));
        int localPort = address.getShort(FLOW_LOCAL_PORT_OFFSET) & 0xFFFF;
        int remotePort = address.getShort(FLOW_REMOTE_PORT_OFFSET) & 0xFFFF;
        if (localIp == null || localPort <= 0) {
            return;
        }

        if (event == WINDIVERT_EVENT_FLOW_DELETED) {
            retainDeletedFlow(localIp, localPort);
            eventsDeleted.incrementAndGet();
            return;
        }
        if (event != WINDIVERT_EVENT_FLOW_ESTABLISHED) {
            return;
        }

        long pid = address.getInt(FLOW_PROCESS_ID_OFFSET) & 0xFFFFFFFFL;
        long now = System.currentTimeMillis();
        long ttl = Math.max(1000L, properties.getCacheTtlMs());
        CacheEntry entry = new CacheEntry(localIp, localPort, remoteIp, remotePort, pid, now, now + ttl);
        pidCache.put(cacheKey(localIp, localPort), entry);
        lastEventAt = now;
        eventsRecorded.incrementAndGet();

        if (pidCache.size() > MAX_CACHE_SIZE) {
            cleanupExpired(true);
        }
        log.debug("[WinDivert] FLOW UDP: {}:{} -> {}:{}, pid={}",
                localIp, localPort, remoteIp, remotePort, pid);
    }

    private void retainDeletedFlow(String localIp, int localPort) {
        String key = cacheKey(localIp, localPort);
        CacheEntry old = pidCache.get(key);
        if (old == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long retainMs = Math.max(0L, properties.getDeleteRetainMs());
        if (retainMs <= 0L) {
            pidCache.remove(key, old);
            return;
        }
        long expireAt = Math.min(old.expireAt, now + retainMs);
        if (expireAt <= now) {
            pidCache.remove(key, old);
            return;
        }
        CacheEntry retained = new CacheEntry(
                old.localIp,
                old.localPort,
                old.remoteIp,
                old.remotePort,
                old.pid,
                old.eventTime,
                expireAt);
        pidCache.put(key, retained);
    }

    private PidLookupResult findPidFromUdpTable(String sourceIp, int sourcePort, long now) {
        if (!properties.isFallbackToUdpTable() || udpPortOwnerService == null || !udpPortOwnerService.isAvailable()) {
            return null;
        }
        String normalizedSourceIp = normalizeIp(sourceIp);
        UdpPortOwnerService.UdpEntry wildcard = null;
        try {
            List<UdpPortOwnerService.UdpEntry> entries = udpPortOwnerService.queryUdpTable();
            for (UdpPortOwnerService.UdpEntry entry : entries) {
                if (entry == null || entry.localPort != sourcePort || entry.owningPid <= 0) {
                    continue;
                }
                String localIp = normalizeIp(entry.localIp);
                if (normalizedSourceIp.equalsIgnoreCase(localIp)) {
                    return cacheUdpTableHit(sourceIp, sourcePort, entry.owningPid, now);
                }
                if ("0.0.0.0".equals(localIp)) {
                    wildcard = entry;
                }
            }
            if (wildcard != null) {
                return cacheUdpTableHit(sourceIp, sourcePort, wildcard.owningPid, now);
            }
        } catch (Throwable t) {
            lastError = "GetExtendedUdpTable fallback failed: " + t.getMessage();
            log.debug("[WinDivert] UDP table fallback failed: {}", t.getMessage());
        }
        udpTableFallbackMisses.incrementAndGet();
        return null;
    }

    private PidLookupResult cacheUdpTableHit(String sourceIp, int sourcePort, long pid, long now) {
        long ttl = Math.max(1000L, properties.getCacheTtlMs());
        CacheEntry entry = new CacheEntry(sourceIp, sourcePort, null, -1, pid, now, now + ttl);
        pidCache.put(cacheKey(sourceIp, sourcePort), entry);
        udpTableFallbackHits.incrementAndGet();
        return PidLookupResult.hit(sourceIp, sourcePort, pid, now);
    }

    private WinDivertLibrary loadLibrary() {
        String dllPath = trimToNull(properties.getDllPath());
        if (dllPath == null) {
            return Native.load("WinDivert", WinDivertLibrary.class);
        }

        File file = new File(dllPath);
        if (file.isDirectory()) {
            String existing = System.getProperty("jna.library.path", "");
            String path = file.getAbsolutePath() + (existing.isEmpty() ? "" : File.pathSeparator + existing);
            System.setProperty("jna.library.path", path);
            return Native.load("WinDivert", WinDivertLibrary.class);
        }
        return Native.load(file.getAbsolutePath(), WinDivertLibrary.class);
    }

    private void setParamQuietly(int param, long value) {
        if (value <= 0 || library == null || handle == null) {
            return;
        }
        try {
            boolean ok = library.WinDivertSetParam(handle, param, value);
            if (!ok) {
                log.debug("[WinDivert] 设置参数失败: param={}, value={}, lastError={}",
                        param, value, Native.getLastError());
            }
        } catch (Throwable t) {
            log.debug("[WinDivert] 设置参数异常: param={}, value={}, reason={}",
                    param, value, t.getMessage());
        }
    }

    private void closeHandleQuietly() {
        Pointer current = handle;
        handle = null;
        if (current != null && Pointer.nativeValue(current) != -1L && library != null) {
            try {
                library.WinDivertClose(current);
            } catch (Throwable ignored) {
                // ignore during shutdown
            }
        }
    }

    private void markUnavailable(String reason, String error) {
        available = false;
        lastReason = reason;
        lastError = error;
    }

    private String formatAddress(Pointer addrPointer) {
        try {
            byte[] buffer = new byte[64];
            if (library != null && library.WinDivertHelperFormatIPv6Address(addrPointer, buffer, buffer.length)) {
                String value = new String(buffer, StandardCharsets.US_ASCII).trim();
                int nul = value.indexOf('\0');
                if (nul >= 0) {
                    value = value.substring(0, nul);
                }
                return normalizeIp(value);
            }
        } catch (Throwable t) {
            log.debug("[WinDivert] 格式化 IP 地址失败: {}", t.getMessage());
        }
        return null;
    }

    private String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String value = ip.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("::ffff:")) {
            return value.substring(7);
        }
        if (lower.startsWith("0:0:0:0:0:ffff:")) {
            return value.substring("0:0:0:0:0:ffff:".length());
        }
        return value;
    }

    private String normalizeFilter(String filter) {
        String value = trimToNull(filter);
        return value != null ? value : "udp and outbound";
    }

    private void cleanupExpired(boolean forceTrim) {
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Map.Entry<String, CacheEntry>> iterator = pidCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CacheEntry> entry = iterator.next();
            if (entry.getValue().expireAt <= now || (forceTrim && pidCache.size() - removed > MAX_CACHE_SIZE)) {
                pidCache.remove(entry.getKey(), entry.getValue());
                removed++;
            }
        }
    }

    private String cacheKey(String ip, int port) {
        return normalizeIp(ip).toLowerCase(Locale.ROOT) + ":" + port;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private boolean isSupportedMonitorMode(String mode) {
        return "monitor".equalsIgnoreCase(mode)
                || "shadow".equalsIgnoreCase(mode)
                || "proxy".equalsIgnoreCase(mode);
    }

    private void sleepQuietly(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class CacheEntry {
        private final String localIp;
        private final int localPort;
        private final String remoteIp;
        private final int remotePort;
        private final long pid;
        private final long eventTime;
        private final long expireAt;

        private CacheEntry(String localIp, int localPort, String remoteIp, int remotePort,
                           long pid, long eventTime, long expireAt) {
            this.localIp = localIp;
            this.localPort = localPort;
            this.remoteIp = remoteIp;
            this.remotePort = remotePort;
            this.pid = pid;
            this.eventTime = eventTime;
            this.expireAt = expireAt;
        }
    }
}

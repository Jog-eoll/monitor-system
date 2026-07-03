package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig.WindivertProperties;
import com.infopublish.client.service.ProcessInfoResolver;
import com.infopublish.client.service.ProcessPolicyEngine;
import com.infopublish.client.service.WindivertMonitorService;
import com.infopublish.client.service.WindivertShadowService;
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
import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WinDivert Phase 2 shadow packet observer.
 */
@Slf4j
@Service
public class WindivertShadowServiceImpl
        implements WindivertShadowService, ApplicationRunner, Ordered {

    private static final int WINDIVERT_LAYER_NETWORK = 0;
    private static final long WINDIVERT_FLAG_SNIFF = 1L;
    private static final long WINDIVERT_FLAG_RECV_ONLY = 4L;
    private static final int WINDIVERT_PARAM_QUEUE_LENGTH = 0;
    private static final int WINDIVERT_PARAM_QUEUE_TIME = 1;
    private static final int WINDIVERT_EVENT_NETWORK_PACKET = 0;
    private static final int ADDRESS_SIZE = 128;
    private static final int IPPROTO_UDP = 17;
    private static final int MAX_PACKET_SIZE = 65575;

    private interface WinDivertLibrary extends Library {
        Pointer WinDivertOpen(String filter, int layer, short priority, long flags);
        boolean WinDivertRecv(Pointer handle, Pointer packet, int packetLen, Pointer recvLen, Pointer addr);
        boolean WinDivertClose(Pointer handle);
        boolean WinDivertSetParam(Pointer handle, int param, long value);
    }

    @Resource
    private WindivertProperties properties;

    @Resource
    private WindivertMonitorService windivertMonitorService;

    @Resource
    private ProcessInfoResolver processInfoResolver;

    @Resource
    private ProcessPolicyEngine processPolicyEngine;

    private final Object eventLock = new Object();
    private final ArrayDeque<ShadowPacketEvent> recentEvents = new ArrayDeque<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong packetsSeen = new AtomicLong();
    private final AtomicLong packetsRecorded = new AtomicLong();
    private final AtomicLong pidCacheHits = new AtomicLong();
    private final AtomicLong pidCacheMisses = new AtomicLong();
    private final AtomicLong pidCacheRetryHits = new AtomicLong();
    private final AtomicLong allowShadow = new AtomicLong();
    private final AtomicLong denyShadow = new AtomicLong();
    private final AtomicLong unknownShadow = new AtomicLong();
    private final AtomicLong parseErrors = new AtomicLong();
    private final AtomicLong recvErrors = new AtomicLong();

    private volatile WinDivertLibrary library;
    private volatile Pointer handle;
    private volatile ExecutorService executorService;
    private volatile Future<?> workerFuture;
    private volatile boolean available = false;
    private volatile long startedAt = 0L;
    private volatile long lastPacketAt = 0L;
    private volatile String lastReason = "NOT_STARTED";
    private volatile String lastError;

    @Override
    public void run(ApplicationArguments args) {
        if (shouldStart()) {
            startShadow();
        } else {
            lastReason = "SHADOW_DISABLED";
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", properties.isEnabled() && properties.getShadow().isEnabled());
        status.put("mode", properties.getMode());
        status.put("packetFilter", properties.getShadow().getPacketFilter());
        status.put("running", running.get());
        status.put("available", available);
        status.put("packetsSeen", packetsSeen.get());
        status.put("packetsRecorded", packetsRecorded.get());
        status.put("pidCacheHits", pidCacheHits.get());
        status.put("pidCacheMisses", pidCacheMisses.get());
        status.put("pidCacheRetryHits", pidCacheRetryHits.get());
        status.put("pidLookupRetryCount", sanitizedPidLookupRetryCount());
        status.put("pidLookupRetryDelayMs", sanitizedPidLookupRetryDelayMs());
        status.put("allowShadow", allowShadow.get());
        status.put("denyShadow", denyShadow.get());
        status.put("unknownShadow", unknownShadow.get());
        status.put("parseErrors", parseErrors.get());
        status.put("recvErrors", recvErrors.get());
        status.put("recentEventCount", eventCount());
        status.put("eventRetention", sanitizedEventRetention());
        status.put("startedAt", startedAt);
        status.put("lastPacketAt", lastPacketAt);
        status.put("lastReason", lastReason);
        status.put("lastError", lastError);
        status.put("processPolicy", processPolicyEngine.getStatus());
        status.put("processInfo", processInfoResolver.getStatus());
        return status;
    }

    @Override
    public List<ShadowPacketEvent> getRecentEvents(int limit) {
        int safeLimit = limit <= 0 ? 100 : Math.min(limit, 1000);
        synchronized (eventLock) {
            List<ShadowPacketEvent> all = new ArrayList<>(recentEvents);
            int from = Math.max(0, all.size() - safeLimit);
            return new ArrayList<>(all.subList(from, all.size()));
        }
    }

    @Override
    public void clear() {
        synchronized (eventLock) {
            recentEvents.clear();
        }
        packetsSeen.set(0L);
        packetsRecorded.set(0L);
        pidCacheHits.set(0L);
        pidCacheMisses.set(0L);
        pidCacheRetryHits.set(0L);
        allowShadow.set(0L);
        denyShadow.set(0L);
        unknownShadow.set(0L);
        parseErrors.set(0L);
        recvErrors.set(0L);
        lastError = null;
        processInfoResolver.clear();
        log.info("[WinDivert-Shadow] statistics cleared");
    }

    @PreDestroy
    public void destroy() {
        stopShadow();
    }

    private boolean shouldStart() {
        return properties.isEnabled()
                && properties.getShadow().isEnabled()
                && "shadow".equalsIgnoreCase(properties.getMode());
    }

    private void startShadow() {
        if (!isWindows()) {
            markUnavailable("NOT_WINDOWS", "WinDivert shadow only supports Windows");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            library = loadLibrary();
            String filter = normalizeFilter(properties.getShadow().getPacketFilter());
            Pointer opened = library.WinDivertOpen(
                    filter,
                    WINDIVERT_LAYER_NETWORK,
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
                Thread thread = new Thread(r, "windivert-shadow");
                thread.setDaemon(true);
                return thread;
            });
            workerFuture = executorService.submit(this::receiveLoop);
            log.info("[WinDivert-Shadow] started: filter={}, retention={}",
                    filter, sanitizedEventRetention());
        } catch (Throwable t) {
            running.set(false);
            closeHandleQuietly();
            markUnavailable("START_FAILED", t.getMessage());
            log.warn("[WinDivert-Shadow] start failed, shadow mode disabled: {}", t.getMessage());
        }
    }

    private void stopShadow() {
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
        int maxPacketSize = sanitizedMaxPacketSize();
        Memory packet = new Memory(maxPacketSize);
        Memory address = new Memory(ADDRESS_SIZE);
        Memory recvLen = new Memory(4);
        while (running.get()) {
            try {
                packet.clear();
                address.clear();
                recvLen.clear();
                boolean ok = library.WinDivertRecv(handle, packet, maxPacketSize, recvLen, address);
                if (!ok) {
                    int err = Native.getLastError();
                    recvErrors.incrementAndGet();
                    if (running.get()) {
                        lastError = "WinDivertRecv failed, lastError=" + err;
                        log.debug("[WinDivert-Shadow] recv failed: {}", lastError);
                        sleepQuietly(100L);
                    }
                    continue;
                }

                int packetLength = recvLen.getInt(0);
                packetsSeen.incrementAndGet();
                handlePacket(packet, packetLength, address);
            } catch (Throwable t) {
                recvErrors.incrementAndGet();
                lastError = t.getMessage();
                log.warn("[WinDivert-Shadow] packet handling failed: {}", t.getMessage());
                sleepQuietly(100L);
            }
        }
    }

    private void handlePacket(Memory packet, int packetLength, Memory address) {
        AddressFlags flags = parseAddressFlags(address);
        if (flags.layer != WINDIVERT_LAYER_NETWORK
                || flags.event != WINDIVERT_EVENT_NETWORK_PACKET
                || !flags.outbound) {
            return;
        }

        PacketInfo packetInfo = parseUdpPacket(packet, packetLength);
        if (packetInfo == null) {
            parseErrors.incrementAndGet();
            return;
        }

        WindivertMonitorService.PidLookupResult pidResult = findPidWithRetry(packetInfo);
        ProcessInfoResolver.ProcessInfo processInfo;
        boolean pidHit = pidResult.isHit();
        if (pidHit) {
            pidCacheHits.incrementAndGet();
            processInfo = processInfoResolver.resolve(pidResult.getPid());
        } else {
            pidCacheMisses.incrementAndGet();
            processInfo = ProcessInfoResolver.ProcessInfo.miss(-1L, pidResult.getReasonCode());
        }

        ProcessPolicyEngine.PolicyDecision decision = processPolicyEngine.decide(processInfo);
        recordDecisionCounter(decision.getDecision());

        ShadowPacketEvent event = new ShadowPacketEvent(
                System.currentTimeMillis(),
                packetInfo.sourceIp,
                packetInfo.sourcePort,
                packetInfo.destinationIp,
                packetInfo.destinationPort,
                packetInfo.packetLength,
                packetInfo.payloadLength,
                pidHit ? pidResult.getPid() : -1L,
                processInfo.getProcessName(),
                processInfo.getExecutablePath(),
                decision.getDecision(),
                decision.getReasonCode(),
                pidHit,
                flags.outbound,
                flags.impostor);
        addRecentEvent(event);
        packetsRecorded.incrementAndGet();
        lastPacketAt = event.getTimestamp();

        if (properties.getShadow().isLogUnknownProcess()
                && !"ALLOW_SHADOW".equals(decision.getDecision())) {
            log.warn("[WinDivert-Shadow] {} {}:{} -> {}:{}, pid={}, process={}, reason={}",
                    decision.getDecision(),
                    event.getSourceIp(),
                    event.getSourcePort(),
                    event.getDestinationIp(),
                    event.getDestinationPort(),
                    event.getPid(),
                    event.getProcessName(),
                    event.getReasonCode());
        } else {
            log.debug("[WinDivert-Shadow] {} {}:{} -> {}:{}, pid={}, process={}",
                    decision.getDecision(),
                    event.getSourceIp(),
                    event.getSourcePort(),
                    event.getDestinationIp(),
                    event.getDestinationPort(),
                    event.getPid(),
                    event.getProcessName());
        }
    }

    private WindivertMonitorService.PidLookupResult findPidWithRetry(PacketInfo packetInfo) {
        WindivertMonitorService.PidLookupResult result =
                windivertMonitorService.findPid(packetInfo.sourceIp, packetInfo.sourcePort);
        if (result.isHit()) {
            return result;
        }

        int retries = sanitizedPidLookupRetryCount();
        long delayMs = sanitizedPidLookupRetryDelayMs();
        for (int i = 0; i < retries && running.get(); i++) {
            sleepQuietly(delayMs);
            result = windivertMonitorService.findPid(packetInfo.sourceIp, packetInfo.sourcePort);
            if (result.isHit()) {
                pidCacheRetryHits.incrementAndGet();
                return result;
            }
        }
        return result;
    }

    private PacketInfo parseUdpPacket(Memory packet, int packetLength) {
        if (packet == null || packetLength < 28) {
            return null;
        }
        int version = (packet.getByte(0) >> 4) & 0x0F;
        if (version == 4) {
            return parseIpv4UdpPacket(packet, packetLength);
        }
        if (version == 6) {
            return parseIpv6UdpPacket(packet, packetLength);
        }
        return null;
    }

    private PacketInfo parseIpv4UdpPacket(Memory packet, int packetLength) {
        int firstByte = packet.getByte(0) & 0xFF;
        int headerLength = (firstByte & 0x0F) * 4;
        if (headerLength < 20 || packetLength < headerLength + 8) {
            return null;
        }
        int protocol = packet.getByte(9) & 0xFF;
        if (protocol != IPPROTO_UDP) {
            return null;
        }
        int frag = readUnsignedShort(packet, 6);
        int fragmentOffset = frag & 0x1FFF;
        if (fragmentOffset != 0) {
            return null;
        }

        int totalLength = readUnsignedShort(packet, 2);
        int effectiveLength = totalLength > 0 ? Math.min(totalLength, packetLength) : packetLength;
        int udpOffset = headerLength;
        if (effectiveLength < udpOffset + 8) {
            return null;
        }

        int sourcePort = readUnsignedShort(packet, udpOffset);
        int destinationPort = readUnsignedShort(packet, udpOffset + 2);
        int udpLength = readUnsignedShort(packet, udpOffset + 4);
        int payloadLength = Math.max(0, Math.min(udpLength, effectiveLength - udpOffset) - 8);
        return new PacketInfo(
                formatIpv4(packet, 12),
                sourcePort,
                formatIpv4(packet, 16),
                destinationPort,
                effectiveLength,
                payloadLength);
    }

    private PacketInfo parseIpv6UdpPacket(Memory packet, int packetLength) {
        if (packetLength < 48) {
            return null;
        }
        int nextHeader = packet.getByte(6) & 0xFF;
        if (nextHeader != IPPROTO_UDP) {
            return null;
        }

        int payloadLengthField = readUnsignedShort(packet, 4);
        int udpOffset = 40;
        int effectiveLength = Math.min(packetLength, udpOffset + payloadLengthField);
        if (effectiveLength < udpOffset + 8) {
            return null;
        }

        int sourcePort = readUnsignedShort(packet, udpOffset);
        int destinationPort = readUnsignedShort(packet, udpOffset + 2);
        int udpLength = readUnsignedShort(packet, udpOffset + 4);
        int payloadLength = Math.max(0, Math.min(udpLength, effectiveLength - udpOffset) - 8);
        return new PacketInfo(
                formatIpv6(packet, 8),
                sourcePort,
                formatIpv6(packet, 24),
                destinationPort,
                effectiveLength,
                payloadLength);
    }

    private void recordDecisionCounter(String decision) {
        if ("ALLOW_SHADOW".equals(decision)) {
            allowShadow.incrementAndGet();
        } else if ("DENY_SHADOW".equals(decision)) {
            denyShadow.incrementAndGet();
        } else {
            unknownShadow.incrementAndGet();
        }
    }

    private void addRecentEvent(ShadowPacketEvent event) {
        synchronized (eventLock) {
            recentEvents.addLast(event);
            int retention = sanitizedEventRetention();
            while (recentEvents.size() > retention) {
                recentEvents.removeFirst();
            }
        }
    }

    private int eventCount() {
        synchronized (eventLock) {
            return recentEvents.size();
        }
    }

    private AddressFlags parseAddressFlags(Memory address) {
        long flags = address.getLong(8);
        return new AddressFlags(
                (int) (flags & 0xFFL),
                (int) ((flags >> 8) & 0xFFL),
                ((flags >> 17) & 0x01L) != 0,
                ((flags >> 19) & 0x01L) != 0);
    }

    private int readUnsignedShort(Memory memory, int offset) {
        return ((memory.getByte(offset) & 0xFF) << 8)
                | (memory.getByte(offset + 1) & 0xFF);
    }

    private String formatIpv4(Memory packet, int offset) {
        return (packet.getByte(offset) & 0xFF) + "."
                + (packet.getByte(offset + 1) & 0xFF) + "."
                + (packet.getByte(offset + 2) & 0xFF) + "."
                + (packet.getByte(offset + 3) & 0xFF);
    }

    private String formatIpv6(Memory packet, int offset) {
        try {
            byte[] bytes = new byte[16];
            packet.read(offset, bytes, 0, bytes.length);
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception e) {
            return null;
        }
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
                log.debug("[WinDivert-Shadow] set param failed: param={}, value={}, lastError={}",
                        param, value, Native.getLastError());
            }
        } catch (Throwable t) {
            log.debug("[WinDivert-Shadow] set param exception: param={}, value={}, reason={}",
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

    private int sanitizedMaxPacketSize() {
        int configured = properties.getShadow().getMaxPacketSize();
        if (configured <= 0) {
            return 65535;
        }
        return Math.min(Math.max(configured, 68), MAX_PACKET_SIZE);
    }

    private int sanitizedEventRetention() {
        int configured = properties.getShadow().getEventRetention();
        if (configured <= 0) {
            return 100;
        }
        return Math.min(configured, 10000);
    }

    private int sanitizedPidLookupRetryCount() {
        int configured = properties.getShadow().getPidLookupRetryCount();
        if (configured <= 0) {
            return 0;
        }
        return Math.min(configured, 10);
    }

    private long sanitizedPidLookupRetryDelayMs() {
        long configured = properties.getShadow().getPidLookupRetryDelayMs();
        if (configured <= 0L) {
            return 0L;
        }
        return Math.min(configured, 200L);
    }

    private String normalizeFilter(String filter) {
        String value = trimToNull(filter);
        return value != null ? value : "udp and outbound";
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

    private void sleepQuietly(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class PacketInfo {
        private final String sourceIp;
        private final int sourcePort;
        private final String destinationIp;
        private final int destinationPort;
        private final int packetLength;
        private final int payloadLength;

        private PacketInfo(String sourceIp, int sourcePort,
                           String destinationIp, int destinationPort,
                           int packetLength, int payloadLength) {
            this.sourceIp = sourceIp;
            this.sourcePort = sourcePort;
            this.destinationIp = destinationIp;
            this.destinationPort = destinationPort;
            this.packetLength = packetLength;
            this.payloadLength = payloadLength;
        }
    }

    private static class AddressFlags {
        private final int layer;
        private final int event;
        private final boolean outbound;
        private final boolean impostor;

        private AddressFlags(int layer, int event, boolean outbound, boolean impostor) {
            this.layer = layer;
            this.event = event;
            this.outbound = outbound;
            this.impostor = impostor;
        }
    }
}

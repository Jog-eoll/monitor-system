package com.publishgateway.udpproxy.secure;

import com.publishgateway.udpproxy.config.SecurePublishProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurePublishIngressServiceImpl implements SecurePublishIngressService {

    private static final byte SYN_BYTE1 = 0x55;
    private static final byte SYN_TYPE2_SUM = (byte) 0xA7;
    private static final byte SYN_TYPE2_CRC = (byte) 0xA3;
    private static final byte SYN_TYPE3_SUM = (byte) 0xA8;
    private static final byte SYN_TYPE3_CRC = (byte) 0xA4;
    private static final int MAIN_CMD_WRITE = 0x02;
    private static final int SUB_CMD_WRITE_PATH = 0x08;
    private static final int SUB_CMD_WRITE_LARGE_PATH_CRC = 0x15;
    private static final int DEFAULT_STANDARD_PAYLOAD_SIZE = 768;
    private static final int MAX_STANDARD_PAYLOAD_SIZE = 65535;
    private static final int MAX_TRACKED_PACKETS = 1_000_000;
    private static final Charset GBK = Charset.forName("GBK");

    private final SecurePublishProperties properties;
    private final SecurePublishVerifierService verifierService;
    private final ConcurrentMap<String, PackageAssembly> assemblyCache = new ConcurrentHashMap<>();

    private volatile long lastCleanTime = System.currentTimeMillis();

    @Override
    public SecurePublishIngressDecision inspect(String ruleId, Long chainId, byte[] data, String sourceIp) {
        if (!properties.isEnabled()) {
            return SecurePublishIngressDecision.bypass();
        }
        SigmaFilePacket packet = parseSigmaFilePacket(data);
        if (packet == null || !isSecurePackagePath(packet.filePath)) {
            return SecurePublishIngressDecision.bypass();
        }

        cleanStaleAssemblies();
        if (packet.totalSize > maxPackageBytes()) {
            SecurePublishVerifyResult rejected = SecurePublishVerifyResult.rejected("PACKAGE_SIZE_NOT_ALLOWED");
            rejected.setPackageName(packet.fileName);
            log.warn("[SecurePublish] secure package declared too large, rejected: file={}, declared={}B, max={}B",
                    packet.filePath, packet.totalSize, maxPackageBytes());
            return SecurePublishIngressDecision.reject(rejected);
        }

        String cacheKey = safe(ruleId) + "|" + safe(sourceIp) + "|" + packet.sourceAddr + "|" + normalizePath(packet.filePath);
        PackageAssembly assembly = assemblyCache.get(cacheKey);
        if (assembly != null && assembly.isStale(properties.getStaleGapMs())) {
            assemblyCache.remove(cacheKey);
            log.warn("[SecurePublish] stale secure package assembly discarded: file={}, packets={}, bytes={}",
                    assembly.getFilePath(), assembly.getPacketCount(), assembly.getCurrentSize());
            assembly = null;
        }

        if (assembly == null) {
            PackageAssembly newAssembly = new PackageAssembly(packet.filePath, packet.standardPayloadSize,
                    packet.totalPackets, packet.totalSize);
            newAssembly.appendPacket(packet);
            assemblyCache.put(cacheKey, newAssembly);
            log.info("[SecurePublish] secure package receive started: file={}, packet={}/{}, payload={}B, standard={}B, firstBytes={}",
                    packet.filePath, packet.packetIndex, packet.totalPackets, packet.payload.length,
                    packet.standardPayloadSize, firstBytesHex(packet.payload));
            return completeOrHold(cacheKey, newAssembly, packet, sourceIp, chainId);
        }

        assembly.appendPacket(packet);
        if (assembly.getCurrentSize() > maxPackageBytes()) {
            assemblyCache.remove(cacheKey);
            SecurePublishVerifyResult rejected = SecurePublishVerifyResult.rejected("PACKAGE_SIZE_NOT_ALLOWED");
            rejected.setPackageName(packet.fileName);
            log.warn("[SecurePublish] secure package too large, rejected: file={}, bytes={}",
                    packet.filePath, assembly.getCurrentSize());
            return SecurePublishIngressDecision.reject(rejected);
        }

        return completeOrHold(cacheKey, assembly, packet, sourceIp, chainId);
    }

    private SecurePublishIngressDecision completeOrHold(String cacheKey, PackageAssembly assembly,
                                                        SigmaFilePacket packet, String sourceIp, Long chainId) {
        if (!assembly.isComplete(packet)) {
            return SecurePublishIngressDecision.hold(packet.fileName);
        }
        byte[] assembled = assembly.getAssembledData();
        assemblyCache.remove(cacheKey);
        if (!looksLikePackageStart(assembled)) {
            SecurePublishVerifyResult rejected = SecurePublishVerifyResult.rejected("PACKAGE_FORMAT_NOT_ALLOWED");
            rejected.setPackageName(packet.fileName);
            log.warn("[SecurePublish] secure package format rejected: file={}, packets={}, bytes={}, firstBytes={}",
                    packet.filePath, assembly.getPacketCount(), assembled.length, firstBytesHex(assembled));
            return SecurePublishIngressDecision.reject(rejected);
        }
        log.info("[SecurePublish] secure package receive completed: file={}, packets={}, bytes={}",
                packet.filePath, assembly.getPacketCount(), assembled.length);
        return verifyAsDecision(assembled, packet.fileName, sourceIp, chainId);
    }

    private SecurePublishIngressDecision verifyAsDecision(byte[] packageBytes, String packageName,
                                                          String sourceIp, Long chainId) {
        log.info("[SecurePublish] package verify started: package={}, bytes={}, sourceIp={}, chainId={}",
                packageName, packageBytes == null ? 0 : packageBytes.length, sourceIp, chainId);
        SecurePublishVerifyResult result = verifierService.verifyPackage(packageBytes, packageName, sourceIp, chainId);
        if (result.isAllowed()) {
            return SecurePublishIngressDecision.forward(result);
        }
        return SecurePublishIngressDecision.reject(result);
    }

    private SigmaFilePacket parseSigmaFilePacket(byte[] udpData) {
        if (udpData == null || udpData.length < 20) {
            return null;
        }
        if (udpData[0] != SYN_BYTE1) {
            return null;
        }
        byte syn2 = udpData[1];
        if (syn2 != SYN_TYPE2_SUM && syn2 != SYN_TYPE2_CRC
                && syn2 != SYN_TYPE3_SUM && syn2 != SYN_TYPE3_CRC) {
            return null;
        }
        int mainCmd = udpData[12] & 0xFF;
        int subCmd = udpData[13] & 0xFF;
        if (!isFileWriteCommand(mainCmd, subCmd)) {
            return null;
        }
        int dataLen = readUnsignedShort(udpData, 4);
        int sourceAddr = ByteBuffer.wrap(udpData, 6, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
        int argLen = udpData[14] & 0xFF;
        int argBytes = argLen * 4;
        int dataStartIndex = 16 + argBytes;
        if (argLen == 0 || udpData.length <= dataStartIndex || dataLen <= 0) {
            return null;
        }

        byte[] args = new byte[argBytes];
        System.arraycopy(udpData, 16, args, 0, argBytes);
        String filePath = extractFilePathFromArgs(args);
        if (filePath == null) {
            return null;
        }

        int payloadLen = Math.min(dataLen, udpData.length - dataStartIndex);
        if (payloadLen <= 0) {
            return null;
        }
        byte[] payload = new byte[payloadLen];
        System.arraycopy(udpData, dataStartIndex, payload, 0, payloadLen);

        SigmaFilePacket packet = new SigmaFilePacket();
        packet.sourceAddr = sourceAddr;
        packet.mainCmd = mainCmd;
        packet.subCmd = subCmd;
        packet.filePath = filePath;
        packet.fileName = extractFileName(filePath);
        packet.standardPayloadSize = parseStandardPayloadSize(args, subCmd);
        packet.totalSize = parseTotalSize(args);
        packet.totalPackets = parseTotalPackets(args, subCmd);
        packet.packetIndex = parsePacketIndex(args, subCmd);
        packet.payload = payload;
        return packet;
    }

    private boolean isFileWriteCommand(int mainCmd, int subCmd) {
        return mainCmd == MAIN_CMD_WRITE
                && (subCmd == SUB_CMD_WRITE_PATH || subCmd == SUB_CMD_WRITE_LARGE_PATH_CRC);
    }

    private String extractFilePathFromArgs(byte[] args) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length - 3; i++) {
            byte b = args[i];
            if (b >= 'A' && b <= 'Z' && args[i + 1] == ':' && args[i + 2] == '\\') {
                int endIndex = i;
                while (endIndex < args.length && args[endIndex] != 0x00) {
                    endIndex++;
                }
                if (endIndex > i + 3) {
                    return new String(args, i, endIndex - i, GBK);
                }
            }
        }
        return null;
    }

    private int parseStandardPayloadSize(byte[] args, int subCmd) {
        int size = 0;
        if (subCmd == SUB_CMD_WRITE_LARGE_PATH_CRC && args != null && args.length >= 8) {
            size = positiveInt(readUnsignedInt(args, 4), MAX_STANDARD_PAYLOAD_SIZE);
        } else if (args != null && args.length >= 6) {
            size = readUnsignedShort(args, 4);
        }
        return size > 0 && size <= MAX_STANDARD_PAYLOAD_SIZE ? size : DEFAULT_STANDARD_PAYLOAD_SIZE;
    }

    private long parseTotalSize(byte[] args) {
        if (args == null || args.length < 4) {
            return 0L;
        }
        return readUnsignedInt(args, 0);
    }

    private int parseTotalPackets(byte[] args, int subCmd) {
        if (subCmd == SUB_CMD_WRITE_LARGE_PATH_CRC && args != null && args.length >= 12) {
            return positiveInt(readUnsignedInt(args, 8), MAX_TRACKED_PACKETS);
        }
        if (args != null && args.length >= 8) {
            return readUnsignedShort(args, 6);
        }
        return 0;
    }

    private int parsePacketIndex(byte[] args, int subCmd) {
        if (subCmd == SUB_CMD_WRITE_LARGE_PATH_CRC && args != null && args.length >= 16) {
            return positiveInt(readUnsignedInt(args, 12), MAX_TRACKED_PACKETS);
        }
        if (args != null && args.length >= 10) {
            return readUnsignedShort(args, 8);
        }
        return 0;
    }

    private int positiveInt(long value, int max) {
        return value > 0 && value <= max ? (int) value : 0;
    }

    private int readUnsignedShort(byte[] bytes, int offset) {
        if (bytes == null || bytes.length < offset + 2) {
            return 0;
        }
        return ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    private long readUnsignedInt(byte[] bytes, int offset) {
        if (bytes == null || bytes.length < offset + 4) {
            return 0L;
        }
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    }

    private boolean isSecurePackagePath(String filePath) {
        String ext = extension(filePath);
        if (ext == null) {
            return false;
        }
        return configuredExtensions().contains(ext);
    }

    private Set<String> configuredExtensions() {
        Set<String> result = new HashSet<>();
        String configured = properties.getPackageExtensions();
        if (configured != null) {
            for (String item : configured.split(",")) {
                String value = safe(item).toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) {
                    result.add(value);
                }
            }
        }
        if (result.isEmpty()) {
            result.add("tar");
            result.add("spkg");
            result.add("zip");
        }
        return result;
    }

    private boolean looksLikePackageStart(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return false;
        }
        if (payload[0] == 'P' && payload[1] == 'K') {
            return true;
        }
        return looksLikeTarStart(payload);
    }

    private boolean looksLikeTarStart(byte[] payload) {
        if (payload == null || payload.length < 100) {
            return false;
        }
        String name = readTarName(payload);
        return "manifest.json".equals(name) || "signature.sig".equals(name) || "payload.bin".equals(name);
    }

    private String readTarName(byte[] payload) {
        int end = 0;
        int max = Math.min(payload.length, 100);
        while (end < max && payload[end] != 0x00) {
            end++;
        }
        if (end <= 0) {
            return "";
        }
        String name = new String(payload, 0, end, Charset.forName("UTF-8")).replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private String firstBytesHex(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        int len = Math.min(payload.length, 16);
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", payload[i] & 0xFF));
        }
        return sb.toString();
    }

    private String extension(String filePath) {
        String fileName = extractFileName(filePath);
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot >= fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String extractFileName(String filePath) {
        String value = safe(filePath);
        int slash = Math.max(value.lastIndexOf('\\'), value.lastIndexOf('/'));
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private void cleanStaleAssemblies() {
        long now = System.currentTimeMillis();
        if (now - lastCleanTime < 30_000L) {
            return;
        }
        lastCleanTime = now;
        long timeout = Math.max(30_000L, properties.getAssemblyTimeoutMs());
        assemblyCache.entrySet().removeIf(entry -> {
            PackageAssembly assembly = entry.getValue();
            boolean expired = assembly != null && assembly.isExpired(timeout);
            if (expired) {
                log.warn("[SecurePublish] secure package assembly expired: file={}, packets={}, bytes={}",
                        assembly.getFilePath(), assembly.getPacketCount(), assembly.getCurrentSize());
            }
            return expired;
        });
    }

    private int maxPackageBytes() {
        long max = Math.max(1L, properties.getMaxPackageSizeMb()) * 1024L * 1024L;
        return max > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) max;
    }

    private String normalizePath(String value) {
        return safe(value).replace('/', '\\').toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class SigmaFilePacket {
        private int sourceAddr;
        private int mainCmd;
        private int subCmd;
        private String filePath;
        private String fileName;
        private int standardPayloadSize;
        private long totalSize;
        private int totalPackets;
        private int packetIndex;
        private byte[] payload;
    }

    private static class PackageAssembly {
        private final String filePath;
        private final int standardPayloadSize;
        private final int expectedTotalPackets;
        private final long expectedTotalSize;
        private final Map<Integer, byte[]> packets = new TreeMap<>();
        private long lastPacketTime = System.currentTimeMillis();
        private int currentSize;
        private int nextFallbackPacketIndex = 1;

        private PackageAssembly(String filePath, int standardPayloadSize, int expectedTotalPackets,
                                long expectedTotalSize) {
            this.filePath = filePath;
            this.standardPayloadSize = standardPayloadSize;
            this.expectedTotalPackets = expectedTotalPackets;
            this.expectedTotalSize = expectedTotalSize;
        }

        private void appendPacket(SigmaFilePacket packet) {
            if (packet == null || packet.payload == null || packet.payload.length == 0) {
                return;
            }
            int packetIndex = packet.packetIndex > 0 ? packet.packetIndex : nextFallbackPacketIndex++;
            byte[] previous = packets.putIfAbsent(packetIndex, packet.payload);
            if (previous == null) {
                currentSize += packet.payload.length;
            }
            lastPacketTime = System.currentTimeMillis();
        }

        private byte[] getAssembledData() {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(currentSize);
            for (byte[] packet : packets.values()) {
                if (packet != null && packet.length > 0) {
                    buffer.write(packet, 0, packet.length);
                }
            }
            return buffer.toByteArray();
        }

        private boolean isComplete(SigmaFilePacket latestPacket) {
            if (expectedTotalPackets > 0) {
                if (packets.size() < expectedTotalPackets) {
                    return false;
                }
                for (int i = 1; i <= expectedTotalPackets; i++) {
                    if (!packets.containsKey(i)) {
                        return false;
                    }
                }
                return true;
            }
            if (expectedTotalSize > 0 && currentSize >= expectedTotalSize) {
                return true;
            }
            return latestPacket != null && latestPacket.payload != null
                    && latestPacket.payload.length < standardPayloadSize;
        }

        private boolean isExpired(long timeoutMs) {
            return System.currentTimeMillis() - lastPacketTime > timeoutMs;
        }

        private boolean isStale(long staleGapMs) {
            return System.currentTimeMillis() - lastPacketTime > Math.max(1000L, staleGapMs);
        }

        private String getFilePath() {
            return filePath;
        }

        private int getStandardPayloadSize() {
            return standardPayloadSize;
        }

        private int getCurrentSize() {
            return currentSize;
        }

        private int getPacketCount() {
            return packets.size();
        }
    }
}

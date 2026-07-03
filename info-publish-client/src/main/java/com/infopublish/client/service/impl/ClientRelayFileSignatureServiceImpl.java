package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig.RelayFileSignatureProperties;
import com.infopublish.client.config.AppConfig.TransparentProxyProperties;
import com.infopublish.client.entity.dto.ClientFileSignatureRecord;
import com.infopublish.client.entity.dto.FileSignatureManifest;
import com.infopublish.client.service.ClientFileEnvelopeSigner;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.ClientRelayFileSignatureService;
import com.infopublish.client.service.UkeyLifecycleManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientRelayFileSignatureServiceImpl implements ClientRelayFileSignatureService {

    private static final byte SYN_BYTE1 = 0x55;
    private static final byte SYN_TYPE2_SUM = (byte) 0xA7;
    private static final byte SYN_TYPE2_CRC = (byte) 0xA3;
    private static final byte SYN_TYPE3_SUM = (byte) 0xA8;
    private static final byte SYN_TYPE3_CRC = (byte) 0xA4;
    private static final int DEFAULT_STANDARD_PAYLOAD_SIZE = 768;
    private static final Charset GBK = Charset.forName("GBK");

    private final RelayFileSignatureProperties properties;
    private final TransparentProxyProperties proxyProperties;
    private final ClientFileEnvelopeSigner envelopeSigner;
    private final ClientAuthService clientAuthService;
    private final UkeyLifecycleManager lifecycleManager;
    private final RestTemplate restTemplate;

    private final ConcurrentMap<String, FileAssembly> assemblyCache = new ConcurrentHashMap<>();
    private final ExecutorService signerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "relay-file-signature-client");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicLong packetsSeen = new AtomicLong();
    private final AtomicLong filePacketsMatched = new AtomicLong();
    private final AtomicLong filesSeen = new AtomicLong();
    private final AtomicLong filesSigned = new AtomicLong();
    private final AtomicLong filesReported = new AtomicLong();
    private final AtomicLong signFailed = new AtomicLong();
    private final AtomicLong reportFailed = new AtomicLong();
    private final AtomicLong filesSkippedTooLarge = new AtomicLong();
    private final AtomicLong assembleSkipped = new AtomicLong();
    private final AtomicLong signingInProgress = new AtomicLong();

    private volatile long lastCleanTime = System.currentTimeMillis();
    private volatile String lastError;
    private volatile ClientFileSignatureRecord lastRecord;
    private volatile String lastSignStage;
    private volatile String lastSignFile;
    private volatile long lastSignStartedAt;
    private volatile long lastSignFinishedAt;

    @Override
    public void observePacket(String sourceIp, int sourcePort, String targetIp, int targetPort, byte[] udpPayload) {
        if (!properties.isEnabled() || !properties.isShadowAssembleEnabled()) {
            return;
        }
        packetsSeen.incrementAndGet();
        try {
            FileTransferResult result = parseFileTransfer(sourceIp, sourcePort, targetIp, targetPort, udpPayload);
            if (result == null) {
                return;
            }
            filePacketsMatched.incrementAndGet();
            if (result.action == ParseAction.REPORT && result.data != null && result.data.length > 0) {
                submitSignature(result);
            } else {
                assembleSkipped.incrementAndGet();
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            log.debug("[RelayFileSignature] client observe failed: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("mode", properties.getMode());
        status.put("auditOnly", properties.isAuditMode());
        status.put("shadowAssembleEnabled", properties.isShadowAssembleEnabled());
        status.put("reportEnabled", properties.isReportEnabled());
        status.put("gatewayUrl", buildGatewayUrl());
        status.put("maxFileSizeMb", properties.getMaxFileSizeMb());
        status.put("assemblyTimeoutMs", properties.getAssemblyTimeoutMs());
        status.put("staleGapMs", properties.getStaleGapMs());
        status.put("packetsSeen", packetsSeen.get());
        status.put("filePacketsMatched", filePacketsMatched.get());
        status.put("filesSeen", filesSeen.get());
        status.put("filesSigned", filesSigned.get());
        status.put("filesReported", filesReported.get());
        status.put("signFailed", signFailed.get());
        status.put("reportFailed", reportFailed.get());
        status.put("filesSkippedTooLarge", filesSkippedTooLarge.get());
        status.put("assembleSkipped", assembleSkipped.get());
        status.put("activeAssemblies", assemblyCache.size());
        status.put("signingInProgress", signingInProgress.get());
        status.put("lastSignStage", lastSignStage);
        status.put("lastSignFile", lastSignFile);
        status.put("lastSignStartedAt", lastSignStartedAt);
        status.put("lastSignFinishedAt", lastSignFinishedAt);
        status.put("lastError", lastError);
        status.put("lastRecord", lastRecord);
        return status;
    }

    @Override
    public void clear() {
        packetsSeen.set(0L);
        filePacketsMatched.set(0L);
        filesSeen.set(0L);
        filesSigned.set(0L);
        filesReported.set(0L);
        signFailed.set(0L);
        reportFailed.set(0L);
        filesSkippedTooLarge.set(0L);
        assembleSkipped.set(0L);
        assemblyCache.clear();
        signingInProgress.set(0L);
        lastSignStage = null;
        lastSignFile = null;
        lastSignStartedAt = 0L;
        lastSignFinishedAt = 0L;
        lastError = null;
        lastRecord = null;
    }

    @PreDestroy
    public void destroy() {
        signerExecutor.shutdownNow();
    }

    private void submitSignature(FileTransferResult result) {
        signerExecutor.submit(() -> signAndReport(result));
    }

    private void signAndReport(FileTransferResult result) {
        signingInProgress.incrementAndGet();
        filesSeen.incrementAndGet();
        lastSignStartedAt = System.currentTimeMillis();
        lastSignFinishedAt = 0L;
        lastSignFile = result.filePath;
        lastSignStage = "STARTED";
        long maxBytes = Math.max(1L, properties.getMaxFileSizeMb()) * 1024L * 1024L;
        try {
            if (result.data.length > maxBytes) {
                filesSkippedTooLarge.incrementAndGet();
                lastSignStage = "SKIPPED_TOO_LARGE";
                log.warn("[RelayFileSignature] skip large client file signature: file={}, size={}, maxBytes={}",
                        result.filePath, result.data.length, maxBytes);
                return;
            }

            lastSignStage = "BUILD_MANIFEST";
            FileSignatureManifest manifest = buildManifest(result);
            lastSignStage = "CANONICALIZE_MANIFEST";
            byte[] manifestBytes = manifest.toCanonicalBytes();
            lastSignStage = "SIGN_ENVELOPE";
            byte[] envelope = envelopeSigner.sign(manifestBytes);
            if (envelope == null || envelope.length == 0) {
                throw new IllegalStateException("signed envelope is empty");
            }
            filesSigned.incrementAndGet();

            lastSignStage = "BUILD_RECORD";
            ClientFileSignatureRecord record = buildRecord(result, manifest, envelope);
            lastRecord = record;
            if (properties.isReportEnabled()) {
                lastSignStage = "REPORT_RECORD";
                report(record);
                filesReported.incrementAndGet();
            }
            lastSignStage = "DONE";
            lastError = null;
            log.info("[RelayFileSignature] client file signed: file={}, hash={}, packets={}, reported={}",
                    result.filePath, manifest.getFileHash(), result.totalPackets, properties.isReportEnabled());
        } catch (Throwable e) {
            if (e instanceof ThreadDeath) {
                throw (ThreadDeath) e;
            }
            signFailed.incrementAndGet();
            lastSignStage = "FAILED_" + safeClassName(e);
            lastError = safeThrowableMessage(e);
            log.warn("[RelayFileSignature] client file signing failed but relay continues: file={}, error={}",
                    result.filePath, lastError, e);
        } finally {
            lastSignFinishedAt = System.currentTimeMillis();
            signingInProgress.decrementAndGet();
        }
    }

    private String safeThrowableMessage(Throwable e) {
        if (e == null) {
            return "unknown";
        }
        String message = e.getMessage();
        if (!isBlank(message)) {
            return e.getClass().getName() + ": " + message;
        }
        return e.getClass().getName();
    }

    private String safeClassName(Throwable e) {
        if (e == null) {
            return "UNKNOWN";
        }
        return e.getClass().getSimpleName();
    }

    private FileSignatureManifest buildManifest(FileTransferResult result) {
        long completedAt = System.currentTimeMillis();
        FileSignatureManifest manifest = new FileSignatureManifest();
        manifest.setAlgorithm(properties.getSignatureAlgorithm());
        manifest.setHashAlgorithm(properties.getHashAlgorithm());
        manifest.setRuleId("");
        manifest.setChainId(null);
        manifest.setSourceIp(result.sourceIp);
        manifest.setTargetIp(result.targetIp);
        manifest.setTargetPort(result.targetPort);
        manifest.setFileName(result.fileName);
        manifest.setFilePath(result.filePath);
        manifest.setFileSize(result.data.length);
        manifest.setTotalPackets(result.totalPackets);
        manifest.setFileHash(FileSignatureManifest.sha256Hex(result.data));
        manifest.setCompletedAt(completedAt);
        return manifest;
    }

    private ClientFileSignatureRecord buildRecord(FileTransferResult result, FileSignatureManifest manifest, byte[] envelope) {
        ClientFileSignatureRecord record = new ClientFileSignatureRecord();
        record.setClientId(clientAuthService.getAuthId());
        record.setClientCertId(lifecycleManager.getCurrentCertSerialNo());
        record.setFileId(buildFileId(manifest));
        record.setFileName(manifest.getFileName());
        record.setFilePath(manifest.getFilePath());
        record.setFileSize(manifest.getFileSize());
        record.setTotalPackets(manifest.getTotalPackets());
        record.setFileHash(manifest.getFileHash());
        record.setManifestJson(manifest.toCanonicalJson());
        record.setSignedEnvelopeBase64(Base64.getEncoder().encodeToString(envelope));
        record.setAlgorithm(manifest.getAlgorithm());
        record.setHashAlgorithm(manifest.getHashAlgorithm());
        record.setSourceIp(result.sourceIp);
        record.setSourcePort(result.sourcePort);
        record.setTargetIp(result.targetIp);
        record.setTargetPort(result.targetPort);
        record.setCompletedAt(manifest.getCompletedAt());
        return record;
    }

    private void report(ClientFileSignatureRecord record) {
        String url = buildGatewayUrl();
        if (isBlank(url)) {
            reportFailed.incrementAndGet();
            throw new IllegalStateException("gateway url is empty");
        }
        try {
            restTemplate.postForObject(url, record, Object.class);
        } catch (Exception e) {
            reportFailed.incrementAndGet();
            throw new IllegalStateException("report signature record failed: " + e.getMessage(), e);
        }
    }

    private String buildGatewayUrl() {
        String base = trimToNull(properties.getGatewayUrl());
        if (base == null) {
            String relayHost = trimToNull(proxyProperties.getRelayHost());
            if (relayHost == null || properties.getGatewayHttpPort() <= 0) {
                return "";
            }
            base = "http://" + relayHost + ":" + properties.getGatewayHttpPort();
        }
        String path = trimToNull(properties.getMetadataPath());
        if (path == null) {
            path = "/security/relay/file-signature/client-record";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private FileTransferResult parseFileTransfer(String sourceIp, int sourcePort,
                                                 String targetIp, int targetPort, byte[] udpData) {
        if (udpData == null || udpData.length < 20 || udpData[0] != SYN_BYTE1) {
            return null;
        }
        byte syn2 = udpData[1];
        if (syn2 != SYN_TYPE2_SUM && syn2 != SYN_TYPE2_CRC
                && syn2 != SYN_TYPE3_SUM && syn2 != SYN_TYPE3_CRC) {
            return null;
        }

        int sourceAddr = ByteBuffer.wrap(udpData, 6, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
        int argLen = udpData[14] & 0xFF;
        int argBytes = argLen * 4;
        int dataStartIndex = 16 + argBytes;
        if (argLen == 0 || udpData.length <= dataStartIndex) {
            return null;
        }

        byte[] args = new byte[argBytes];
        System.arraycopy(udpData, 16, args, 0, argBytes);
        String filePath = extractFilePathFromArgs(args);
        if (filePath == null) {
            return null;
        }

        int payloadLen = udpData.length - dataStartIndex;
        byte[] payload = new byte[payloadLen];
        System.arraycopy(udpData, dataStartIndex, payload, 0, payloadLen);

        int standardPayloadSize = parseStandardPayloadSize(args);
        cleanStaleAssemblies();

        String cacheKey = targetIp + ":" + targetPort + "_" + sourceAddr + "_" + filePath;
        String fileName = extractFileNameFromPath(filePath);
        FileAssembly assembly = assemblyCache.get(cacheKey);

        if (assembly != null && assembly.isStale(properties.getStaleGapMs())) {
            byte[] fullData = assembly.getAssembledData();
            int totalPackets = assembly.getPacketCount();
            assemblyCache.remove(cacheKey);
            return FileTransferResult.report(sourceIp, sourcePort, targetIp, targetPort,
                    filePath, fileName, fullData, totalPackets);
        }

        if (assembly == null) {
            if (payloadLen < standardPayloadSize) {
                return FileTransferResult.report(sourceIp, sourcePort, targetIp, targetPort,
                        filePath, fileName, payload, 1);
            }
            assembly = new FileAssembly(filePath, standardPayloadSize);
            assembly.appendPayload(payload);
            assemblyCache.put(cacheKey, assembly);
            return FileTransferResult.skip(filePath);
        }

        assembly.appendPayload(payload);
        if (assembly.getCurrentSize() > maxAssemblyBytes()) {
            assemblyCache.remove(cacheKey);
            filesSkippedTooLarge.incrementAndGet();
            return FileTransferResult.skip(filePath);
        }

        boolean isLastPacket = payloadLen < assembly.getStandardPayloadSize();
        if (!isLastPacket) {
            return FileTransferResult.skip(filePath);
        }

        byte[] fullData = assembly.getAssembledData();
        int totalPackets = assembly.getPacketCount();
        assemblyCache.remove(cacheKey);
        return FileTransferResult.report(sourceIp, sourcePort, targetIp, targetPort,
                filePath, fileName, fullData, totalPackets);
    }

    private void cleanStaleAssemblies() {
        long now = System.currentTimeMillis();
        if (now - lastCleanTime < 30000L) {
            return;
        }
        lastCleanTime = now;
        long timeoutMs = Math.max(1000L, properties.getAssemblyTimeoutMs());
        assemblyCache.entrySet().removeIf(entry -> entry.getValue().isExpired(timeoutMs));
    }

    private int parseStandardPayloadSize(byte[] args) {
        if (args != null && args.length >= 6) {
            int size = ByteBuffer.wrap(args, 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            if (size > 0 && size <= 4096) {
                return size;
            }
        }
        return DEFAULT_STANDARD_PAYLOAD_SIZE;
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

    private String extractFileNameFromPath(String filePath) {
        if (filePath == null) {
            return null;
        }
        int lastSlash = filePath.lastIndexOf('\\');
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return filePath.substring(lastSlash + 1);
        }
        int lastForward = filePath.lastIndexOf('/');
        if (lastForward >= 0 && lastForward < filePath.length() - 1) {
            return filePath.substring(lastForward + 1);
        }
        return filePath;
    }

    private long maxAssemblyBytes() {
        return Math.max(1L, properties.getMaxFileSizeMb()) * 1024L * 1024L;
    }

    private String buildFileId(FileSignatureManifest manifest) {
        String seed = manifest.getFileHash() + "|" + manifest.getFilePath() + "|" + manifest.getCompletedAt();
        String hash = FileSignatureManifest.sha256Hex(seed.getBytes(StandardCharsets.UTF_8));
        return isBlank(hash) ? UUID.randomUUID().toString() : hash;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private enum ParseAction {
        REPORT,
        SKIP
    }

    private static class FileAssembly {
        private final String filePath;
        private final int standardPayloadSize;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private long lastPacketTime = System.currentTimeMillis();
        private int packetCount = 0;

        FileAssembly(String filePath, int standardPayloadSize) {
            this.filePath = filePath;
            this.standardPayloadSize = standardPayloadSize;
        }

        void appendPayload(byte[] payload) {
            buffer.write(payload, 0, payload.length);
            packetCount++;
            lastPacketTime = System.currentTimeMillis();
        }

        byte[] getAssembledData() {
            return buffer.toByteArray();
        }

        int getCurrentSize() {
            return buffer.size();
        }

        int getPacketCount() {
            return packetCount;
        }

        int getStandardPayloadSize() {
            return standardPayloadSize;
        }

        boolean isExpired(long timeoutMs) {
            return System.currentTimeMillis() - lastPacketTime > timeoutMs;
        }

        boolean isStale(long staleGapMs) {
            return System.currentTimeMillis() - lastPacketTime > staleGapMs;
        }
    }

    private static class FileTransferResult {
        private ParseAction action;
        private String sourceIp;
        private int sourcePort;
        private String targetIp;
        private int targetPort;
        private String filePath;
        private String fileName;
        private byte[] data;
        private int totalPackets;

        static FileTransferResult skip(String filePath) {
            FileTransferResult result = new FileTransferResult();
            result.action = ParseAction.SKIP;
            result.filePath = filePath;
            return result;
        }

        static FileTransferResult report(String sourceIp, int sourcePort, String targetIp, int targetPort,
                                         String filePath, String fileName, byte[] data, int totalPackets) {
            FileTransferResult result = new FileTransferResult();
            result.action = ParseAction.REPORT;
            result.sourceIp = sourceIp;
            result.sourcePort = sourcePort;
            result.targetIp = targetIp;
            result.targetPort = targetPort;
            result.filePath = filePath;
            result.fileName = fileName;
            result.data = data;
            result.totalPackets = totalPackets;
            return result;
        }
    }
}

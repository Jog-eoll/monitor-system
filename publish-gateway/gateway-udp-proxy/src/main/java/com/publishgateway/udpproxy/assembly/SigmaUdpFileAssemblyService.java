package com.publishgateway.udpproxy.assembly;

import com.publishgateway.udpproxy.secure.jpeg.SecurePublishJpegSegmentUtil;
import com.publishgateway.udpproxy.secure.jpeg.SecurePublishJpegSegmentUtil.SecurePublishJpegBlockInfo;
import com.publishgateway.udpproxy.secure.jpeg.SecurePublishJpegSignatureService;
import com.publishgateway.udpproxy.secure.jpeg.SecurePublishJpegVerifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 青松 Sigma UDP 文件分片重组服务。
 */
@Slf4j
@Service
public class SigmaUdpFileAssemblyService implements UdpFileAssemblyService {

    private static final byte SYN_BYTE1 = 0x55;
    private static final byte SYN_TYPE2_SUM = (byte) 0xA7;
    private static final byte SYN_TYPE2_CRC = (byte) 0xA3;
    private static final byte SYN_TYPE3_SUM = (byte) 0xA8;
    private static final byte SYN_TYPE3_CRC = (byte) 0xA4;

    private static final Charset GBK = Charset.forName("GBK");
    private static final int DEFAULT_STANDARD_PAYLOAD_SIZE = 768;
    private static final int MAX_ASSEMBLY_SIZE = 20 * 1024 * 1024;
    private static final int MAX_VIDEO_ASSEMBLY_SIZE = 500 * 1024 * 1024;
    private static final long ASSEMBLY_TIMEOUT_MS = 120_000L;
    private static final long STALE_GAP_MS = 30_000L;

    private final Map<String, FileAssembly> assemblyCache = new ConcurrentHashMap<>();
    private final Object assemblyLock = new Object();
    private volatile long lastCleanTime = System.currentTimeMillis();

    @Resource
    private AssembledFileStore assembledFileStore;

    @Resource
    private SecurePublishJpegSignatureService securePublishJpegSignatureService;

    @Override
    public FileAssemblyResult accept(FileAssemblyRequest request) {
        if (request == null || request.getPayload() == null || request.getPayload().length == 0) {
            return FileAssemblyResult.ignored("EMPTY_PAYLOAD");
        }
        if (!isSigma(request.getManufacturer())) {
            return FileAssemblyResult.ignored("UNSUPPORTED_MANUFACTURER");
        }

        try {
            return parseAndAssemble(request);
        } catch (Exception e) {
            log.warn("【Sigma文件重组】处理异常: ruleId={}, error={}", request.getRuleId(), e.getMessage(), e);
            return FileAssemblyResult.failed(e.getMessage());
        }
    }

    private FileAssemblyResult parseAndAssemble(FileAssemblyRequest request) {
        byte[] udpData = request.getPayload();
        if (udpData.length < 20 || udpData[0] != SYN_BYTE1) {
            return FileAssemblyResult.ignored("NOT_SIGMA_FILE_PACKET");
        }
        byte syn2 = udpData[1];
        if (syn2 != SYN_TYPE2_SUM && syn2 != SYN_TYPE2_CRC
                && syn2 != SYN_TYPE3_SUM && syn2 != SYN_TYPE3_CRC) {
            return FileAssemblyResult.ignored("NOT_SIGMA_FILE_PACKET");
        }

        int sourceAddr = ByteBuffer.wrap(udpData, 6, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
        int groupAddr = udpData[8] & 0xFF;
        int unitAddr = udpData[9] & 0xFF;
        int argLen = udpData[14] & 0xFF;
        int argBytes = argLen * 4;
        int dataStartIndex = 16 + argBytes;
        if (argLen == 0 || udpData.length <= dataStartIndex) {
            return FileAssemblyResult.ignored("INVALID_SIGMA_ARGS");
        }

        byte[] args = new byte[argBytes];
        System.arraycopy(udpData, 16, args, 0, argBytes);
        String filePath = extractFilePathFromArgs(args);
        if (filePath == null || filePath.isEmpty()) {
            return FileAssemblyResult.ignored("FILE_PATH_NOT_FOUND");
        }

        int payloadLen = udpData.length - dataStartIndex;
        byte[] payload = new byte[payloadLen];
        System.arraycopy(udpData, dataStartIndex, payload, 0, payloadLen);

        int standardPayloadSize = parseStandardPayloadSize(args);
        String fileName = extractFileNameFromPath(filePath);
        String extension = getFileExtension(fileName);
        String destAddress = groupAddr + ":" + unitAddr;
        String cacheKey = buildCacheKey(request, sourceAddr, filePath);

        synchronized (assemblyLock) {
            cleanStaleAssemblies();

            FileAssembly assembly = assemblyCache.get(cacheKey);
            if (assembly != null && assembly.isStale()) {
                byte[] fullData = assembly.getAssembledData();
                int totalPackets = assembly.getPacketCount();
                assemblyCache.remove(cacheKey);
                log.info("【Sigma文件重组】静默完成: file={}, packets={}, size={}KB",
                        filePath, totalPackets, fullData.length / 1024);
                return buildCompletedResult(request, filePath, fileName, extension, fullData, totalPackets,
                        sourceAddr, destAddress, standardPayloadSize);
            }

            if (assembly == null) {
                if (payloadLen < standardPayloadSize) {
                    log.info("【Sigma文件重组】单包完成: file={}, size={}B", filePath, payloadLen);
                    return buildCompletedResult(request, filePath, fileName, extension, payload, 1,
                            sourceAddr, destAddress, standardPayloadSize);
                }

                assembly = new FileAssembly(filePath, standardPayloadSize);
                assembly.appendPayload(payload);
                assemblyCache.put(cacheKey, assembly);
                return FileAssemblyResult.assembling(filePath, "ASSEMBLY_STARTED");
            }

            assembly.appendPayload(payload);
            int maxSize = maxAssemblySize(extension);
            if (assembly.getCurrentSize() > maxSize) {
                assemblyCache.remove(cacheKey);
                log.warn("【Sigma文件重组】文件超过限制，丢弃: file={}, size={}MB, max={}MB",
                        filePath, assembly.getCurrentSize() / (1024 * 1024), maxSize / (1024 * 1024));
                return FileAssemblyResult.discarded(filePath, "ASSEMBLY_SIZE_EXCEEDED");
            }

            boolean lastPacket = payloadLen < assembly.getStandardPayloadSize();
            if (!lastPacket) {
                return FileAssemblyResult.assembling(filePath, "ASSEMBLING");
            }

            byte[] fullData = assembly.getAssembledData();
            int totalPackets = assembly.getPacketCount();
            assemblyCache.remove(cacheKey);
            log.info("【Sigma文件重组】完成: file={}, packets={}, size={}KB",
                    filePath, totalPackets, fullData.length / 1024);
            return buildCompletedResult(request, filePath, fileName, extension, fullData, totalPackets,
                    sourceAddr, destAddress, standardPayloadSize);
        }
    }

    private FileAssemblyResult buildCompletedResult(FileAssemblyRequest request,
                                                    String filePath,
                                                    String fileName,
                                                    String extension,
                                                    byte[] fileData,
                                                    int totalPackets,
                                                    int sourceAddr,
                                                    String destAddress,
                                                    int standardPayloadSize) {
        SecurePublishJpegBlockInfo secureBlock = inspectSecurePublishBlock(extension, fileData);
        SecurePublishJpegVerifyResult verifyResult = verifySecurePublishJpeg(extension, fileData, fileName);
        String fileSha256 = sha256Hex(fileData);
        AssembledFile file = AssembledFile.builder()
                .ruleId(request.getRuleId())
                .chainId(request.getChainId())
                .manufacturer("sigma")
                .sourceIp(request.getSourceIp())
                .sourcePort(request.getSourcePort())
                .targetIp(request.getTargetIp())
                .targetPort(request.getTargetPort())
                .filePath(filePath)
                .fileName(fileName)
                .fileExtension(extension)
                .contentType(resolveContentType(extension))
                .fileBytes(fileData)
                .sha256(fileSha256)
                .securePublishBlockPresent(secureBlock.getPresent())
                .securePublishSegmentType(secureBlock.getSegmentType())
                .securePublishPayloadSha256(secureBlock.getPayloadSha256())
                .securePublishStrippedPayloadSha256(secureBlock.getStrippedPayloadSha256())
                .securePublishPayloadHashMatched(secureBlock.getPayloadHashMatched())
                .securePublishSignatureAlgorithm(secureBlock.getSignatureAlgorithm())
                .securePublishSignature(secureBlock.getSignature())
                .securePublishCreatedAt(secureBlock.getCreatedAt())
                .securePublishError(secureBlock.getError())
                .securePublishSigned(verifyResult != null ? verifyResult.isSigned() : null)
                .securePublishVerified(verifyResult != null ? verifyResult.isVerified() : null)
                .securePublishAllowed(verifyResult != null ? verifyResult.isAllowed() : null)
                .securePublishAuditOnly(verifyResult != null ? verifyResult.isAuditOnly() : null)
                .securePublishVerifyMode(verifyResult != null ? verifyResult.getMode() : null)
                .securePublishVerifyReason(verifyResult != null ? verifyResult.getReason() : null)
                .securePublishKeyId(verifyResult != null ? verifyResult.getKeyId() : null)
                .securePublishSignTime(verifyResult != null ? verifyResult.getSignTime() : null)
                .securePublishExpireTime(verifyResult != null ? verifyResult.getExpireTime() : null)
                .totalPackets(totalPackets)
                .totalSize(fileData.length)
                .protocolSourceAddr(String.format("0x%04X", sourceAddr))
                .protocolDestAddr(destAddress)
                .standardPayloadSize(standardPayloadSize)
                .completedAt(System.currentTimeMillis())
                .build();
        logSecurePublishJpegResult(file);
        saveCompletedFile(file);
        return FileAssemblyResult.completed(file);
    }

    private SecurePublishJpegVerifyResult verifySecurePublishJpeg(String extension, byte[] fileData, String fileName) {
        if (!isJpegExtension(extension) || securePublishJpegSignatureService == null
                || fileData == null || fileData.length == 0) {
            return null;
        }
        return securePublishJpegSignatureService.verify(fileData, fileName);
    }

    private SecurePublishJpegBlockInfo inspectSecurePublishBlock(String extension, byte[] fileData) {
        SecurePublishJpegBlockInfo empty = new SecurePublishJpegBlockInfo();
        empty.setPresent(false);
        if (!isJpegExtension(extension) || fileData == null || fileData.length == 0) {
            return empty;
        }
        return SecurePublishJpegSegmentUtil.inspect(fileData);
    }

    private void logSecurePublishJpegResult(AssembledFile file) {
        if (file == null || !isJpegExtension(file.getFileExtension())) {
            return;
        }
        if (Boolean.TRUE.equals(file.getSecurePublishBlockPresent())) {
            log.info("[SecurePublish-JPEG] assembled jpg captured: file={}, sha256={}, segment={}, payloadSha256={}, strippedPayloadSha256={}, payloadHashMatched={}, verified={}, allowed={}, reason={}",
                    file.getFileName(), file.getSha256(), file.getSecurePublishSegmentType(),
                    file.getSecurePublishPayloadSha256(), file.getSecurePublishStrippedPayloadSha256(),
                    file.getSecurePublishPayloadHashMatched(), file.getSecurePublishVerified(),
                    file.getSecurePublishAllowed(), file.getSecurePublishVerifyReason());
            return;
        }
        if (file.getSecurePublishError() != null && !file.getSecurePublishError().trim().isEmpty()) {
            log.warn("[SecurePublish-JPEG] assembled jpg inspect failed: file={}, sha256={}, error={}",
                    file.getFileName(), file.getSha256(), file.getSecurePublishError());
            return;
        }
        log.info("[SecurePublish-JPEG] assembled jpg captured without SecurePublish block: file={}, sha256={}",
                file.getFileName(), file.getSha256());
    }

    private void saveCompletedFile(AssembledFile file) {
        if (assembledFileStore == null || file == null) {
            return;
        }
        try {
            assembledFileStore.save(file);
        } catch (Exception e) {
            log.warn("【Sigma文件重组】完整文件保存失败，不影响重组结果: file={}, error={}",
                    file.getFileName(), e.getMessage(), e);
        }
    }

    private boolean isSigma(String manufacturer) {
        return manufacturer == null
                || manufacturer.trim().isEmpty()
                || "sigma".equalsIgnoreCase(manufacturer.trim());
    }

    private String buildCacheKey(FileAssemblyRequest request, int sourceAddr, String filePath) {
        return safe(request.getRuleId()) + "|"
                + safe(request.getChainId()) + "|"
                + safe(request.getSourceIp()) + ":" + safe(request.getSourcePort()) + "|"
                + safe(request.getTargetIp()) + ":" + safe(request.getTargetPort()) + "|"
                + sourceAddr + "|"
                + filePath;
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String extractFilePathFromArgs(byte[] args) {
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

    private int parseStandardPayloadSize(byte[] args) {
        if (args.length >= 6) {
            int size = ByteBuffer.wrap(args, 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            if (size > 0 && size <= 4096) {
                return size;
            }
        }
        return DEFAULT_STANDARD_PAYLOAD_SIZE;
    }

    private String extractFileNameFromPath(String filePath) {
        if (filePath == null) {
            return null;
        }
        int lastSlash = Math.max(filePath.lastIndexOf('\\'), filePath.lastIndexOf('/'));
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return filePath.substring(lastSlash + 1);
        }
        return filePath;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private String resolveContentType(String extension) {
        if (isImageExtension(extension)) {
            return "image";
        }
        if (isVideoExtension(extension)) {
            return "video";
        }
        if (isTextExtension(extension)) {
            return "text";
        }
        return "binary";
    }

    private int maxAssemblySize(String extension) {
        return isVideoExtension(extension) ? MAX_VIDEO_ASSEMBLY_SIZE : MAX_ASSEMBLY_SIZE;
    }

    private boolean isImageExtension(String extension) {
        if (extension == null) {
            return false;
        }
        switch (extension.toLowerCase(Locale.ROOT)) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
            case "tif":
            case "tiff":
            case "webp":
                return true;
            default:
                return false;
        }
    }

    private boolean isJpegExtension(String extension) {
        if (extension == null) {
            return false;
        }
        String value = extension.toLowerCase(Locale.ROOT);
        return "jpg".equals(value) || "jpeg".equals(value);
    }

    private boolean isVideoExtension(String extension) {
        if (extension == null) {
            return false;
        }
        switch (extension.toLowerCase(Locale.ROOT)) {
            case "mp4":
            case "avi":
            case "mov":
            case "wmv":
            case "mkv":
            case "flv":
            case "rmvb":
            case "ts":
                return true;
            default:
                return false;
        }
    }

    private boolean isTextExtension(String extension) {
        if (extension == null) {
            return false;
        }
        switch (extension.toLowerCase(Locale.ROOT)) {
            case "nmg":
            case "pmg":
            case "txt":
            case "log":
            case "xml":
            case "json":
            case "ini":
            case "cfg":
            case "conf":
                return true;
            default:
                return false;
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b & 0xFF));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private void cleanStaleAssemblies() {
        long now = System.currentTimeMillis();
        if (now - lastCleanTime < 30_000L) {
            return;
        }
        lastCleanTime = now;
        assemblyCache.entrySet().removeIf(entry -> {
            FileAssembly assembly = entry.getValue();
            boolean expired = assembly.isExpired();
            if (expired) {
                log.warn("【Sigma文件重组】超时清理: file={}, packets={}, size={}KB",
                        assembly.getFilePath(), assembly.getPacketCount(), assembly.getCurrentSize() / 1024);
            }
            return expired;
        });
    }

    private static class FileAssembly {
        private final String filePath;
        private final int standardPayloadSize;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final long createTime = System.currentTimeMillis();
        private long lastPacketTime = System.currentTimeMillis();
        private int packetCount;

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

        boolean isExpired() {
            return System.currentTimeMillis() - lastPacketTime > ASSEMBLY_TIMEOUT_MS;
        }

        boolean isStale() {
            return System.currentTimeMillis() - lastPacketTime > STALE_GAP_MS;
        }

        String getFilePath() {
            return filePath;
        }

        int getStandardPayloadSize() {
            return standardPayloadSize;
        }

        int getCurrentSize() {
            return buffer.size();
        }

        int getPacketCount() {
            return packetCount;
        }

        @SuppressWarnings("unused")
        long getCreateTime() {
            return createTime;
        }
    }
}

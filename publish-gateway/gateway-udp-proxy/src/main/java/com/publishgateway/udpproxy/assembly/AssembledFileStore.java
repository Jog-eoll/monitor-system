package com.publishgateway.udpproxy.assembly;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 已重组文件的本地存储。
 */
@Slf4j
@Service
public class AssembledFileStore {

    @Value("${assembly.file-store-dir:}")
    private String configuredStoreDir;

    @Value("${assembly.file-max-retained:200}")
    private int maxRetained;

    private final Map<String, AssembledFile> files = new ConcurrentHashMap<>();
    private final Object storeLock = new Object();

    public AssembledFile save(AssembledFile file) {
        if (file == null || file.getFileBytes() == null || file.getFileBytes().length == 0) {
            throw new IllegalArgumentException("assembled file bytes must not be empty");
        }
        synchronized (storeLock) {
            String fileId = UUID.randomUUID().toString().replace("-", "");
            String extension = sanitizeExtension(file.getFileExtension());
            String storageFileName = extension.isEmpty() ? fileId : fileId + "." + extension;
            Path dir = resolveStoreDir().resolve(LocalDate.now().toString());
            Path path = dir.resolve(storageFileName);
            try {
                Files.createDirectories(dir);
                Files.write(path, file.getFileBytes());
            } catch (IOException e) {
                throw new IllegalStateException("save assembled file failed: " + e.getMessage(), e);
            }

            long now = System.currentTimeMillis();
            file.setFileId(fileId);
            file.setStoragePath(path.toAbsolutePath().toString());
            file.setStoredAt(now);

            AssembledFile metadata = copyMetadata(file);
            files.put(fileId, metadata);
            evictOverflow();
            log.info("【重组文件存储】已保存: fileId={}, file={}, size={}B, path={}",
                    fileId, file.getFileName(), file.getTotalSize(), metadata.getStoragePath());
            return metadata;
        }
    }

    public List<AssembledFile> list(String ruleId, Long chainId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return files.values().stream()
                .filter(file -> ruleId == null || ruleId.trim().isEmpty() || ruleId.equals(file.getRuleId()))
                .filter(file -> chainId == null || chainId.equals(file.getChainId()))
                .sorted(Comparator.comparing(AssembledFile::getStoredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    public AssembledFile get(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return null;
        }
        return files.get(fileId.trim());
    }

    public Path getContentPath(String fileId) {
        AssembledFile file = get(fileId);
        if (file == null || file.getStoragePath() == null) {
            return null;
        }
        Path path = Paths.get(file.getStoragePath());
        return Files.exists(path) ? path : null;
    }

    private Path resolveStoreDir() {
        if (configuredStoreDir != null && !configuredStoreDir.trim().isEmpty()) {
            return Paths.get(configuredStoreDir.trim());
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "publish-gateway-assembly");
    }

    private String sanitizeExtension(String extension) {
        if (extension == null) {
            return "";
        }
        return extension.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private AssembledFile copyMetadata(AssembledFile file) {
        return AssembledFile.builder()
                .fileId(file.getFileId())
                .ruleId(file.getRuleId())
                .chainId(file.getChainId())
                .manufacturer(file.getManufacturer())
                .sourceIp(file.getSourceIp())
                .sourcePort(file.getSourcePort())
                .targetIp(file.getTargetIp())
                .targetPort(file.getTargetPort())
                .filePath(file.getFilePath())
                .fileName(file.getFileName())
                .fileExtension(file.getFileExtension())
                .contentType(file.getContentType())
                .storagePath(file.getStoragePath())
                .sha256(file.getSha256())
                .securePublishBlockPresent(file.getSecurePublishBlockPresent())
                .securePublishSegmentType(file.getSecurePublishSegmentType())
                .securePublishPayloadSha256(file.getSecurePublishPayloadSha256())
                .securePublishStrippedPayloadSha256(file.getSecurePublishStrippedPayloadSha256())
                .securePublishPayloadHashMatched(file.getSecurePublishPayloadHashMatched())
                .securePublishSignatureAlgorithm(file.getSecurePublishSignatureAlgorithm())
                .securePublishSignature(file.getSecurePublishSignature())
                .securePublishCreatedAt(file.getSecurePublishCreatedAt())
                .securePublishError(file.getSecurePublishError())
                .securePublishSigned(file.getSecurePublishSigned())
                .securePublishVerified(file.getSecurePublishVerified())
                .securePublishAllowed(file.getSecurePublishAllowed())
                .securePublishAuditOnly(file.getSecurePublishAuditOnly())
                .securePublishVerifyMode(file.getSecurePublishVerifyMode())
                .securePublishVerifyReason(file.getSecurePublishVerifyReason())
                .securePublishKeyId(file.getSecurePublishKeyId())
                .securePublishSignTime(file.getSecurePublishSignTime())
                .securePublishExpireTime(file.getSecurePublishExpireTime())
                .totalPackets(file.getTotalPackets())
                .totalSize(file.getTotalSize())
                .protocolSourceAddr(file.getProtocolSourceAddr())
                .protocolDestAddr(file.getProtocolDestAddr())
                .standardPayloadSize(file.getStandardPayloadSize())
                .completedAt(file.getCompletedAt())
                .storedAt(file.getStoredAt())
                .build();
    }

    private void evictOverflow() {
        int safeMax = Math.max(1, maxRetained);
        if (files.size() <= safeMax) {
            return;
        }
        List<AssembledFile> overflow = files.values().stream()
                .sorted(Comparator.comparing(AssembledFile::getStoredAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(files.size() - safeMax)
                .collect(Collectors.toList());
        for (AssembledFile file : overflow) {
            if (file == null || file.getFileId() == null) {
                continue;
            }
            files.remove(file.getFileId());
            deleteQuietly(file.getStoragePath());
        }
    }

    private void deleteQuietly(String storagePath) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(storagePath));
        } catch (Exception e) {
            log.debug("删除重组文件失败: path={}, error={}", storagePath, e.getMessage());
        }
    }
}

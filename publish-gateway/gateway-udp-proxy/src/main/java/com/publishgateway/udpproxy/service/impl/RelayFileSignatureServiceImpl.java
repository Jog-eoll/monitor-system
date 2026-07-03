package com.publishgateway.udpproxy.service.impl;

import com.publishgateway.udpproxy.config.RelayFileSignatureProperties;
import com.publishgateway.udpproxy.service.ClientFileSignatureRecord;
import com.publishgateway.udpproxy.service.FileSignatureCheckResult;
import com.publishgateway.udpproxy.service.FileSignatureManifest;
import com.publishgateway.udpproxy.service.FileSignatureResult;
import com.publishgateway.udpproxy.service.FileSignatureVerifyResult;
import com.publishgateway.udpproxy.service.RelayFileSignatureService;
import com.publishgateway.udpproxy.service.SignedEnvelopeCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelayFileSignatureServiceImpl implements RelayFileSignatureService {

    private final RelayFileSignatureProperties properties;
    private final SignedEnvelopeCryptoService cryptoService;

    private final AtomicLong filesSeen = new AtomicLong();
    private final AtomicLong filesSigned = new AtomicLong();
    private final AtomicLong filesVerified = new AtomicLong();
    private final AtomicLong verifyFailed = new AtomicLong();
    private final AtomicLong signFailed = new AtomicLong();
    private final AtomicLong auditOnly = new AtomicLong();
    private final AtomicLong filesSkipped = new AtomicLong();
    private final AtomicLong clientRecordsReceived = new AtomicLong();
    private final AtomicLong clientRecordsVerified = new AtomicLong();
    private final AtomicLong clientRecordsVerifyFailed = new AtomicLong();
    private final AtomicLong clientRecordsMatched = new AtomicLong();
    private final AtomicLong clientRecordsMissed = new AtomicLong();

    private volatile String lastError;
    private volatile FileSignatureCheckResult lastResult;
    private final ConcurrentMap<String, ClientFileSignatureRecord> clientRecords = new ConcurrentHashMap<>();

    @Override
    public FileSignatureCheckResult signAndVerify(FileSignatureManifest manifest) {
        FileSignatureCheckResult check = initResult(manifest);
        if (!properties.isEnabled()) {
            check.setAllowed(true);
            lastResult = check;
            return check;
        }

        filesSeen.incrementAndGet();
        if (properties.isAuditMode()) {
            auditOnly.incrementAndGet();
        }

        if (manifest == null) {
            return fail(check, "MANIFEST_NULL", false);
        }

        long maxBytes = Math.max(1L, properties.getMaxFileSizeMb()) * 1024L * 1024L;
        Integer fileSize = manifest.getFileSize();
        if (fileSize != null && fileSize.longValue() > maxBytes) {
            filesSkipped.incrementAndGet();
            check.setAllowed(true);
            check.setErrorMessage("FILE_TOO_LARGE_FOR_SIGNATURE");
            lastResult = check;
            lastError = check.getErrorMessage();
            log.warn("[RelayFileSignature] skip large file signature: file={}, size={}, maxBytes={}",
                    manifest.getFilePath(), fileSize, maxBytes);
            return check;
        }

        if (properties.isPreferClientSignature()) {
            FileSignatureCheckResult clientResult = tryClientRecord(check, manifest);
            if (clientResult != null) {
                return clientResult;
            }
        }
        return fail(check, "CLIENT_SIGNATURE_RECORD_MISSING", false);
    }

    @Override
    public FileSignatureResult sign(FileSignatureManifest manifest) {
        if (manifest == null) {
            return FileSignatureResult.failure("MANIFEST_NULL");
        }
        try {
            byte[] signedEnvelope = cryptoService.signEnvelope(manifest.toCanonicalBytes());
            if (signedEnvelope == null || signedEnvelope.length == 0) {
                return FileSignatureResult.failure("SIGN_ENVELOPE_EMPTY");
            }
            return FileSignatureResult.success(signedEnvelope, manifest.getAlgorithm());
        } catch (Exception e) {
            log.warn("[RelayFileSignature] sign exception: {}", e.getMessage(), e);
            return FileSignatureResult.failure("SIGN_EXCEPTION: " + e.getMessage());
        }
    }

    @Override
    public FileSignatureVerifyResult verify(FileSignatureManifest manifest, byte[] signedEnvelope) {
        if (manifest == null) {
            return FileSignatureVerifyResult.failure("MANIFEST_NULL");
        }
        if (signedEnvelope == null || signedEnvelope.length == 0) {
            return FileSignatureVerifyResult.failure("SIGNED_ENVELOPE_EMPTY");
        }
        try {
            byte[] verifiedBytes = cryptoService.verifyEnvelope(signedEnvelope);
            if (verifiedBytes == null || verifiedBytes.length == 0) {
                return FileSignatureVerifyResult.failure("VERIFY_ENVELOPE_EMPTY");
            }
            if (!Arrays.equals(manifest.toCanonicalBytes(), verifiedBytes)) {
                return FileSignatureVerifyResult.failure("MANIFEST_MISMATCH");
            }
            return FileSignatureVerifyResult.success();
        } catch (Exception e) {
            log.warn("[RelayFileSignature] verify exception: {}", e.getMessage(), e);
            return FileSignatureVerifyResult.failure("VERIFY_EXCEPTION: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> acceptClientRecord(ClientFileSignatureRecord record) {
        clientRecordsReceived.incrementAndGet();
        Map<String, Object> result = new LinkedHashMap<>();
        if (record == null) {
            clientRecordsVerifyFailed.incrementAndGet();
            result.put("accepted", false);
            result.put("verified", false);
            result.put("error", "RECORD_NULL");
            return result;
        }

        record.setReceivedAt(System.currentTimeMillis());
        try {
            validateClientRecord(record);
            byte[] envelope = Base64.getDecoder().decode(record.getSignedEnvelopeBase64());
            byte[] manifestBytes = cryptoService.verifyEnvelope(envelope);
            byte[] expected = record.getManifestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (manifestBytes == null || !Arrays.equals(expected, manifestBytes)) {
                throw new IllegalStateException("CLIENT_MANIFEST_MISMATCH");
            }
            record.setVerified(true);
            record.setVerifyError(null);
            clientRecordsVerified.incrementAndGet();
            clientRecords.put(recordKey(record.getFileHash(), record.getFilePath()), record);
            result.put("accepted", true);
            result.put("verified", true);
            result.put("fileHash", record.getFileHash());
            cleanupClientRecords();
            return result;
        } catch (Exception e) {
            record.setVerified(false);
            record.setVerifyError(e.getMessage());
            clientRecordsVerifyFailed.incrementAndGet();
            clientRecords.put(recordKey(record.getFileHash(), record.getFilePath()), record);
            lastError = e.getMessage();
            result.put("accepted", true);
            result.put("verified", false);
            result.put("error", e.getMessage());
            log.warn("[RelayFileSignature] client record verify failed but accepted for audit: file={}, hash={}, error={}",
                    record.getFilePath(), record.getFileHash(), e.getMessage());
            cleanupClientRecords();
            return result;
        }
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("mode", properties.getMode());
        status.put("verifyOnCompleteOnly", properties.isVerifyOnCompleteOnly());
        status.put("failOpenOnSignError", properties.isFailOpenOnSignError());
        status.put("cacheTtlMs", properties.getCacheTtlMs());
        status.put("maxFileSizeMb", properties.getMaxFileSizeMb());
        status.put("filesSeen", filesSeen.get());
        status.put("filesSigned", filesSigned.get());
        status.put("filesVerified", filesVerified.get());
        status.put("verifyFailed", verifyFailed.get());
        status.put("signFailed", signFailed.get());
        status.put("auditOnly", auditOnly.get());
        status.put("filesSkipped", filesSkipped.get());
        status.put("preferClientSignature", properties.isPreferClientSignature());
        status.put("requireClientSignature", properties.isRequireClientSignature());
        status.put("clientRecordsReceived", clientRecordsReceived.get());
        status.put("clientRecordsVerified", clientRecordsVerified.get());
        status.put("clientRecordsVerifyFailed", clientRecordsVerifyFailed.get());
        status.put("clientRecordsMatched", clientRecordsMatched.get());
        status.put("clientRecordsMissed", clientRecordsMissed.get());
        status.put("clientRecordCacheSize", clientRecords.size());
        status.put("lastError", lastError);
        status.put("lastResult", lastResult);
        return status;
    }

    @Override
    public void clearStatus() {
        filesSeen.set(0);
        filesSigned.set(0);
        filesVerified.set(0);
        verifyFailed.set(0);
        signFailed.set(0);
        auditOnly.set(0);
        filesSkipped.set(0);
        clientRecordsReceived.set(0);
        clientRecordsVerified.set(0);
        clientRecordsVerifyFailed.set(0);
        clientRecordsMatched.set(0);
        clientRecordsMissed.set(0);
        clientRecords.clear();
        lastError = null;
        lastResult = null;
    }

    @Override
    public boolean isEnforceMode() {
        return properties.isEnforceMode();
    }

    private FileSignatureCheckResult initResult(FileSignatureManifest manifest) {
        FileSignatureCheckResult check = new FileSignatureCheckResult();
        check.setEnabled(properties.isEnabled());
        check.setMode(properties.getMode());
        check.setAuditOnly(properties.isAuditMode());
        check.setAllowed(!properties.isEnabled());
        check.setSignatureSource("client");
        if (manifest != null) {
            check.setAlgorithm(manifest.getAlgorithm());
            check.setHashAlgorithm(manifest.getHashAlgorithm());
            check.setFileHash(manifest.getFileHash());
            check.setFilePath(manifest.getFilePath());
            check.setCompletedAt(manifest.getCompletedAt() == null ? System.currentTimeMillis() : manifest.getCompletedAt());
        } else {
            check.setCompletedAt(System.currentTimeMillis());
        }
        return check;
    }

    private FileSignatureCheckResult tryClientRecord(FileSignatureCheckResult check, FileSignatureManifest manifest) {
        cleanupClientRecords();
        ClientFileSignatureRecord record = clientRecords.get(recordKey(manifest.getFileHash(), manifest.getFilePath()));
        if (record == null) {
            clientRecordsMissed.incrementAndGet();
            return null;
        }

        check.setSignatureSource("client");
        check.setClientRecordMatched(true);
        check.setClientId(record.getClientId());
        check.setClientCertId(record.getClientCertId());
        check.setFileId(record.getFileId());
        check.setSigned(record.getSignedEnvelopeBase64() != null && !record.getSignedEnvelopeBase64().isEmpty());
        check.setSignedEnvelopeLength(record.getSignedEnvelopeBase64() == null ? 0 : record.getSignedEnvelopeBase64().length());

        if (!Boolean.TRUE.equals(record.getVerified())) {
            return fail(check, "CLIENT_SIGNATURE_VERIFY_FAILED: " + safe(record.getVerifyError()), false);
        }
        if (!matchesLocalManifest(record, manifest)) {
            return fail(check, "CLIENT_SIGNATURE_LOCAL_FILE_MISMATCH", false);
        }

        clientRecordsMatched.incrementAndGet();
        filesVerified.incrementAndGet();
        check.setVerified(true);
        check.setAllowed(true);
        lastResult = check;
        lastError = null;
        log.info("[RelayFileSignature] matched client file signature: file={}, hash={}, clientId={}",
                manifest.getFilePath(), manifest.getFileHash(), record.getClientId());
        return check;
    }

    private boolean matchesLocalManifest(ClientFileSignatureRecord record, FileSignatureManifest manifest) {
        if (!safe(record.getFileHash()).equalsIgnoreCase(safe(manifest.getFileHash()))) {
            return false;
        }
        if (!normalizePath(record.getFilePath()).equals(normalizePath(manifest.getFilePath()))) {
            return false;
        }
        if (record.getFileSize() != null && manifest.getFileSize() != null
                && !record.getFileSize().equals(manifest.getFileSize())) {
            return false;
        }
        if (record.getTargetPort() != null && manifest.getTargetPort() != null
                && !record.getTargetPort().equals(manifest.getTargetPort())) {
            return false;
        }
        return true;
    }

    private void validateClientRecord(ClientFileSignatureRecord record) {
        if (isBlank(record.getFileHash())) {
            throw new IllegalArgumentException("fileHash is required");
        }
        if (isBlank(record.getFilePath())) {
            throw new IllegalArgumentException("filePath is required");
        }
        if (isBlank(record.getManifestJson())) {
            throw new IllegalArgumentException("manifestJson is required");
        }
        if (isBlank(record.getSignedEnvelopeBase64())) {
            throw new IllegalArgumentException("signedEnvelopeBase64 is required");
        }
    }

    private void cleanupClientRecords() {
        long expiredBefore = System.currentTimeMillis() - Math.max(1000L, properties.getCacheTtlMs());
        clientRecords.entrySet().removeIf(entry -> {
            ClientFileSignatureRecord record = entry.getValue();
            Long receivedAt = record == null ? null : record.getReceivedAt();
            return receivedAt != null && receivedAt < expiredBefore;
        });
    }

    private String recordKey(String fileHash, String filePath) {
        return safe(fileHash).toLowerCase() + "|" + normalizePath(filePath);
    }

    private String normalizePath(String value) {
        return safe(value).replace('/', '\\').toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private FileSignatureCheckResult fail(FileSignatureCheckResult check, String message, boolean serviceError) {
        String safeMessage = message == null ? "UNKNOWN_SIGNATURE_ERROR" : message;
        check.setErrorMessage(safeMessage);
        check.setAllowed(properties.isAuditMode() || (serviceError && properties.isFailOpenOnSignError()));
        lastError = safeMessage;
        lastResult = check;
        if (check.isAllowed()) {
            log.warn("[RelayFileSignature] signature check failed but allowed: file={}, mode={}, error={}",
                    check.getFilePath(), properties.getMode(), safeMessage);
        } else {
            log.warn("[RelayFileSignature] signature check failed and denied: file={}, mode={}, error={}",
                    check.getFilePath(), properties.getMode(), safeMessage);
        }
        return check;
    }

    private boolean isServiceError(String message) {
        if (message == null) {
            return true;
        }
        return message.startsWith("SIGN_")
                || message.startsWith("VERIFY_EXCEPTION")
                || message.startsWith("VERIFY_ENVELOPE_EMPTY");
    }
}

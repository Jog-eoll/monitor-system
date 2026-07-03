package com.publishgateway.udpproxy.secure.jpeg;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.publishgateway.udpproxy.config.SecurePublishProperties;
import com.publishgateway.udpproxy.secure.jpeg.SecurePublishJpegSegmentUtil.SecurePublishJpegBlockInfo;
import com.publishgateway.udpproxy.service.SignedEnvelopeCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verifies SecurePublish signatures embedded in JPEG APP11/COM segments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurePublishJpegSignatureService {

    private final SecurePublishProperties properties;
    private final SignedEnvelopeCryptoService cryptoService;

    private final AtomicLong filesSeen = new AtomicLong();
    private final AtomicLong filesVerified = new AtomicLong();
    private final AtomicLong filesAllowed = new AtomicLong();
    private final AtomicLong filesRejected = new AtomicLong();
    private final AtomicLong signatureFailed = new AtomicLong();
    private final AtomicLong hashFailed = new AtomicLong();
    private final AtomicLong policyFailed = new AtomicLong();

    private volatile String lastError;
    private volatile SecurePublishJpegVerifyResult lastResult;

    public SecurePublishJpegVerifyResult verify(byte[] jpegBytes, String fileName) {
        if (!properties.isJpegEnabled()) {
            SecurePublishJpegVerifyResult disabled = SecurePublishJpegVerifyResult.disabled();
            lastResult = disabled;
            return disabled;
        }

        filesSeen.incrementAndGet();
        SecurePublishJpegVerifyResult result = initResult(fileName);
        result.setFileHash(SecurePublishJpegSegmentUtil.sha256Hex(jpegBytes));
        try {
            SecurePublishJpegBlockInfo block = SecurePublishJpegSegmentUtil.inspect(jpegBytes);
            applyBlockInfo(result, block);

            if (!Boolean.TRUE.equals(block.getPresent())) {
                if (properties.isJpegRequireBlock()) {
                    return fail(result, "SECURE_PUBLISH_BLOCK_MISSING", "policy");
                }
                result.setAllowed(true);
                result.setReason("SECURE_PUBLISH_BLOCK_MISSING_BYPASS");
                filesAllowed.incrementAndGet();
                lastResult = result;
                return result;
            }

            if (!Boolean.TRUE.equals(block.getPayloadHashMatched())) {
                return fail(result, "PAYLOAD_HASH_MISMATCH", "hash");
            }

            if (properties.isJpegRequireSignature()) {
                verifySignedManifest(result, block, jpegBytes);
            } else {
                result.setVerified(true);
                result.setReason("HASH_ONLY_ACCEPTED");
            }

            result.setAllowed(true);
            filesVerified.incrementAndGet();
            filesAllowed.incrementAndGet();
            lastError = null;
            lastResult = result;
            log.info("[SecurePublish-JPEG] verify passed: file={}, hash={}, payloadHash={}, keyId={}, mode={}",
                    result.getFileName(), result.getFileHash(), result.getPayloadHash(), result.getKeyId(), result.getMode());
            return result;
        } catch (HashVerifyException e) {
            return fail(result, e.getMessage(), "hash");
        } catch (PolicyVerifyException e) {
            return fail(result, e.getMessage(), "policy");
        } catch (SignatureVerifyException e) {
            return fail(result, e.getMessage(), "signature");
        } catch (Exception e) {
            return fail(result, "VERIFY_EXCEPTION: " + e.getMessage(), "signature");
        }
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isJpegEnabled());
        status.put("mode", properties.getJpegMode());
        status.put("requireBlock", properties.isJpegRequireBlock());
        status.put("requireSignature", properties.isJpegRequireSignature());
        status.put("requireContentAllow", properties.isJpegRequireContentAllow());
        status.put("requireNotExpired", properties.isJpegRequireNotExpired());
        status.put("filesSeen", filesSeen.get());
        status.put("filesVerified", filesVerified.get());
        status.put("filesAllowed", filesAllowed.get());
        status.put("filesRejected", filesRejected.get());
        status.put("signatureFailed", signatureFailed.get());
        status.put("hashFailed", hashFailed.get());
        status.put("policyFailed", policyFailed.get());
        status.put("lastError", lastError);
        status.put("lastResult", lastResult);
        return status;
    }

    public void clearStatus() {
        filesSeen.set(0);
        filesVerified.set(0);
        filesAllowed.set(0);
        filesRejected.set(0);
        signatureFailed.set(0);
        hashFailed.set(0);
        policyFailed.set(0);
        lastError = null;
        lastResult = null;
    }

    private SecurePublishJpegVerifyResult initResult(String fileName) {
        SecurePublishJpegVerifyResult result = new SecurePublishJpegVerifyResult();
        result.setEnabled(true);
        result.setMode(properties.getJpegMode());
        result.setAuditOnly(properties.isJpegAuditMode());
        result.setAllowed(false);
        result.setFileName(fileName);
        result.setVerifyTime(System.currentTimeMillis());
        return result;
    }

    private void applyBlockInfo(SecurePublishJpegVerifyResult result, SecurePublishJpegBlockInfo block) {
        if (block == null) {
            result.setBlockPresent(false);
            return;
        }
        result.setBlockPresent(Boolean.TRUE.equals(block.getPresent()));
        result.setPayloadHash(block.getStrippedPayloadSha256());
        result.setSignatureAlgorithm(block.getSignatureAlgorithm());
        result.setFileName(firstNonBlank(block.getFileName(), result.getFileName()));
        result.setKeyId(block.getKeyId());
        result.setManifestJson(block.getManifestJson());
    }

    private void verifySignedManifest(SecurePublishJpegVerifyResult result,
                                      SecurePublishJpegBlockInfo block,
                                      byte[] jpegBytes) {
        if (isBlank(block.getManifestJson()) || isBlank(block.getSignedEnvelopeBase64())) {
            throw new SignatureVerifyException("SIGNATURE_MISSING");
        }

        byte[] signedEnvelope = Base64.getDecoder().decode(block.getSignedEnvelopeBase64());
        byte[] verifiedBytes = cryptoService.verifyEnvelope(signedEnvelope);
        byte[] manifestBytes = block.getManifestJson().getBytes(StandardCharsets.UTF_8);
        if (verifiedBytes == null || !Arrays.equals(verifiedBytes, manifestBytes)) {
            throw new SignatureVerifyException("SIGNATURE_INVALID");
        }

        JSONObject manifest = JSON.parseObject(block.getManifestJson());
        result.setSigned(true);
        result.setManifestVersion(manifest.getInteger("version"));
        result.setContentDecision(manifest.getString("contentDecision"));
        result.setPublisher(manifest.getString("publisher"));
        result.setKeyId(firstNonBlank(manifest.getString("keyId"), block.getKeyId()));
        result.setSignTime(manifest.getLong("signTime"));
        result.setExpireTime(manifest.getLong("expireTime"));

        String manifestPayloadHash = manifest.getString("payloadSha256");
        if (!safe(manifestPayloadHash).equalsIgnoreCase(safe(block.getStrippedPayloadSha256()))) {
            throw new HashVerifyException("MANIFEST_PAYLOAD_HASH_MISMATCH");
        }
        String manifestHashAlgorithm = firstNonBlank(manifest.getString("hashAlgorithm"), "SHA-256");
        if (!"SHA-256".equalsIgnoreCase(manifestHashAlgorithm)) {
            throw new PolicyVerifyException("HASH_ALGORITHM_UNSUPPORTED");
        }
        Long fileSize = manifest.getLong("fileSize");
        int unsignedSize = SecurePublishJpegSegmentUtil.stripSecurePublishBlocks(jpegBytes).length;
        if (fileSize != null && fileSize.longValue() != unsignedSize) {
            throw new HashVerifyException("MANIFEST_FILE_SIZE_MISMATCH");
        }
        if (properties.isJpegRequireContentAllow()
                && !"ALLOW".equalsIgnoreCase(safe(manifest.getString("contentDecision")))) {
            throw new PolicyVerifyException("CONTENT_NOT_ALLOWED");
        }
        if (properties.isJpegRequireNotExpired()) {
            Long expireTime = manifest.getLong("expireTime");
            if (expireTime == null || expireTime <= System.currentTimeMillis()) {
                throw new PolicyVerifyException("SIGNATURE_EXPIRED");
            }
        }

        result.setVerified(true);
        result.setReason("VERIFIED");
    }

    private SecurePublishJpegVerifyResult fail(SecurePublishJpegVerifyResult result, String reason, String type) {
        String safeReason = firstNonBlank(reason, "VERIFY_FAILED");
        if ("hash".equals(type)) {
            hashFailed.incrementAndGet();
        } else if ("policy".equals(type)) {
            policyFailed.incrementAndGet();
        } else {
            signatureFailed.incrementAndGet();
        }

        result.setReason(safeReason);
        result.setAllowed(properties.isJpegAuditMode());
        if (result.isAllowed()) {
            filesAllowed.incrementAndGet();
            log.warn("[SecurePublish-JPEG] verify failed but allowed: file={}, mode={}, reason={}",
                    result.getFileName(), result.getMode(), safeReason);
        } else {
            filesRejected.incrementAndGet();
            log.warn("[SecurePublish-JPEG] verify failed and blocked: file={}, mode={}, reason={}",
                    result.getFileName(), result.getMode(), safeReason);
        }
        lastError = safeReason;
        lastResult = result;
        return result;
    }

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class SignatureVerifyException extends RuntimeException {
        private SignatureVerifyException(String message) {
            super(message);
        }
    }

    private static class HashVerifyException extends RuntimeException {
        private HashVerifyException(String message) {
            super(message);
        }
    }

    private static class PolicyVerifyException extends RuntimeException {
        private PolicyVerifyException(String message) {
            super(message);
        }
    }
}

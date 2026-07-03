package com.publishgateway.udpproxy.service.impl;

import com.publishgateway.udpproxy.config.ContentReleaseTokenProperties;
import com.publishgateway.udpproxy.entity.UdpProxyRule;
import com.publishgateway.udpproxy.relay.RelayPacketCodec;
import com.publishgateway.udpproxy.service.ContentReleaseTokenIssueRequest;
import com.publishgateway.udpproxy.service.ContentReleaseTokenIssueResponse;
import com.publishgateway.udpproxy.service.ContentReleaseTokenMetadata;
import com.publishgateway.udpproxy.service.ContentReleaseTokenService;
import com.publishgateway.udpproxy.service.ContentReleaseTokenVerifyResult;
import com.publishgateway.udpproxy.service.FileSignatureManifest;
import com.publishgateway.udpproxy.service.SignedEnvelopeCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentReleaseTokenServiceImpl implements ContentReleaseTokenService {

    private static final byte SYN_BYTE1 = 0x55;
    private static final byte SYN_TYPE2_SUM = (byte) 0xA7;
    private static final byte SYN_TYPE2_CRC = (byte) 0xA3;
    private static final byte SYN_TYPE3_SUM = (byte) 0xA8;
    private static final byte SYN_TYPE3_CRC = (byte) 0xA4;
    private static final Charset GBK = Charset.forName("GBK");
    private static final Set<String> CONTENT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "bmp", "gif", "webp",
            "mp4", "avi", "mov", "mkv", "wmv", "flv", "mpeg", "mpg", "ts",
            "txt", "json", "xml", "csv", "log"));

    private final ContentReleaseTokenProperties properties;
    private final SignedEnvelopeCryptoService cryptoService;

    private final ConcurrentMap<String, TokenRecord> tokenCache = new ConcurrentHashMap<>();
    private final AtomicLong tokensIssued = new AtomicLong();
    private final AtomicLong tokensVerified = new AtomicLong();
    private final AtomicLong tokenRejected = new AtomicLong();
    private final AtomicLong tokenExpired = new AtomicLong();
    private final AtomicLong tokenMissing = new AtomicLong();
    private final AtomicLong contentBlocked = new AtomicLong();

    private volatile String lastTokenError;

    @Override
    public ContentReleaseTokenIssueResponse issue(ContentReleaseTokenIssueRequest request) {
        ContentReleaseTokenIssueResponse response = new ContentReleaseTokenIssueResponse();
        if (!properties.isEnabled()) {
            response.setIssued(false);
            response.setError("CONTENT_RELEASE_TOKEN_DISABLED");
            return response;
        }
        String validationError = validateIssueRequest(request);
        if (validationError != null) {
            tokenRejected.incrementAndGet();
            lastTokenError = validationError;
            response.setIssued(false);
            response.setError(validationError);
            return response;
        }

        try {
            long now = System.currentTimeMillis();
            long ttl = request.getTokenTtlMs() != null && request.getTokenTtlMs() > 0
                    ? request.getTokenTtlMs()
                    : Math.max(60_000L, properties.getTokenTtlMs());
            ContentReleaseTokenMetadata metadata = new ContentReleaseTokenMetadata();
            metadata.setTokenId(UUID.randomUUID().toString().replace("-", ""));
            metadata.setFileId(buildFileId(request));
            metadata.setFileName(request.getFileName());
            metadata.setFilePath(request.getFilePath());
            metadata.setFileHash(normalizeHash(request.getFileHash()));
            metadata.setFileSize(request.getFileSize());
            metadata.setContentType(request.getContentType());
            metadata.setScanResult("PASS");
            metadata.setRiskLevel(defaultIfBlank(request.getRiskLevel(), "LOW"));
            metadata.setAllowForward(Boolean.TRUE);
            metadata.setAllowDisplay(Boolean.TRUE);
            metadata.setPolicyVersion(defaultIfBlank(request.getPolicyVersion(), properties.getPolicyVersion()));
            metadata.setClientId(request.getClientId());
            metadata.setSourceIp(request.getSourceIp());
            metadata.setSourcePort(request.getSourcePort());
            metadata.setTargetIp(request.getTargetIp());
            metadata.setTargetPort(request.getTargetPort());
            metadata.setRuleId(request.getRuleId());
            metadata.setChainId(request.getChainId());
            metadata.setIssuedAt(now);
            metadata.setExpireAt(now + ttl);
            metadata.setIssuer(defaultIfBlank(properties.getIssuer(), "publish-gateway"));

            byte[] envelope = cryptoService.signEnvelope(metadata.toCanonicalBytes());
            if (envelope == null || envelope.length == 0) {
                throw new IllegalStateException("SIGNED_ENVELOPE_EMPTY");
            }
            String envelopeBase64 = Base64.getEncoder().encodeToString(envelope);
            TokenRecord record = new TokenRecord(metadata, envelopeBase64);
            tokenCache.put(metadata.getTokenId(), record);
            tokensIssued.incrementAndGet();
            cleanupExpiredTokens();

            response.setIssued(true);
            response.setTokenId(metadata.getTokenId());
            response.setFileId(metadata.getFileId());
            response.setMetadata(metadata);
            response.setSignedEnvelopeBase64(envelopeBase64);
            lastTokenError = null;
            return response;
        } catch (Exception e) {
            tokenRejected.incrementAndGet();
            lastTokenError = e.getMessage();
            response.setIssued(false);
            response.setError("TOKEN_SIGN_FAILED: " + e.getMessage());
            log.warn("[ContentReleaseToken] issue failed: {}", e.getMessage(), e);
            return response;
        }
    }

    @Override
    public ContentReleaseTokenVerifyResult verifyRelayPacket(RelayPacketCodec.RelayPacket packet, UdpProxyRule rule) {
        if (!properties.isEnabled() || !properties.isRequireToken()) {
            return ContentReleaseTokenVerifyResult.allow("TOKEN_CHECK_DISABLED");
        }
        ParsedContentPacket parsed = parseContentPacket(packet == null ? null : packet.getPayload());
        if (parsed == null) {
            return ContentReleaseTokenVerifyResult.allow("NOT_PROTECTED_CONTENT");
        }
        if (packet == null || isBlank(packet.getContentTokenId())) {
            tokenMissing.incrementAndGet();
            contentBlocked.incrementAndGet();
            lastTokenError = "CONTENT_TOKEN_MISSING";
            return ContentReleaseTokenVerifyResult.deny("CONTENT_TOKEN_MISSING", true);
        }
        TokenRecord record = tokenCache.get(packet.getContentTokenId());
        if (record == null) {
            tokenRejected.incrementAndGet();
            contentBlocked.incrementAndGet();
            lastTokenError = "CONTENT_TOKEN_NOT_FOUND";
            return ContentReleaseTokenVerifyResult.deny("CONTENT_TOKEN_NOT_FOUND", true);
        }
        long now = System.currentTimeMillis();
        if (record.metadata.getExpireAt() != null && now > record.metadata.getExpireAt()) {
            tokenExpired.incrementAndGet();
            contentBlocked.incrementAndGet();
            tokenCache.remove(record.metadata.getTokenId(), record);
            lastTokenError = "CONTENT_TOKEN_EXPIRED";
            return ContentReleaseTokenVerifyResult.deny("CONTENT_TOKEN_EXPIRED", true);
        }
        String deniedReason = validateRelayBinding(packet, rule, parsed, record.metadata);
        if (deniedReason != null) {
            tokenRejected.incrementAndGet();
            contentBlocked.incrementAndGet();
            lastTokenError = deniedReason;
            return ContentReleaseTokenVerifyResult.deny(deniedReason, true);
        }
        if (!record.verified) {
            String verifyError = verifyEnvelope(record);
            if (verifyError != null) {
                tokenRejected.incrementAndGet();
                contentBlocked.incrementAndGet();
                lastTokenError = verifyError;
                return ContentReleaseTokenVerifyResult.deny(verifyError, true);
            }
            record.verified = true;
        }
        tokensVerified.incrementAndGet();
        lastTokenError = null;
        ContentReleaseTokenVerifyResult result = ContentReleaseTokenVerifyResult.allow("CONTENT_TOKEN_VERIFIED");
        result.setProtectedContent(true);
        result.setTokenId(record.metadata.getTokenId());
        result.setFileId(record.metadata.getFileId());
        return result;
    }

    @Override
    public Map<String, Object> getStatus() {
        cleanupExpiredTokens();
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("requireToken", properties.isRequireToken());
        status.put("tokenTtlMs", properties.getTokenTtlMs());
        status.put("cacheTtlMs", properties.getCacheTtlMs());
        status.put("tokensIssued", tokensIssued.get());
        status.put("tokensVerified", tokensVerified.get());
        status.put("tokenRejected", tokenRejected.get());
        status.put("tokenExpired", tokenExpired.get());
        status.put("tokenMissing", tokenMissing.get());
        status.put("contentBlocked", contentBlocked.get());
        status.put("tokenCacheSize", tokenCache.size());
        status.put("lastTokenError", lastTokenError);
        return status;
    }

    @Override
    public void clearStatus() {
        tokenCache.clear();
        tokensIssued.set(0L);
        tokensVerified.set(0L);
        tokenRejected.set(0L);
        tokenExpired.set(0L);
        tokenMissing.set(0L);
        contentBlocked.set(0L);
        lastTokenError = null;
    }

    private String validateIssueRequest(ContentReleaseTokenIssueRequest request) {
        if (request == null) {
            return "REQUEST_NULL";
        }
        if (!"PASS".equalsIgnoreCase(request.getScanResult())) {
            return "SCAN_RESULT_NOT_PASS";
        }
        if (!Boolean.TRUE.equals(request.getAllowForward()) || !Boolean.TRUE.equals(request.getAllowDisplay())) {
            return "TOKEN_POLICY_NOT_ALLOWED";
        }
        if (isBlank(request.getFileHash()) || request.getFileSize() == null || request.getFileSize() <= 0) {
            return "FILE_METADATA_INVALID";
        }
        long maxBytes = Math.max(1L, properties.getMaxFileSizeMb()) * 1024L * 1024L;
        if (request.getFileSize() > maxBytes) {
            return "FILE_TOO_LARGE_FOR_TOKEN";
        }
        if (isBlank(request.getSourceIp()) || isBlank(request.getTargetIp()) || request.getTargetPort() == null) {
            return "TOKEN_BINDING_INVALID";
        }
        return null;
    }

    private String validateRelayBinding(RelayPacketCodec.RelayPacket packet,
                                        UdpProxyRule rule,
                                        ParsedContentPacket parsed,
                                        ContentReleaseTokenMetadata metadata) {
        if (!"PASS".equalsIgnoreCase(metadata.getScanResult())) {
            return "TOKEN_SCAN_RESULT_DENIED";
        }
        if (!Boolean.TRUE.equals(metadata.getAllowForward()) || !Boolean.TRUE.equals(metadata.getAllowDisplay())) {
            return "TOKEN_POLICY_DENIED";
        }
        if (!safeEquals(metadata.getTokenId(), packet.getContentTokenId())) {
            return "TOKEN_ID_MISMATCH";
        }
        if (!isBlank(packet.getContentFileId()) && !safeEquals(metadata.getFileId(), packet.getContentFileId())) {
            return "FILE_ID_MISMATCH";
        }
        if (!safeEquals(metadata.getSourceIp(), packet.getOriginalSrcIp())) {
            return "TOKEN_SOURCE_IP_MISMATCH";
        }
        if (!safeEquals(metadata.getTargetIp(), packet.getOriginalDstIp())) {
            return "TOKEN_TARGET_IP_MISMATCH";
        }
        if (metadata.getTargetPort() == null || metadata.getTargetPort() != packet.getOriginalDstPort()) {
            return "TOKEN_TARGET_PORT_MISMATCH";
        }
        if (!isBlank(metadata.getFilePath()) && !safeEquals(normalizePath(metadata.getFilePath()), normalizePath(parsed.filePath))) {
            return "TOKEN_FILE_PATH_MISMATCH";
        }
        if (rule != null && !isBlank(metadata.getRuleId()) && !safeEquals(metadata.getRuleId(), rule.getRuleId())) {
            return "TOKEN_RULE_MISMATCH";
        }
        return null;
    }

    private String verifyEnvelope(TokenRecord record) {
        try {
            byte[] envelope = Base64.getDecoder().decode(record.signedEnvelopeBase64);
            byte[] verifiedBytes = cryptoService.verifyEnvelope(envelope);
            if (verifiedBytes == null || !Arrays.equals(record.metadata.toCanonicalBytes(), verifiedBytes)) {
                return "TOKEN_ENVELOPE_MISMATCH";
            }
            return null;
        } catch (Exception e) {
            return "TOKEN_VERIFY_FAILED: " + e.getMessage();
        }
    }

    private void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        long cacheTtl = Math.max(60_000L, properties.getCacheTtlMs());
        for (Map.Entry<String, TokenRecord> entry : tokenCache.entrySet()) {
            TokenRecord record = entry.getValue();
            if (record == null || record.metadata == null) {
                tokenCache.remove(entry.getKey());
                continue;
            }
            Long expireAt = record.metadata.getExpireAt();
            Long issuedAt = record.metadata.getIssuedAt();
            if ((expireAt != null && now > expireAt)
                    || (issuedAt != null && now - issuedAt > cacheTtl)) {
                tokenCache.remove(entry.getKey(), record);
            }
        }
    }

    private ParsedContentPacket parseContentPacket(byte[] udpData) {
        if (udpData == null || udpData.length < 20 || udpData[0] != SYN_BYTE1) {
            return null;
        }
        byte syn2 = udpData[1];
        if (syn2 != SYN_TYPE2_SUM && syn2 != SYN_TYPE2_CRC
                && syn2 != SYN_TYPE3_SUM && syn2 != SYN_TYPE3_CRC) {
            return null;
        }
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
        String extension = extensionOf(filePath);
        if (extension == null || !CONTENT_EXTENSIONS.contains(extension)) {
            return null;
        }
        ParsedContentPacket parsed = new ParsedContentPacket();
        parsed.filePath = filePath;
        return parsed;
    }

    private String extractFilePathFromArgs(byte[] args) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length - 3; i++) {
            byte b = args[i];
            if (b >= 'A' && b <= 'Z' && args[i + 1] == ':' && args[i + 2] == '\\') {
                int end = i;
                while (end < args.length && args[end] != 0x00) {
                    end++;
                }
                if (end > i + 3) {
                    return new String(args, i, end - i, GBK);
                }
            }
        }
        return null;
    }

    private String extensionOf(String filePath) {
        if (filePath == null) {
            return null;
        }
        int index = filePath.lastIndexOf('.');
        if (index < 0 || index >= filePath.length() - 1) {
            return null;
        }
        return filePath.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String buildFileId(ContentReleaseTokenIssueRequest request) {
        String seed = normalizeHash(request.getFileHash()) + "|"
                + normalizePath(request.getFilePath()) + "|"
                + defaultIfBlank(request.getTargetIp(), "") + ":"
                + (request.getTargetPort() == null ? 0 : request.getTargetPort());
        return FileSignatureManifest.sha256Hex(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String normalizeHash(String hash) {
        String value = defaultIfBlank(hash, "");
        if (value.regionMatches(true, 0, "SHA256:", 0, 7)) {
            return "SHA256:" + value.substring(7).toLowerCase(Locale.ROOT);
        }
        return "SHA256:" + value.toLowerCase(Locale.ROOT);
    }

    private String normalizePath(String filePath) {
        return defaultIfBlank(filePath, "").replace('/', '\\').toLowerCase(Locale.ROOT);
    }

    private boolean safeEquals(String left, String right) {
        return defaultIfBlank(left, "").equals(defaultIfBlank(right, ""));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class ParsedContentPacket {
        private String filePath;
    }

    private static class TokenRecord {
        private final ContentReleaseTokenMetadata metadata;
        private final String signedEnvelopeBase64;
        private volatile boolean verified;

        private TokenRecord(ContentReleaseTokenMetadata metadata, String signedEnvelopeBase64) {
            this.metadata = metadata;
            this.signedEnvelopeBase64 = signedEnvelopeBase64;
        }
    }
}

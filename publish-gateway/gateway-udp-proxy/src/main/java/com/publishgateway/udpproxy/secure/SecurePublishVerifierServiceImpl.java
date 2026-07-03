package com.publishgateway.udpproxy.secure;

import com.alibaba.fastjson2.JSON;
import com.publishgateway.udpproxy.config.SecurePublishProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurePublishVerifierServiceImpl implements SecurePublishVerifierService {

    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String SIGNATURE_ENTRY = "signature.sig";
    private static final String PAYLOAD_ENTRY = "payload.bin";
    private static final String RSA_SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int TAR_BLOCK_SIZE = 512;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_SIGNATURE_BYTES = 4 * 1024 * 1024;

    private final SecurePublishProperties properties;

    private final AtomicLong packagesSeen = new AtomicLong();
    private final AtomicLong packagesVerified = new AtomicLong();
    private final AtomicLong packagesRejected = new AtomicLong();
    private final AtomicLong signatureFailed = new AtomicLong();
    private final AtomicLong hashFailed = new AtomicLong();
    private final AtomicLong policyFailed = new AtomicLong();

    private volatile String lastError;
    private volatile SecurePublishVerifyResult lastResult;

    @Override
    public SecurePublishVerifyResult verifyPackage(byte[] packageBytes, String packageName, String sourceIp, Long chainId) {
        packagesSeen.incrementAndGet();
        String safePackageName = safePackageName(packageName);
        try {
            long maxBytes = Math.max(1L, properties.getMaxPackageSizeMb()) * 1024L * 1024L;
            if (packageBytes == null || packageBytes.length <= 0 || packageBytes.length > maxBytes) {
                return reject("PACKAGE_SIZE_NOT_ALLOWED", safePackageName, sourceIp, chainId);
            }

            SecurePackageEntries entries = extractEntries(packageBytes);
            SecurePublishManifest manifest = SecurePublishManifest.fromJson(entries.manifestBytes);
            if (manifest == null) {
                return reject("MANIFEST_INVALID", safePackageName, sourceIp, chainId);
            }

            String keyId = firstNonBlank(manifest.getKeyId(), properties.getDefaultKeyId());
            validateManifest(manifest, keyId);
            verifySignature(entries.manifestBytes, manifest, entries.signatureBytes, keyId);
            validatePayload(manifest, entries.payloadBytes);

            SecurePublishVerifyResult result = SecurePublishVerifyResult.allowed(manifest, entries.payloadBytes, keyId);
            result.setPackageName(safePackageName);
            packagesVerified.incrementAndGet();
            lastError = null;
            lastResult = result;
            log.info("[SecurePublish] package verified: package={}, payload={}, hash={}, keyId={}, sourceIp={}, chainId={}",
                    safePackageName, result.getPayloadName(), result.getFileHash(), keyId, sourceIp, chainId);
            return result;
        } catch (SignatureVerifyException e) {
            signatureFailed.incrementAndGet();
            return reject(e.getMessage(), safePackageName, sourceIp, chainId);
        } catch (HashVerifyException e) {
            hashFailed.incrementAndGet();
            return reject(e.getMessage(), safePackageName, sourceIp, chainId);
        } catch (PolicyVerifyException e) {
            policyFailed.incrementAndGet();
            return reject(e.getMessage(), safePackageName, sourceIp, chainId);
        } catch (Exception e) {
            log.warn("[SecurePublish] package verify exception: package={}, reason={}",
                    safePackageName, e.getMessage(), e);
            return reject(firstNonBlank(e.getMessage(), "PACKAGE_FORMAT_ERROR"), safePackageName, sourceIp, chainId);
        }
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("trustStoreDir", properties.getTrustStoreDir());
        status.put("trustedKeyIds", properties.getTrustedKeyIds());
        status.put("verifierPublicKeyPath", properties.getVerifierPublicKeyPath());
        status.put("requireAuditPass", properties.isRequireAuditPass());
        status.put("requireContentAllow", properties.isRequireContentAllow());
        status.put("requireNotExpired", properties.isRequireNotExpired());
        status.put("maxPackageSizeMb", properties.getMaxPackageSizeMb());
        status.put("packageExtensions", properties.getPackageExtensions());
        status.put("packagesSeen", packagesSeen.get());
        status.put("packagesVerified", packagesVerified.get());
        status.put("packagesRejected", packagesRejected.get());
        status.put("signatureFailed", signatureFailed.get());
        status.put("hashFailed", hashFailed.get());
        status.put("policyFailed", policyFailed.get());
        status.put("lastError", lastError);
        status.put("lastResult", lastResult);
        return status;
    }

    @Override
    public void clearStatus() {
        packagesSeen.set(0);
        packagesVerified.set(0);
        packagesRejected.set(0);
        signatureFailed.set(0);
        hashFailed.set(0);
        policyFailed.set(0);
        lastError = null;
        lastResult = null;
    }

    private void validateManifest(SecurePublishManifest manifest, String keyId) {
        if (manifest.getVersion() == null || manifest.getVersion() < 2 || manifest.getVersion() > 3) {
            throw new PolicyVerifyException("MANIFEST_VERSION_UNSUPPORTED");
        }
        if (isBlank(manifest.getFileName())) {
            throw new PolicyVerifyException("MANIFEST_FILENAME_EMPTY");
        }
        if (isBlank(manifest.getSha256())) {
            throw new PolicyVerifyException("MANIFEST_SHA256_EMPTY");
        }
        if (manifest.getFileSize() != null && manifest.getFileSize() <= 0) {
            throw new PolicyVerifyException("MANIFEST_FILE_SIZE_INVALID");
        }
        if (properties.isRequireAuditPass() && !"PASS".equalsIgnoreCase(safe(manifest.getScanResult()))) {
            throw new PolicyVerifyException("AUDIT_NOT_PASS");
        }
        boolean version3 = Integer.valueOf(3).equals(manifest.getVersion());
        if (properties.isRequireContentAllow() && (version3 || !isBlank(manifest.getContentDecision()))
                && !"ALLOW".equalsIgnoreCase(safe(manifest.getContentDecision()))) {
            throw new PolicyVerifyException("CONTENT_NOT_ALLOWED");
        }
        if (properties.isRequireNotExpired()) {
            Long expireTime = manifest.getExpireTime();
            if (expireTime == null || expireTime <= System.currentTimeMillis()) {
                throw new PolicyVerifyException("PACKAGE_EXPIRED");
            }
        }
        if (!isTrustedKeyId(keyId)) {
            throw new PolicyVerifyException("KEY_NOT_TRUSTED");
        }
    }

    private void verifySignature(byte[] manifestBytes, SecurePublishManifest manifest,
                                 byte[] signatureBytes, String keyId) throws Exception {
        if (signatureBytes == null || signatureBytes.length == 0) {
            throw new SignatureVerifyException("SIGNATURE_MISSING");
        }
        String algorithm = firstNonBlank(manifest.getAlgorithm(), RSA_SIGNATURE_ALGORITHM);
        if (!algorithm.toUpperCase(Locale.ROOT).contains("RSA")) {
            throw new SignatureVerifyException("UNSUPPORTED_SIGNATURE_ALGORITHM: " + algorithm);
        }
        PublicKey publicKey = loadPublicKey(keyId);
        if (verifyRsa(manifestBytes, signatureBytes, publicKey)) {
            return;
        }
        byte[] canonical = manifest.toCanonicalBytes();
        if (!Arrays.equals(manifestBytes, canonical) && verifyRsa(canonical, signatureBytes, publicKey)) {
            return;
        }
        throw new SignatureVerifyException("SIGNATURE_INVALID");
    }

    private boolean verifyRsa(byte[] data, byte[] signatureBytes, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance(RSA_SIGNATURE_ALGORITHM);
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(signatureBytes);
    }

    private void validatePayload(SecurePublishManifest manifest, byte[] payloadBytes) throws Exception {
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new HashVerifyException("PAYLOAD_EMPTY");
        }
        if (manifest.getFileSize() != null && manifest.getFileSize() != payloadBytes.length) {
            throw new HashVerifyException("PAYLOAD_SIZE_MISMATCH");
        }
        String hash = sha256Hex(payloadBytes);
        if (!hash.equalsIgnoreCase(safe(manifest.getSha256()))) {
            throw new HashVerifyException("HASH_MISMATCH");
        }
    }

    private SecurePackageEntries extractEntries(byte[] packageBytes) throws Exception {
        if (isZip(packageBytes)) {
            return extractZipEntries(packageBytes);
        }
        return extractTarEntries(packageBytes);
    }

    private SecurePackageEntries extractZipEntries(byte[] packageBytes) throws Exception {
        SecurePackageEntries entries = new SecurePackageEntries();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(packageBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalizeEntryName(entry.getName());
                if (MANIFEST_ENTRY.equals(name)) {
                    entries.manifestBytes = readLimited(zip, MAX_MANIFEST_BYTES);
                } else if (SIGNATURE_ENTRY.equals(name)) {
                    entries.signatureBytes = readLimited(zip, MAX_SIGNATURE_BYTES);
                } else if (PAYLOAD_ENTRY.equals(name)) {
                    entries.payloadBytes = readLimited(zip, maxPackageBytes());
                }
            }
        }
        entries.validate();
        return entries;
    }

    private SecurePackageEntries extractTarEntries(byte[] packageBytes) {
        SecurePackageEntries entries = new SecurePackageEntries();
        int offset = 0;
        while (offset + TAR_BLOCK_SIZE <= packageBytes.length) {
            if (isZeroBlock(packageBytes, offset)) {
                break;
            }
            String name = readTarString(packageBytes, offset, 100);
            long size = readTarSize(packageBytes, offset + 124, 12);
            if (isBlank(name) || size < 0 || size > maxPackageBytes()) {
                throw new IllegalArgumentException("PACKAGE_FORMAT_ERROR");
            }
            int dataOffset = offset + TAR_BLOCK_SIZE;
            long dataEnd = dataOffset + size;
            if (dataEnd > packageBytes.length || dataEnd > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("PACKAGE_FORMAT_ERROR");
            }
            String normalizedName = normalizeEntryName(name);
            byte[] value = Arrays.copyOfRange(packageBytes, dataOffset, (int) dataEnd);
            if (MANIFEST_ENTRY.equals(normalizedName)) {
                if (value.length > MAX_MANIFEST_BYTES) {
                    throw new IllegalArgumentException("SPKG_ENTRY_TOO_LARGE: " + MANIFEST_ENTRY);
                }
                entries.manifestBytes = value;
            } else if (SIGNATURE_ENTRY.equals(normalizedName)) {
                if (value.length > MAX_SIGNATURE_BYTES) {
                    throw new IllegalArgumentException("SPKG_ENTRY_TOO_LARGE: " + SIGNATURE_ENTRY);
                }
                entries.signatureBytes = value;
            } else if (PAYLOAD_ENTRY.equals(normalizedName)) {
                entries.payloadBytes = value;
            }
            long paddedSize = ((size + TAR_BLOCK_SIZE - 1) / TAR_BLOCK_SIZE) * TAR_BLOCK_SIZE;
            offset = dataOffset + (int) paddedSize;
        }
        entries.validate();
        return entries;
    }

    private byte[] readLimited(ZipInputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("SPKG_ENTRY_TOO_LARGE");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private PublicKey loadPublicKey(String keyId) throws Exception {
        File keyFile = null;
        if (!isBlank(properties.getTrustStoreDir()) && !isBlank(keyId)) {
            keyFile = new File(properties.getTrustStoreDir(), keyId + ".pub");
        }
        if (keyFile == null || !keyFile.isFile()) {
            if (!isBlank(properties.getVerifierPublicKeyPath())) {
                keyFile = new File(properties.getVerifierPublicKeyPath());
            }
        }
        if (keyFile == null || !keyFile.isFile()) {
            throw new SignatureVerifyException("TRUST_KEY_NOT_FOUND: " + keyId);
        }
        String pem = new String(Files.readAllBytes(keyFile.toPath()), StandardCharsets.US_ASCII).trim();
        if (pem.contains("BEGIN CERTIFICATE")) {
            byte[] certBytes = pemBytes(pem, "CERTIFICATE");
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return factory.generateCertificate(new ByteArrayInputStream(certBytes)).getPublicKey();
        }
        byte[] keyBytes = pemBytes(pem, "PUBLIC KEY");
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private byte[] pemBytes(String text, String type) {
        if (text == null) {
            throw new IllegalArgumentException(type + "_EMPTY");
        }
        String value = text.trim();
        if (value.startsWith("-----BEGIN")) {
            value = value.replace("-----BEGIN " + type + "-----", "")
                    .replace("-----END " + type + "-----", "")
                    .replaceAll("\\s", "");
        } else {
            value = value.replaceAll("\\s", "");
        }
        return Base64.getDecoder().decode(value);
    }

    private boolean isTrustedKeyId(String keyId) {
        if (properties.getTrustedKeyIds() == null || properties.getTrustedKeyIds().isEmpty()) {
            return true;
        }
        boolean hasTrustedKey = false;
        for (String trusted : properties.getTrustedKeyIds()) {
            if (isBlank(trusted)) {
                continue;
            }
            hasTrustedKey = true;
            if (keyId != null && keyId.equalsIgnoreCase(safe(trusted))) {
                return true;
            }
        }
        return !hasTrustedKey;
    }

    private SecurePublishVerifyResult reject(String reason, String packageName, String sourceIp, Long chainId) {
        packagesRejected.incrementAndGet();
        SecurePublishVerifyResult result = SecurePublishVerifyResult.rejected(reason);
        result.setPackageName(packageName);
        lastError = result.getReason();
        lastResult = result;
        writeRejectRecord(result, sourceIp, chainId);
        log.warn("[SecurePublish] package rejected: package={}, reason={}, sourceIp={}, chainId={}",
                packageName, result.getReason(), sourceIp, chainId);
        return result;
    }

    private void writeRejectRecord(SecurePublishVerifyResult result, String sourceIp, Long chainId) {
        if (result == null || isBlank(properties.getRejectedDir())) {
            return;
        }
        try {
            File dir = new File(properties.getRejectedDir());
            Files.createDirectories(dir.toPath());
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("package", result.getPackageName());
            record.put("reason", result.getReason());
            record.put("sourceIp", sourceIp);
            record.put("chainId", chainId);
            record.put("verifyTime", result.getVerifyTime());
            File file = new File(dir, result.getVerifyTime() + "-" + safePackageName(result.getPackageName()) + ".json");
            Files.write(file.toPath(), JSON.toJSONString(record).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[SecurePublish] write reject record failed: {}", e.getMessage());
        }
    }

    private int maxPackageBytes() {
        long max = Math.max(1L, properties.getMaxPackageSizeMb()) * 1024L * 1024L;
        return max > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) max;
    }

    private boolean isZip(byte[] packageBytes) {
        return packageBytes != null && packageBytes.length >= 4
                && packageBytes[0] == 'P'
                && packageBytes[1] == 'K';
    }

    private boolean isZeroBlock(byte[] bytes, int offset) {
        for (int i = 0; i < TAR_BLOCK_SIZE; i++) {
            if (bytes[offset + i] != 0) {
                return false;
            }
        }
        return true;
    }

    private String readTarString(byte[] bytes, int offset, int len) {
        int end = offset;
        int max = Math.min(bytes.length, offset + len);
        while (end < max && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private long readTarSize(byte[] bytes, int offset, int len) {
        String value = readTarString(bytes, offset, len).trim();
        if (value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.replaceAll("[^0-7]", ""), 8);
        } catch (Exception e) {
            return -1L;
        }
    }

    private String normalizeEntryName(String name) {
        String value = safe(name).replace('\\', '/');
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data == null ? new byte[0] : data);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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

    private String safePackageName(String packageName) {
        String value = isBlank(packageName) ? "unknown-package" : packageName.trim();
        return value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private static class SecurePackageEntries {
        private byte[] manifestBytes;
        private byte[] signatureBytes;
        private byte[] payloadBytes;

        private void validate() {
            if (manifestBytes == null || manifestBytes.length == 0) {
                throw new IllegalArgumentException("SPKG_ENTRY_MISSING: " + MANIFEST_ENTRY);
            }
            if (signatureBytes == null || signatureBytes.length == 0) {
                throw new IllegalArgumentException("SPKG_ENTRY_MISSING: " + SIGNATURE_ENTRY);
            }
            if (payloadBytes == null || payloadBytes.length == 0) {
                throw new IllegalArgumentException("SPKG_ENTRY_MISSING: " + PAYLOAD_ENTRY);
            }
        }
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

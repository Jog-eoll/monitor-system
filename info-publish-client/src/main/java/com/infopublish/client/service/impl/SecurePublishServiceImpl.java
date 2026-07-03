package com.infopublish.client.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.infopublish.client.config.AppConfig.SecurePublishProperties;
import com.infopublish.client.entity.SecurePublishKey;
import com.infopublish.client.entity.dto.SecurePublishItem;
import com.infopublish.client.entity.dto.SecurePublishManifest;
import com.infopublish.client.log.DiagnosticLogReport;
import com.infopublish.client.log.DiagnosticLogReporter;
import com.infopublish.client.repository.SecurePublishKeyMapper;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.SecurePublishService;
import com.infopublish.client.service.UkeyLifecycleManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurePublishServiceImpl implements SecurePublishService {

    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String SIGNATURE_ENTRY = "signature.sig";
    private static final String PAYLOAD_ENTRY = "payload.bin";
    private static final String SIGNATURE_PROVIDER_RSA = "rsa";
    private static final String SIGNATURE_PROVIDER_RSA_DB = "rsa-db";
    private static final String SIGNATURE_PROVIDER_VAUTH = "vauth-envelope";
    private static final String RSA_SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String DB_KEY_ROLE_SIGNER = "SIGNER";
    private static final String DB_KEY_STATUS_ACTIVE = "ACTIVE";
    private static final String DB_KEY_STATUS_INACTIVE = "INACTIVE";
    private static final Set<String> DEFAULT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "bmp", "gif", "webp",
            "mp4", "avi", "mov", "mkv", "wmv", "flv", "mpeg", "mpg", "ts",
            "txt", "nmg", "pmg"));

    private final SecurePublishProperties properties;
    private final ClientAuthService clientAuthService;
    private final UkeyLifecycleManager lifecycleManager;
    private final SecurePublishKeyMapper securePublishKeyMapper;

    @Resource
    private DiagnosticLogReporter diagnosticLogReporter;

    private final ConcurrentMap<String, SecurePublishItem> items = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> itemOrder = new ConcurrentLinkedDeque<>();
    private final AtomicLong packagesSeen = new AtomicLong();
    private final AtomicLong packagesVerified = new AtomicLong();
    private final AtomicLong packagesRejected = new AtomicLong();
    private final AtomicLong testPackagesCreated = new AtomicLong();
    private final AtomicLong signerPackagesCreated = new AtomicLong();
    private final AtomicLong signerFilesRejected = new AtomicLong();
    private final AtomicLong signerAuditPassed = new AtomicLong();
    private final AtomicLong signerAuditRejected = new AtomicLong();
    private final AtomicLong signerAuditFailed = new AtomicLong();

    private volatile String lastError;
    private volatile String lastSignerError;
    private volatile String lastSignerAuditError;
    private volatile long lastScanAt;
    private volatile long lastSignerScanAt;
    private volatile long lastSignerAuditAt;

    @Scheduled(fixedDelayString = "${secure-publish.scan-interval-ms:2000}")
    public void scheduledScan() {
        if (properties.isEnabled()) {
            scanNow();
        }
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("incomingDir", properties.getIncomingDir());
        status.put("verifiedDir", properties.getVerifiedDir());
        status.put("runtimeDir", properties.getRuntimeDir());
        status.put("rejectedDir", properties.getRejectedDir());
        status.put("packagesSeen", packagesSeen.get());
        status.put("packagesVerified", packagesVerified.get());
        status.put("packagesRejected", packagesRejected.get());
        status.put("testPackagesCreated", testPackagesCreated.get());
        status.put("signerEnabled", properties.isSignerEnabled());
        status.put("signerPackagesCreated", signerPackagesCreated.get());
        status.put("signerFilesRejected", signerFilesRejected.get());
        status.put("signatureProvider", signatureProvider());
        status.put("signatureKeyId", configuredSignatureKeyId());
        status.put("keyStorage", SIGNATURE_PROVIDER_RSA_DB.equals(signatureProvider()) ? "database" : "file");
        if (properties.isSignerEnabled() || properties.isTestPackageEnabled()) {
            status.put("signerPrivateKeyPath", properties.getSignerPrivateKeyPath());
        }
        status.put("verifierPublicKeyPath", properties.getVerifierPublicKeyPath());
        status.put("lastScanAt", lastScanAt);
        status.put("lastSignerScanAt", lastSignerScanAt);
        status.put("lastError", lastError);
        status.put("lastSignerError", lastSignerError);
        status.put("items", items.size());
        return status;
    }

    @Override
    public List<SecurePublishItem> listItems() {
        List<SecurePublishItem> result = new ArrayList<>();
        for (String key : itemOrder) {
            SecurePublishItem item = items.get(key);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> scanNow() {
        Map<String, Object> result = new LinkedHashMap<>();
        int scanned = 0;
        int verified = 0;
        int rejected = 0;
        lastScanAt = System.currentTimeMillis();
        try {
            ensureDirectories();
            File incoming = new File(properties.getIncomingDir());
            File[] packages = incoming.listFiles((dir, name) -> name != null && name.toLowerCase(Locale.ROOT).endsWith(".spkg"));
            if (packages == null) {
                packages = new File[0];
            }
            Arrays.sort(packages, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File spkg : packages) {
                scanned++;
                packagesSeen.incrementAndGet();
                SecurePublishItem item = processPackage(spkg);
                if ("VERIFIED".equalsIgnoreCase(item.getStatus())) {
                    verified++;
                } else {
                    rejected++;
                }
            }
            lastError = null;
        } catch (Exception e) {
            lastError = e.getMessage();
            log.warn("[SecurePublish] scan failed: {}", e.getMessage(), e);
        }
        result.put("scanned", scanned);
        result.put("verified", verified);
        result.put("rejected", rejected);
        result.put("lastError", lastError);
        return result;
    }

    @Override
    public Map<String, Object> createTestPackage(String payloadPath, String outputDir) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!properties.isTestPackageEnabled()) {
            result.put("created", false);
            result.put("error", "secure-publish.test-package-enabled is false");
            return result;
        }
        try {
            File payload = requirePayloadFile(payloadPath);
            File outDir = new File(trimToNull(outputDir) == null ? properties.getIncomingDir() : outputDir);
            Map<String, Object> created = createSignedPackage(payload, outDir, configuredPublisher());
            testPackagesCreated.incrementAndGet();
            result.putAll(created);
        } catch (Exception e) {
            result.put("created", false);
            result.put("error", e.getMessage());
            lastError = e.getMessage();
            log.warn("[SecurePublish] test package create failed: {}", e.getMessage(), e);
        }
        return result;
    }

    @Override
    public Map<String, Object> createTestKeyPair(boolean overwrite) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!properties.isTestPackageEnabled()) {
            result.put("created", false);
            result.put("error", "secure-publish.test-package-enabled is false");
            return result;
        }
        return createKeyPair(overwrite, "test");
    }

    @Override
    public Map<String, Object> getSignerStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isSignerEnabled());
        status.put("signatureProvider", signatureProvider());
        status.put("signatureKeyId", configuredSignatureKeyId());
        status.put("keyStorage", SIGNATURE_PROVIDER_RSA_DB.equals(signatureProvider()) ? "database" : "file");
        status.put("publisher", configuredPublisher());
        status.put("inputDir", properties.getSignerInputDir());
        status.put("outputDir", properties.getSignerOutputDir());
        status.put("archiveDir", properties.getSignerArchiveDir());
        status.put("rejectedDir", properties.getSignerRejectedDir());
        if (properties.isSignerEnabled() && !SIGNATURE_PROVIDER_RSA_DB.equals(signatureProvider())) {
            status.put("privateKeyPath", properties.getSignerPrivateKeyPath());
        }
        status.put("publicKeyPath", properties.getVerifierPublicKeyPath());
        status.put("auditEnabled", properties.isSignerAuditEnabled());
        status.put("auditUrl", properties.getSignerAuditUrl());
        status.put("auditFailPolicy", properties.getSignerAuditFailPolicy());
        status.put("auditPolicyVersion", properties.getSignerAuditPolicyVersion());
        status.put("requireAuditPass", properties.isRequireAuditPass());
        status.put("auditPassed", signerAuditPassed.get());
        status.put("auditRejected", signerAuditRejected.get());
        status.put("auditFailed", signerAuditFailed.get());
        status.put("lastAuditAt", lastSignerAuditAt);
        status.put("lastAuditError", lastSignerAuditError);
        status.put("packagesCreated", signerPackagesCreated.get());
        status.put("filesRejected", signerFilesRejected.get());
        status.put("lastScanAt", lastSignerScanAt);
        status.put("lastError", lastSignerError);
        return status;
    }

    @Override
    public Map<String, Object> createSignerKeyPair(boolean overwrite) {
        if (!properties.isSignerEnabled()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("created", false);
            result.put("error", "secure-publish.signer-enabled is false");
            return result;
        }
        return createKeyPair(overwrite, "signer");
    }

    @Override
    public Map<String, Object> signPackage(String payloadPath, String outputDir) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!properties.isSignerEnabled()) {
            result.put("created", false);
            result.put("error", "secure-publish.signer-enabled is false");
            return result;
        }
        try {
            File payload = requirePayloadFile(payloadPath);
            File outDir = new File(trimToNull(outputDir) == null ? properties.getSignerOutputDir() : outputDir);
            Map<String, Object> created = createSignedPackage(payload, outDir, configuredPublisher());
            signerPackagesCreated.incrementAndGet();
            lastSignerError = null;
            result.putAll(created);
        } catch (Exception e) {
            result.put("created", false);
            result.put("error", e.getMessage());
            lastSignerError = e.getMessage();
            log.warn("[SecurePublish] signer package create failed: {}", e.getMessage(), e);
        }
        return result;
    }

    @Override
    public Map<String, Object> signUploadedFiles(MultipartFile[] files, String[] relativePaths, String outputDir) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!properties.isSignerEnabled()) {
            result.put("created", false);
            result.put("error", "secure-publish.signer-enabled is false");
            return result;
        }
        if (files == null || files.length == 0) {
            result.put("created", false);
            result.put("error", "files is required");
            return result;
        }

        int uploaded = 0;
        int created = 0;
        int rejected = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        File uploadDir = new File(properties.getTempDir(), "signer-upload-" + System.currentTimeMillis());
        try {
            ensureSignerDirectories();
            Files.createDirectories(uploadDir.toPath());
            File outDir = new File(trimToNull(outputDir) == null ? properties.getSignerOutputDir() : outputDir);
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                Map<String, Object> detail = new LinkedHashMap<>();
                String relativePath = relativePaths != null && i < relativePaths.length ? relativePaths[i] : null;
                String originalName = firstNonBlank(relativePath, file == null ? null : file.getOriginalFilename());
                detail.put("source", safe(originalName));
                try {
                    if (file == null || file.isEmpty()) {
                        throw new IllegalArgumentException("UPLOAD_FILE_EMPTY");
                    }
                    String payloadName = sanitizeFileName(new File(originalName == null ? file.getOriginalFilename() : originalName).getName());
                    if (!isAllowedExtension(payloadName)) {
                        throw new IllegalArgumentException("PAYLOAD_EXTENSION_NOT_ALLOWED");
                    }
                    File tempPayload = uniqueFile(uploadDir, payloadName);
                    file.transferTo(tempPayload);
                    uploaded++;
                    Map<String, Object> signed = createSignedPackage(tempPayload, outDir, configuredPublisher());
                    signerPackagesCreated.incrementAndGet();
                    created++;
                    detail.put("created", true);
                    detail.put("packagePath", signed.get("packagePath"));
                    detail.put("fileHash", signed.get("fileHash"));
                    detail.put("fileSize", signed.get("fileSize"));
                    detail.put("auditDecision", signed.get("auditDecision"));
                    detail.put("auditRiskLevel", signed.get("auditRiskLevel"));
                    detail.put("auditMessage", signed.get("auditMessage"));
                    detail.put("auditReason", signed.get("auditReason"));
                } catch (Exception e) {
                    rejected++;
                    signerFilesRejected.incrementAndGet();
                    detail.put("created", false);
                    detail.put("error", e.getMessage());
                    lastSignerError = e.getMessage();
                    log.warn("[SecurePublish] signer upload rejected file={}, reason={}", originalName, e.getMessage());
                }
                details.add(detail);
            }
            if (rejected == 0) {
                lastSignerError = null;
            }
        } catch (Exception e) {
            lastSignerError = e.getMessage();
            result.put("error", e.getMessage());
            log.warn("[SecurePublish] signer upload failed: {}", e.getMessage(), e);
        } finally {
            deleteDirectoryQuietly(uploadDir);
        }

        result.put("created", created > 0);
        result.put("uploaded", uploaded);
        result.put("signed", created);
        result.put("rejected", rejected);
        result.put("outputDir", trimToNull(outputDir) == null ? properties.getSignerOutputDir() : outputDir);
        result.put("details", details);
        result.put("lastError", lastSignerError);
        return result;
    }

    @Override
    public Map<String, Object> scanSignerInput() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!properties.isSignerEnabled()) {
            result.put("enabled", false);
            result.put("error", "secure-publish.signer-enabled is false");
            return result;
        }
        int scanned = 0;
        int signed = 0;
        int rejected = 0;
        lastSignerScanAt = System.currentTimeMillis();
        try {
            ensureSignerDirectories();
            File inputDir = new File(requirePath(properties.getSignerInputDir(), "SIGNER_INPUT_DIR_EMPTY"));
            File[] files = inputDir.listFiles(file -> file != null
                    && file.isFile()
                    && !file.getName().toLowerCase(Locale.ROOT).endsWith(".spkg"));
            if (files == null) {
                files = new File[0];
            }
            Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File payload : files) {
                scanned++;
                try {
                    if (!isAllowedExtension(payload.getName())) {
                        throw new IllegalArgumentException("PAYLOAD_EXTENSION_NOT_ALLOWED");
                    }
                    createSignedPackage(payload, new File(properties.getSignerOutputDir()), configuredPublisher());
                    signerPackagesCreated.incrementAndGet();
                    movePackage(payload, new File(properties.getSignerArchiveDir()));
                    signed++;
                } catch (Exception e) {
                    signerFilesRejected.incrementAndGet();
                    movePackageQuietly(payload, new File(properties.getSignerRejectedDir()));
                    rejected++;
                    lastSignerError = e.getMessage();
                    log.warn("[SecurePublish] signer scan rejected file={}, reason={}", payload.getName(), e.getMessage());
                }
            }
            if (rejected == 0) {
                lastSignerError = null;
            }
        } catch (Exception e) {
            lastSignerError = e.getMessage();
            log.warn("[SecurePublish] signer scan failed: {}", e.getMessage(), e);
        }
        result.put("enabled", true);
        result.put("scanned", scanned);
        result.put("signed", signed);
        result.put("rejected", rejected);
        result.put("lastError", lastSignerError);
        return result;
    }

    private Map<String, Object> createKeyPair(boolean overwrite, String scope) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (SIGNATURE_PROVIDER_RSA_DB.equals(signatureProvider())) {
                return createDatabaseKeyPair(overwrite);
            }
            File privateKeyFile = new File(requirePath(properties.getSignerPrivateKeyPath(), "SIGNER_PRIVATE_KEY_PATH_EMPTY"));
            File publicKeyFile = new File(requirePath(properties.getVerifierPublicKeyPath(), "VERIFIER_PUBLIC_KEY_PATH_EMPTY"));
            if (!overwrite && (privateKeyFile.exists() || publicKeyFile.exists())) {
                result.put("created", false);
                result.put("error", "KEYPAIR_ALREADY_EXISTS");
                result.put("privateKeyPath", privateKeyFile.getAbsolutePath());
                result.put("publicKeyPath", publicKeyFile.getAbsolutePath());
                return result;
            }
            createParentDirectories(privateKeyFile);
            createParentDirectories(publicKeyFile);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            writePem(privateKeyFile, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
            writePem(publicKeyFile, "PUBLIC KEY", keyPair.getPublic().getEncoded());
            result.put("created", true);
            result.put("keyStorage", "file");
            result.put("privateKeyPath", privateKeyFile.getAbsolutePath());
            result.put("publicKeyPath", publicKeyFile.getAbsolutePath());
        } catch (Exception e) {
            result.put("created", false);
            result.put("error", e.getMessage());
            if ("signer".equals(scope)) {
                lastSignerError = e.getMessage();
            } else {
                lastError = e.getMessage();
            }
            log.warn("[SecurePublish] {} keypair create failed: {}", scope, e.getMessage(), e);
        }
        return result;
    }

    private Map<String, Object> createDatabaseKeyPair(boolean overwrite) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        String keyId = configuredSignatureKeyId();
        SecurePublishKey existing = findActiveDatabaseKey(keyId);
        if (!overwrite && existing != null) {
            result.put("created", false);
            result.put("error", "KEYPAIR_ALREADY_EXISTS");
            result.put("keyStorage", "database");
            result.put("keyId", keyId);
            result.put("publicKeyFingerprint", publicKeyFingerprint(existing.getPublicKeyPem()));
            return result;
        }
        if (overwrite && existing != null) {
            LambdaUpdateWrapper<SecurePublishKey> update = new LambdaUpdateWrapper<>();
            update.eq(SecurePublishKey::getKeyId, keyId)
                    .eq(SecurePublishKey::getKeyRole, DB_KEY_ROLE_SIGNER)
                    .eq(SecurePublishKey::getStatus, DB_KEY_STATUS_ACTIVE)
                    .set(SecurePublishKey::getStatus, DB_KEY_STATUS_INACTIVE)
                    .set(SecurePublishKey::getUpdatedAt, LocalDateTime.now());
            securePublishKeyMapper.update(null, update);
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        SecurePublishKey row = new SecurePublishKey();
        row.setKeyId(keyId);
        row.setKeyRole(DB_KEY_ROLE_SIGNER);
        row.setAlgorithm(RSA_SIGNATURE_ALGORITHM);
        row.setPrivateKeyPem(toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        row.setPublicKeyPem(toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        row.setStatus(DB_KEY_STATUS_ACTIVE);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        securePublishKeyMapper.insert(row);

        result.put("created", true);
        result.put("keyStorage", "database");
        result.put("keyId", keyId);
        result.put("algorithm", RSA_SIGNATURE_ALGORITHM);
        result.put("publicKeyFingerprint", sha256Hex(keyPair.getPublic().getEncoded()));
        return result;
    }

    private SecurePublishItem processPackage(File spkg) {
        SecurePublishItem item = new SecurePublishItem();
        long now = System.currentTimeMillis();
        item.setPackageName(spkg.getName());
        item.setPackagePath(spkg.getAbsolutePath());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        try {
            verifyAndAdmit(spkg, item);
            item.setStatus("VERIFIED");
            item.setReason("SIGNATURE_AND_HASH_VERIFIED");
            packagesVerified.incrementAndGet();
            movePackageQuietly(spkg, new File(properties.getVerifiedDir()));
            reportClientPackageVerified(item.getPackageName(), true, item.getFileHash(),
                    item.getPayloadName(), item.getStatus(), item.getReason(), null);
            log.info("[SecurePublish] package verified: package={}, payload={}, runtime={}",
                    item.getPackageName(), item.getPayloadName(), item.getRuntimePath());
        } catch (Exception e) {
            item.setStatus("REJECTED");
            item.setReason(e.getMessage());
            item.setUpdatedAt(System.currentTimeMillis());
            packagesRejected.incrementAndGet();
            lastError = e.getMessage();
            movePackageQuietly(spkg, new File(properties.getRejectedDir()));
            reportClientPackageVerified(item.getPackageName(), false, item.getFileHash(),
                    item.getPayloadName(), item.getStatus(), item.getReason(), e.getMessage());
            log.warn("[SecurePublish] package rejected: package={}, reason={}", item.getPackageName(), e.getMessage());
        }
        saveItem(item);
        return item;
    }

    private Map<String, Object> createSignedPackage(File payload, File outDir, String publisher) throws Exception {
        if (payload == null || !payload.isFile()) {
            throw new IllegalArgumentException("payload file not found");
        }
        if (!isAllowedExtension(payload.getName())) {
            throw new IllegalArgumentException("PAYLOAD_EXTENSION_NOT_ALLOWED");
        }
        ensureDirectories();
        if (outDir == null) {
            throw new IllegalArgumentException("OUTPUT_DIR_EMPTY");
        }
        Files.createDirectories(outDir.toPath());
        Map<String, Object> result = null;
        try {
            SecurePublishManifest manifest = buildManifest(payload, publisher);
            SignerAuditResult audit = auditBeforeSign(payload, manifest);
            applyAuditResult(manifest, audit);
            byte[] manifestBytes = manifest.toCanonicalBytes();
            byte[] signature = signManifest(manifestBytes);
            if (signature == null || signature.length == 0) {
                throw new IllegalStateException("signature is empty");
            }
            File output = uniqueFile(outDir, stripExtension(payload.getName()) + ".spkg");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
                putBytes(zip, MANIFEST_ENTRY, manifest.toCanonicalJson().getBytes(StandardCharsets.UTF_8));
                putBytes(zip, SIGNATURE_ENTRY, signature);
                zip.putNextEntry(new ZipEntry(PAYLOAD_ENTRY));
                try (InputStream in = new FileInputStream(payload)) {
                    copy(in, zip);
                }
                zip.closeEntry();
            }
            result = new LinkedHashMap<>();
            result.put("created", true);
            result.put("packagePath", output.getAbsolutePath());
            result.put("payloadPath", payload.getAbsolutePath());
            result.put("fileHash", manifest.getSha256());
            result.put("fileSize", manifest.getFileSize());
            result.put("publisher", manifest.getPublisher());
            result.put("algorithm", manifest.getAlgorithm());
            result.put("auditDecision", manifest.getScanResult());
            result.put("auditRiskLevel", manifest.getRiskLevel());
            result.put("auditMessage", manifest.getAuditMessage());
            result.put("auditReason", manifest.getAuditReason());
            reportClientContentSigned(payload.getName(), manifest, publisher, null);
            return result;
        } catch (Exception e) {
            reportClientContentSigned(payload.getName(), null, publisher, e.getMessage());
            throw e;
        }
    }

    private File requirePayloadFile(String payloadPath) {
        String path = trimToNull(payloadPath);
        if (path == null) {
            throw new IllegalArgumentException("payloadPath is required");
        }
        File payload = new File(path);
        if (!payload.isFile()) {
            throw new IllegalArgumentException("payload file not found");
        }
        return payload;
    }

    private SignerAuditResult auditBeforeSign(File payload, SecurePublishManifest manifest) {
        lastSignerAuditAt = System.currentTimeMillis();
        if (!properties.isSignerAuditEnabled()) {
            return SignerAuditResult.pass("LOW", "该发布内容合规，继续传输", "SIGNER_AUDIT_DISABLED", null);
        }
        String auditUrl = trimToNull(properties.getSignerAuditUrl());
        if (auditUrl == null) {
            signerAuditFailed.incrementAndGet();
            lastSignerAuditError = "SIGNER_AUDIT_URL_EMPTY";
            return handleAuditFailure("SIGNER_AUDIT_URL_EMPTY");
        }
        try {
            HttpEntity<MultiValueMap<String, Object>> request = buildSignerAuditRequest(payload, manifest);
            ResponseEntity<Map> response = auditRestTemplate().postForEntity(auditUrl, request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                signerAuditFailed.incrementAndGet();
                lastSignerAuditError = "HTTP_" + response.getStatusCodeValue();
                return handleAuditFailure(lastSignerAuditError);
            }
            SignerAuditResult result = parseSignerAuditResponse(response.getBody());
            if ("PASS".equals(result.decision)) {
                signerAuditPassed.incrementAndGet();
                lastSignerAuditError = null;
                return result;
            }
            signerAuditRejected.incrementAndGet();
            lastSignerAuditError = result.reason;
            return result;
        } catch (RestClientException e) {
            signerAuditFailed.incrementAndGet();
            lastSignerAuditError = e.getMessage();
            return handleAuditFailure("SIGNER_AUDIT_EXCEPTION: " + e.getMessage());
        } catch (Exception e) {
            signerAuditFailed.incrementAndGet();
            lastSignerAuditError = e.getMessage();
            return handleAuditFailure("SIGNER_AUDIT_EXCEPTION: " + e.getMessage());
        }
    }

    private HttpEntity<MultiValueMap<String, Object>> buildSignerAuditRequest(File payload,
                                                                              SecurePublishManifest manifest) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("auditId", "signer_" + System.currentTimeMillis() + "_" + Math.abs(payload.getAbsolutePath().hashCode()));
        metadata.put("clientId", defaultIfBlank(configuredPublisher(), "secure-publish-signer"));
        metadata.put("fileName", manifest.getFileName());
        metadata.put("fileHash", manifest.getSha256());
        metadata.put("fileSize", manifest.getFileSize());
        metadata.put("contentType", contentTypeForFileName(manifest.getFileName()));
        metadata.put("totalPackets", 1);
        body.add("metadata", JSON.toJSONString(metadata));
        body.add("file", new ByteArrayResource(Files.readAllBytes(payload.toPath())) {
            @Override
            public String getFilename() {
                return manifest.getFileName();
            }
        });
        return new HttpEntity<>(body, headers);
    }

    private SignerAuditResult parseSignerAuditResponse(Map<?, ?> payload) {
        if (payload == null) {
            return SignerAuditResult.reject("UNKNOWN", "发布内容检测异常，需人工审查。", "EMPTY_AUDIT_RESPONSE", null);
        }
        Object codeObj = firstValue(payload, "code", "状态码");
        int code = parseInt(codeObj, -1);
        if (code != 200 && code != 0) {
            String message = safeString(firstValue(payload, "message", "msg", "消息"), "REMOTE_AUDIT_FAILED");
            return SignerAuditResult.reject("UNKNOWN", "发布内容检测异常，需人工审查。", message, null);
        }
        Object dataObj = firstValue(payload, "data", "数据");
        if (!(dataObj instanceof Map)) {
            return SignerAuditResult.reject("UNKNOWN", "发布内容检测异常，需人工审查。", "AUDIT_DATA_EMPTY", null);
        }
        Map<?, ?> data = (Map<?, ?>) dataObj;
        String decision = normalizeAuditDecision(safeString(firstValue(data, "decision", "审核结果"), "NEED_MANUAL"));
        String reason = safeString(firstValue(data, "reason", "原因"), "");
        String riskLevel = safeString(firstValue(data, "riskLevel", "违规等级"), "");
        String recordId = safeString(firstValue(data, "recordId", "id"), "");
        if ("PASS".equals(decision)) {
            return SignerAuditResult.pass(defaultIfBlank(riskLevel, "LOW"),
                    "该发布内容合规，继续传输", defaultIfBlank(reason, "MODEL_APPROVED"), recordId);
        }
        String message = "该发布内容含" + defaultIfBlank(reason, defaultIfBlank(riskLevel, "疑似违规"))
                + "内容，不合规，请审查。";
        return SignerAuditResult.reject(defaultIfBlank(riskLevel, "HIGH"), message,
                defaultIfBlank(reason, "MODEL_REJECTED"), recordId);
    }

    private SignerAuditResult handleAuditFailure(String reason) {
        if ("allow".equalsIgnoreCase(safe(properties.getSignerAuditFailPolicy()))) {
            return SignerAuditResult.pass("UNKNOWN", "发布内容检测异常，已按配置放行。", reason, null);
        }
        return SignerAuditResult.reject("UNKNOWN", "发布内容检测异常，需人工审查。", reason, null);
    }

    private RestTemplate auditRestTemplate() {
        int timeout = Math.max(1000, properties.getSignerAuditTimeoutMs());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private void applyAuditResult(SecurePublishManifest manifest, SignerAuditResult audit) {
        if (audit == null) {
            audit = SignerAuditResult.reject("UNKNOWN", "发布内容检测异常，需人工审查。", "AUDIT_RESULT_NULL", null);
        }
        manifest.setScanResult(audit.decision);
        manifest.setRiskLevel(audit.riskLevel);
        manifest.setAuditMessage(audit.message);
        manifest.setAuditReason(audit.reason);
        manifest.setAuditRecordId(audit.recordId);
        if (!"PASS".equals(audit.decision)) {
            throw new IllegalStateException(audit.message + " reason=" + audit.reason);
        }
    }

    private void verifyAndAdmit(File spkg, SecurePublishItem item) throws Exception {
        long maxBytes = Math.max(1L, properties.getMaxPackageSizeMb()) * 1024L * 1024L;
        if (spkg.length() <= 0 || spkg.length() > maxBytes) {
            throw new IllegalArgumentException("PACKAGE_SIZE_NOT_ALLOWED");
        }
        File tempPayload = uniqueFile(new File(properties.getTempDir()), spkg.getName() + ".payload.tmp");
        boolean admitted = false;
        try {
            SecurePublishManifest manifest;
            byte[] signature;
            try (ZipFile zipFile = new ZipFile(spkg)) {
                byte[] manifestBytes = readSmallEntry(zipFile, MANIFEST_ENTRY, 1024 * 1024);
                signature = readSmallEntry(zipFile, SIGNATURE_ENTRY, 4 * 1024 * 1024);
                manifest = SecurePublishManifest.fromJson(manifestBytes);
                if (manifest == null) {
                    throw new IllegalArgumentException("MANIFEST_INVALID");
                }
                extractPayload(zipFile, tempPayload);
            }

            validateManifest(manifest);
            verifyManifestSignature(manifest.toCanonicalBytes(), signature);

            long fileSize = tempPayload.length();
            if (manifest.getFileSize() == null || manifest.getFileSize() != fileSize) {
                throw new IllegalStateException("PAYLOAD_SIZE_MISMATCH");
            }
            String hash = sha256Hex(tempPayload);
            if (!hash.equalsIgnoreCase(safe(manifest.getSha256()))) {
                throw new IllegalStateException("PAYLOAD_HASH_MISMATCH");
            }

            String payloadName = sanitizeFileName(manifest.getFileName());
            if (!isAllowedExtension(payloadName)) {
                throw new IllegalArgumentException("PAYLOAD_EXTENSION_NOT_ALLOWED");
            }
            File runtimeFile = new File(properties.getRuntimeDir(), payloadName);
            Files.createDirectories(runtimeFile.getParentFile().toPath());
            Files.move(tempPayload.toPath(), runtimeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            admitted = true;

            item.setPayloadName(payloadName);
            item.setRuntimePath(runtimeFile.getAbsolutePath());
            item.setFileHash(hash);
            item.setFileSize(fileSize);
            item.setScanResult(manifest.getScanResult());
            item.setRiskLevel(manifest.getRiskLevel());
            item.setAuditMessage(manifest.getAuditMessage());
            item.setAuditReason(manifest.getAuditReason());
            item.setUpdatedAt(System.currentTimeMillis());
        } finally {
            if (!admitted && tempPayload.exists() && !tempPayload.delete()) {
                log.debug("[SecurePublish] temp payload cleanup skipped: {}", tempPayload.getAbsolutePath());
            }
        }
    }

    private void validateManifest(SecurePublishManifest manifest) {
        if (manifest.getVersion() == null || manifest.getVersion() != 2) {
            throw new IllegalArgumentException("MANIFEST_VERSION_UNSUPPORTED");
        }
        if (trimToNull(manifest.getFileName()) == null) {
            throw new IllegalArgumentException("MANIFEST_FILENAME_EMPTY");
        }
        if (manifest.getFileSize() == null || manifest.getFileSize() <= 0) {
            throw new IllegalArgumentException("MANIFEST_FILE_SIZE_INVALID");
        }
        if (trimToNull(manifest.getSha256()) == null) {
            throw new IllegalArgumentException("MANIFEST_SHA256_EMPTY");
        }
        if (properties.isRequireAuditPass() && !"PASS".equalsIgnoreCase(safe(manifest.getScanResult()))) {
            throw new IllegalArgumentException("MANIFEST_AUDIT_NOT_PASS");
        }
        Long expireTime = manifest.getExpireTime();
        if (expireTime == null || expireTime <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("MANIFEST_EXPIRED");
        }
    }

    private SecurePublishManifest buildManifest(File payload, String publisher) throws Exception {
        SecurePublishManifest manifest = new SecurePublishManifest();
        long now = System.currentTimeMillis();
        manifest.setVersion(2);
        manifest.setFileName(payload.getName());
        manifest.setFileSize(payload.length());
        manifest.setMediaType(Files.probeContentType(payload.toPath()));
        manifest.setSha256(sha256Hex(payload));
        manifest.setPublisher(publisher);
        manifest.setSignTime(now);
        manifest.setExpireTime(now + Math.max(60_000L, properties.getPackageExpireMs()));
        manifest.setPolicyVersion(defaultIfBlank(properties.getSignerAuditPolicyVersion(), "v1"));
        manifest.setAlgorithm(isRsaSignatureProvider() ? RSA_SIGNATURE_ALGORITHM : "SM2-SIGN");
        return manifest;
    }

    private byte[] signManifest(byte[] manifestBytes) throws Exception {
        String provider = signatureProvider();
        if (SIGNATURE_PROVIDER_RSA.equals(provider)) {
            PrivateKey privateKey = loadPrivateKey(properties.getSignerPrivateKeyPath());
            Signature signature = Signature.getInstance(RSA_SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(manifestBytes);
            return signature.sign();
        }
        if (SIGNATURE_PROVIDER_RSA_DB.equals(provider)) {
            PrivateKey privateKey = loadPrivateKeyFromDatabase();
            Signature signature = Signature.getInstance(RSA_SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(manifestBytes);
            return signature.sign();
        }
        if (SIGNATURE_PROVIDER_VAUTH.equals(provider)) {
            return clientAuthService.signEnvelopeWithControlPlatform(
                    lifecycleManager.getCurrentUkeyPath(), manifestBytes);
        }
        throw new IllegalArgumentException("UNSUPPORTED_SIGNATURE_PROVIDER: " + provider);
    }

    private void verifyManifestSignature(byte[] manifestBytes, byte[] signatureBytes) throws Exception {
        String provider = signatureProvider();
        if (SIGNATURE_PROVIDER_RSA.equals(provider)) {
            PublicKey publicKey = loadPublicKey(properties.getVerifierPublicKeyPath());
            Signature signature = Signature.getInstance(RSA_SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(manifestBytes);
            if (!signature.verify(signatureBytes)) {
                throw new IllegalStateException("SIGNATURE_VERIFY_FAILED");
            }
            return;
        }
        if (SIGNATURE_PROVIDER_RSA_DB.equals(provider)) {
            PublicKey publicKey = loadPublicKeyFromDatabase();
            Signature signature = Signature.getInstance(RSA_SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(manifestBytes);
            if (!signature.verify(signatureBytes)) {
                throw new IllegalStateException("SIGNATURE_VERIFY_FAILED");
            }
            return;
        }
        if (SIGNATURE_PROVIDER_VAUTH.equals(provider)) {
            byte[] verifiedManifest = clientAuthService.verifyEnvelopeWithControlPlatform(
                    lifecycleManager.getCurrentUkeyPath(), signatureBytes);
            if (verifiedManifest == null || !Arrays.equals(manifestBytes, verifiedManifest)) {
                throw new IllegalStateException("SIGNATURE_MANIFEST_MISMATCH");
            }
            return;
        }
        throw new IllegalArgumentException("UNSUPPORTED_SIGNATURE_PROVIDER: " + provider);
    }

    private byte[] readSmallEntry(ZipFile zipFile, String name, int maxBytes) throws Exception {
        ZipEntry entry = zipFile.getEntry(name);
        if (entry == null || entry.isDirectory()) {
            throw new IllegalArgumentException("SPKG_ENTRY_MISSING: " + name);
        }
        if (entry.getSize() > maxBytes) {
            throw new IllegalArgumentException("SPKG_ENTRY_TOO_LARGE: " + name);
        }
        try (InputStream in = zipFile.getInputStream(entry);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copyLimited(in, out, maxBytes);
            return out.toByteArray();
        }
    }

    private void extractPayload(ZipFile zipFile, File target) throws Exception {
        ZipEntry entry = zipFile.getEntry(PAYLOAD_ENTRY);
        if (entry == null || entry.isDirectory()) {
            throw new IllegalArgumentException("SPKG_ENTRY_MISSING: " + PAYLOAD_ENTRY);
        }
        Files.createDirectories(target.getParentFile().toPath());
        try (InputStream in = zipFile.getInputStream(entry);
             OutputStream out = new FileOutputStream(target)) {
            copy(in, out);
        }
    }

    private void putBytes(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes == null ? new byte[0] : bytes);
        zip.closeEntry();
    }

    private void copyLimited(InputStream in, OutputStream out, int maxBytes) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("SPKG_ENTRY_TOO_LARGE");
            }
            out.write(buffer, 0, read);
        }
    }

    private void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    private String sha256Hex(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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

    private boolean isAllowedExtension(String fileName) {
        String extension = extensionOf(fileName);
        if (extension == null) {
            return false;
        }
        Set<String> allowed = configuredExtensions();
        return allowed.contains(extension);
    }

    private Set<String> configuredExtensions() {
        String configured = trimToNull(properties.getAllowedExtensions());
        if (configured == null) {
            return DEFAULT_EXTENSIONS;
        }
        Set<String> allowed = new HashSet<>();
        for (String item : configured.split(",")) {
            String value = trimToNull(item);
            if (value != null) {
                allowed.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return allowed.isEmpty() ? DEFAULT_EXTENSIONS : allowed;
    }

    private String contentTypeForFileName(String fileName) {
        String ext = extensionOf(fileName);
        if (ext == null) {
            return "file";
        }
        if (Arrays.asList("jpg", "jpeg", "png", "bmp", "gif", "webp").contains(ext)) {
            return "image";
        }
        if (Arrays.asList("mp4", "avi", "mov", "mkv", "wmv", "flv", "mpeg", "mpg", "ts").contains(ext)) {
            return "video";
        }
        if (Arrays.asList("txt", "nmg", "pmg", "json", "xml", "csv", "log").contains(ext)) {
            return "text";
        }
        return "file";
    }

    private Object firstValue(Map<?, ?> data, String... keys) {
        if (data == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key != null && data.containsKey(key)) {
                return data.get(key);
            }
        }
        return null;
    }

    private int parseInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String normalizeAuditDecision(String decision) {
        String value = trimToNull(decision);
        if (value == null) {
            return "NEED_MANUAL";
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if ("PASS".equals(upper) || "ALLOW".equals(upper) || "APPROVED".equals(upper)) {
            return "PASS";
        }
        if ("REJECT".equals(upper) || "DENY".equals(upper) || "BLOCK".equals(upper) || "VIOLATION".equals(upper)) {
            return "REJECT";
        }
        return "NEED_MANUAL";
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        byte[] keyBytes = readPemBytes(requirePath(path, "SIGNER_PRIVATE_KEY_PATH_EMPTY"),
                "PRIVATE KEY");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private PublicKey loadPublicKey(String path) throws Exception {
        byte[] keyBytes = readPemBytes(requirePath(path, "VERIFIER_PUBLIC_KEY_PATH_EMPTY"),
                "PUBLIC KEY");
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private PrivateKey loadPrivateKeyFromDatabase() throws Exception {
        SecurePublishKey row = loadActiveDatabaseKey(true);
        byte[] keyBytes = pemBytes(row.getPrivateKeyPem(), "PRIVATE KEY");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private PublicKey loadPublicKeyFromDatabase() throws Exception {
        SecurePublishKey row = loadActiveDatabaseKey(false);
        byte[] keyBytes = pemBytes(row.getPublicKeyPem(), "PUBLIC KEY");
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private SecurePublishKey loadActiveDatabaseKey(boolean requirePrivateKey) {
        String keyId = configuredSignatureKeyId();
        SecurePublishKey row = findActiveDatabaseKey(keyId);
        if (row == null) {
            throw new IllegalStateException("DB_KEY_NOT_FOUND: " + keyId);
        }
        if (requirePrivateKey && trimToNull(row.getPrivateKeyPem()) == null) {
            throw new IllegalStateException("DB_PRIVATE_KEY_EMPTY: " + keyId);
        }
        if (trimToNull(row.getPublicKeyPem()) == null) {
            throw new IllegalStateException("DB_PUBLIC_KEY_EMPTY: " + keyId);
        }
        return row;
    }

    private SecurePublishKey findActiveDatabaseKey(String keyId) {
        LambdaQueryWrapper<SecurePublishKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SecurePublishKey::getKeyId, keyId)
                .eq(SecurePublishKey::getKeyRole, DB_KEY_ROLE_SIGNER)
                .eq(SecurePublishKey::getStatus, DB_KEY_STATUS_ACTIVE)
                .orderByDesc(SecurePublishKey::getId)
                .last("LIMIT 1");
        return securePublishKeyMapper.selectOne(wrapper);
    }

    private byte[] readPemBytes(String path, String type) throws Exception {
        byte[] raw = Files.readAllBytes(new File(path).toPath());
        String text = new String(raw, StandardCharsets.US_ASCII).trim();
        return pemBytes(text, type);
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
            return Base64.getDecoder().decode(value);
        }
        return Base64.getDecoder().decode(value.replaceAll("\\s", ""));
    }

    private void writePem(File file, String type, byte[] encoded) throws Exception {
        createParentDirectories(file);
        Files.write(file.toPath(), toPem(type, encoded).getBytes(StandardCharsets.US_ASCII));
    }

    private String toPem(String type, byte[] encoded) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n"
                + base64
                + "\n-----END " + type + "-----\n";
    }

    private void createParentDirectories(File file) throws Exception {
        if (file != null && file.getParentFile() != null) {
            Files.createDirectories(file.getParentFile().toPath());
        }
    }

    private String requirePath(String path, String error) {
        String value = trimToNull(path);
        if (value == null) {
            throw new IllegalArgumentException(error);
        }
        return value;
    }

    private String signatureProvider() {
        String provider = trimToNull(properties.getSignatureProvider());
        return provider == null ? SIGNATURE_PROVIDER_RSA : provider.toLowerCase(Locale.ROOT);
    }

    private boolean isRsaSignatureProvider() {
        String provider = signatureProvider();
        return SIGNATURE_PROVIDER_RSA.equals(provider) || SIGNATURE_PROVIDER_RSA_DB.equals(provider);
    }

    private String configuredSignatureKeyId() {
        String keyId = trimToNull(properties.getSignatureKeyId());
        return keyId == null ? "default" : keyId;
    }

    private String publicKeyFingerprint(String publicKeyPem) {
        try {
            return sha256Hex(pemBytes(publicKeyPem, "PUBLIC KEY"));
        } catch (Exception e) {
            return null;
        }
    }

    private String extensionOf(String fileName) {
        String name = trimToNull(fileName);
        if (name == null) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFileName(String fileName) {
        String name = new File(fileName == null ? "" : fileName).getName();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException("PAYLOAD_FILENAME_INVALID");
        }
        return name;
    }

    private void movePackage(File source, File targetDir) throws Exception {
        Files.createDirectories(targetDir.toPath());
        File target = uniqueFile(targetDir, source.getName());
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void movePackageQuietly(File source, File targetDir) {
        try {
            movePackage(source, targetDir);
        } catch (Exception e) {
            log.warn("[SecurePublish] move rejected package failed: {}", e.getMessage());
        }
    }

    private File uniqueFile(File dir, String fileName) {
        File target = new File(dir, sanitizeFileName(fileName));
        if (!target.exists()) {
            return target;
        }
        String name = target.getName();
        String base = stripExtension(name);
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            ext = name.substring(dot);
        }
        return new File(dir, base + "-" + System.currentTimeMillis() + ext);
    }

    private String stripExtension(String name) {
        String safeName = sanitizeFileName(name);
        int dot = safeName.lastIndexOf('.');
        return dot <= 0 ? safeName : safeName.substring(0, dot);
    }

    private void ensureDirectories() throws Exception {
        for (String dir : Arrays.asList(
                properties.getIncomingDir(),
                properties.getVerifiedDir(),
                properties.getRuntimeDir(),
                properties.getRejectedDir(),
                properties.getTempDir())) {
            String value = trimToNull(dir);
            if (value != null) {
                Files.createDirectories(new File(value).toPath());
            }
        }
    }

    private void ensureSignerDirectories() throws Exception {
        for (String dir : Arrays.asList(
                properties.getSignerInputDir(),
                properties.getSignerOutputDir(),
                properties.getSignerArchiveDir(),
                properties.getSignerRejectedDir())) {
            String value = trimToNull(dir);
            if (value != null) {
                Files.createDirectories(new File(value).toPath());
            }
        }
    }

    private void deleteDirectoryQuietly(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectoryQuietly(file);
                } else if (!file.delete()) {
                    log.debug("[SecurePublish] temp upload cleanup skipped: {}", file.getAbsolutePath());
                }
            }
        }
        if (!dir.delete()) {
            log.debug("[SecurePublish] temp upload dir cleanup skipped: {}", dir.getAbsolutePath());
        }
    }

    private String configuredPublisher() {
        String publisher = trimToNull(properties.getPublisher());
        return publisher == null ? "secure-publish-signer" : publisher;
    }

    private void saveItem(SecurePublishItem item) {
        if (item == null || trimToNull(item.getPackageName()) == null) {
            return;
        }
        String key = item.getPackageName() + "|" + item.getUpdatedAt();
        items.put(key, item);
        itemOrder.addFirst(key);
        while (itemOrder.size() > 100) {
            String old = itemOrder.pollLast();
            if (old != null) {
                items.remove(old);
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(second);
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private void reportClientContentSigned(String fileName, SecurePublishManifest manifest,
                                            String publisher, String errorMessage) {
        if (diagnosticLogReporter == null) {
            return;
        }
        try {
            DiagnosticLogReport report = new DiagnosticLogReport();
            report.setEventType("CLIENT_CONTENT_SIGNED");
            report.setEventLevel(errorMessage != null ? "error" : "info");
            report.setResultStatus(errorMessage != null ? "fail" : "success");
            report.setOperatorId(publisher);
            report.setOperatorName(publisher);
            report.setSummary(errorMessage != null ? "客户端签名失败" : "客户端签名成功");
            report.setErrorMessage(errorMessage);

            boolean success = errorMessage == null;
            String fileHash = manifest != null ? manifest.getSha256() : null;
            String signatureProvider = signatureProvider();
            String keyId = configuredSignatureKeyId();
            String auditDecision = manifest != null ? manifest.getScanResult() : null;

            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("packageName", fileName);
            if (manifest != null) {
                detail.put("fileName", manifest.getFileName());
            }
            detail.put("fileHash", defaultIfBlank(fileHash, ""));
            if (manifest != null && manifest.getFileSize() != null) {
                detail.put("fileSize", String.valueOf(manifest.getFileSize()));
            }
            detail.put("signatureProvider", defaultIfBlank(signatureProvider, ""));
            detail.put("keyId", defaultIfBlank(keyId, ""));
            detail.put("auditDecision", defaultIfBlank(auditDecision, ""));
            if (!success) {
                detail.put("error", defaultIfBlank(errorMessage, "unknown"));
            }
            report.setDetailJson(JSON.toJSONString(detail));
            report.setDedupKey(fileHash != null ? "signed:" + fileHash : "signed:" + fileName);
            report.setRefId(fileHash);
            report.setRefTable("secure_publish");

            diagnosticLogReporter.reportAsync(report);
        } catch (Exception e) {
            log.debug("[SecurePublish] report client content signed failed: {}", e.getMessage());
        }
    }

    private void reportClientPackageVerified(String packageName, boolean success, String fileHash,
                                              String payloadName, String status, String reason,
                                              String errorMessage) {
        if (diagnosticLogReporter == null) {
            return;
        }
        try {
            DiagnosticLogReport report = new DiagnosticLogReport();
            report.setEventType("CLIENT_PACKAGE_VERIFIED");
            report.setEventLevel(success ? "info" : "warn");
            report.setResultStatus(success ? "success" : "fail");
            report.setOperatorName("secure-publish-verifier");
            report.setSummary(success ? "客户端验签通过" : "客户端验签失败");
            report.setErrorMessage(errorMessage);
            report.setContentId(fileHash);

            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("packageName", defaultIfBlank(packageName, ""));
            detail.put("payloadName", defaultIfBlank(payloadName, ""));
            detail.put("fileHash", defaultIfBlank(fileHash, ""));
            detail.put("status", defaultIfBlank(status, ""));
            detail.put("reason", defaultIfBlank(reason, ""));
            if (!success) {
                detail.put("error", defaultIfBlank(errorMessage, "unknown"));
            }
            report.setDetailJson(JSON.toJSONString(detail));
            report.setDedupKey(fileHash != null ? "verified:" + fileHash : "verified:" + packageName);
            report.setRefId(fileHash);
            report.setRefTable("secure_publish");

            diagnosticLogReporter.reportAsync(report);
        } catch (Exception e) {
            log.debug("[SecurePublish] report client package verified failed: {}", e.getMessage());
        }
    }

    private String safeString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = trimToNull(String.valueOf(value));
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class SignerAuditResult {
        private final String decision;
        private final String riskLevel;
        private final String message;
        private final String reason;
        private final String recordId;

        private SignerAuditResult(String decision, String riskLevel, String message, String reason, String recordId) {
            this.decision = decision;
            this.riskLevel = riskLevel;
            this.message = message;
            this.reason = reason;
            this.recordId = recordId;
        }

        private static SignerAuditResult pass(String riskLevel, String message, String reason, String recordId) {
            return new SignerAuditResult("PASS", riskLevel, message, reason, recordId);
        }

        private static SignerAuditResult reject(String riskLevel, String message, String reason, String recordId) {
            return new SignerAuditResult("REJECT", riskLevel, message, reason, recordId);
        }
    }
}

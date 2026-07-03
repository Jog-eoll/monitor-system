package com.infopublish.client.service.impl;

import com.infopublish.client.service.CertificateFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * 本地证书文件读写服务，支持明文 .cer 和 AES-GCM 加密后的 .cer.enc。
 */
@Slf4j
@Service
public class CertificateFileServiceImpl implements CertificateFileService {

    private static final String ENCRYPTED_SUFFIX = ".enc";
    private static final String FORMAT_KEY = "format";
    private static final String FORMAT_VALUE = "IPC_CERT_ENC_V1";
    private static final String ALGORITHM_KEY = "algorithm";
    private static final String ALGORITHM_VALUE = "AES/GCM/NoPadding";
    private static final String IV_KEY = "iv";
    private static final String DATA_KEY = "data";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${vauth.cert-crypto.enabled:false}")
    private boolean encryptionEnabled;

    @Value("${vauth.cert-crypto.key-env:VAUTH_CERT_AES_KEY}")
    private String keyEnvName;

    /**
     * 读取证书内容，返回 SDK 和管控平台可直接使用的 PEM 字符串。
     */
    public String loadCertificateContent(String configuredPath) {
        if (isBlank(configuredPath)) {
            throw new IllegalArgumentException("certificate path is required");
        }

        Path path = resolvePath(configuredPath);
        byte[] storedBytes;
        try {
            storedBytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("read certificate file failed: " + path.toAbsolutePath(), e);
        }

        byte[] plainBytes = isEncryptedFile(configuredPath, storedBytes)
                ? decrypt(storedBytes, path)
                : storedBytes;

        String pem = toPemCertificate(plainBytes);
        log.debug("certificate loaded: {}", path.toAbsolutePath());
        return pem;
    }

    /**
     * 保存本地证书。启用加密时写入 .cer.enc，否则保持原 .cer 文件。
     */
    public Path saveLocalCertificate(Path plainTargetPath, byte[] certificateBytes) {
        if (plainTargetPath == null) {
            throw new IllegalArgumentException("certificate target path is required");
        }
        if (certificateBytes == null || certificateBytes.length == 0) {
            throw new IllegalArgumentException("certificate content is empty");
        }

        Path targetPath = encryptionEnabled ? toEncryptedPath(plainTargetPath) : plainTargetPath;
        byte[] storedBytes = encryptionEnabled ? encrypt(certificateBytes) : certificateBytes;

        try {
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(targetPath, storedBytes);
            log.info("certificate saved: path={}, encrypted={}", targetPath.toAbsolutePath(), encryptionEnabled);
            return targetPath;
        } catch (IOException e) {
            throw new IllegalStateException("save certificate file failed: " + targetPath.toAbsolutePath(), e);
        }
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    private byte[] encrypt(byte[] plainBytes) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM_VALUE);
            cipher.init(Cipher.ENCRYPT_MODE, loadAesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encryptedBytes = cipher.doFinal(plainBytes);

            StringBuilder builder = new StringBuilder();
            builder.append(FORMAT_KEY).append('=').append(FORMAT_VALUE).append('\n');
            builder.append(ALGORITHM_KEY).append('=').append(ALGORITHM_VALUE).append('\n');
            builder.append(IV_KEY).append('=').append(Base64.getEncoder().encodeToString(iv)).append('\n');
            builder.append(DATA_KEY).append('=').append(Base64.getEncoder().encodeToString(encryptedBytes)).append('\n');
            return builder.toString().getBytes(StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encrypt certificate file failed", e);
        }
    }

    private byte[] decrypt(byte[] storedBytes, Path sourcePath) {
        Properties properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(storedBytes));
        } catch (IOException e) {
            throw new IllegalStateException("read encrypted certificate metadata failed: " + sourcePath.toAbsolutePath(), e);
        }

        String format = properties.getProperty(FORMAT_KEY);
        String algorithm = properties.getProperty(ALGORITHM_KEY);
        String ivText = properties.getProperty(IV_KEY);
        String dataText = properties.getProperty(DATA_KEY);

        if (!FORMAT_VALUE.equals(format) || !ALGORITHM_VALUE.equals(algorithm)
                || isBlank(ivText) || isBlank(dataText)) {
            throw new IllegalStateException("unsupported encrypted certificate format: " + sourcePath.toAbsolutePath());
        }

        try {
            byte[] iv = Base64.getDecoder().decode(ivText.trim());
            byte[] encryptedBytes = Base64.getDecoder().decode(dataText.trim());

            Cipher cipher = Cipher.getInstance(ALGORITHM_VALUE);
            cipher.init(Cipher.DECRYPT_MODE, loadAesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(encryptedBytes);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("decrypt certificate file failed: " + sourcePath.toAbsolutePath(), e);
        }
    }

    private SecretKeySpec loadAesKey() {
        String envName = isBlank(keyEnvName) ? "VAUTH_CERT_AES_KEY" : keyEnvName.trim();
        String keyText = System.getenv(envName);
        if (isBlank(keyText)) {
            throw new IllegalStateException("certificate crypto key env is missing: " + envName);
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyText.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("certificate crypto key must be Base64: " + envName, e);
        }

        int keyLength = keyBytes.length;
        if (keyLength != 16 && keyLength != 24 && keyLength != 32) {
            throw new IllegalStateException("certificate crypto key length must be 16, 24 or 32 bytes: " + envName);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private boolean isEncryptedFile(String configuredPath, byte[] storedBytes) {
        if (configuredPath != null && configuredPath.toLowerCase().endsWith(ENCRYPTED_SUFFIX)) {
            return true;
        }
        String prefix = new String(storedBytes, 0, Math.min(storedBytes.length, 64), StandardCharsets.US_ASCII);
        return prefix.contains(FORMAT_KEY + "=" + FORMAT_VALUE);
    }

    private String toPemCertificate(byte[] certificateBytes) {
        String text = new String(certificateBytes, StandardCharsets.UTF_8).trim();
        if (text.contains("-----BEGIN CERTIFICATE-----")) {
            return text;
        }

        String compactText = text.replaceAll("\\s+", "");
        if (compactText.length() > 128 && compactText.matches("[A-Za-z0-9+/=]+")) {
            return wrapPemBody(compactText);
        }

        return wrapPemBody(Base64.getEncoder().encodeToString(certificateBytes));
    }

    private String wrapPemBody(String base64Body) {
        StringBuilder builder = new StringBuilder();
        builder.append("-----BEGIN CERTIFICATE-----\n");
        int index = 0;
        while (index < base64Body.length()) {
            int end = Math.min(index + 64, base64Body.length());
            builder.append(base64Body, index, end).append('\n');
            index = end;
        }
        builder.append("-----END CERTIFICATE-----");
        return builder.toString();
    }

    private Path toEncryptedPath(Path plainTargetPath) {
        String fileName = plainTargetPath.getFileName().toString();
        if (fileName.toLowerCase().endsWith(ENCRYPTED_SUFFIX)) {
            return plainTargetPath;
        }
        return plainTargetPath.resolveSibling(fileName + ENCRYPTED_SUFFIX);
    }

    private Path resolvePath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        return Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

package com.infopublish.client.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 离线证书加密工具，用于把现有 .cer 转为运行时可读取的 .cer.enc。
 */
public class CertificateEncryptTool {

    private static final String FORMAT_VALUE = "IPC_CERT_ENC_V1";
    private static final String ALGORITHM_VALUE = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 1) {
            System.err.println("Usage: CertificateEncryptTool <source.cer> [target.cer.enc] [keyEnvName]");
            System.exit(1);
            return;
        }

        Path sourcePath = Paths.get(args[0]).toAbsolutePath().normalize();
        Path targetPath = args.length >= 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : Paths.get(args[0] + ".enc").toAbsolutePath().normalize();
        String keyEnvName = args.length >= 3 ? args[2] : "VAUTH_CERT_AES_KEY";

        byte[] plainBytes = Files.readAllBytes(sourcePath);
        byte[] encryptedBytes = encrypt(plainBytes, keyEnvName);

        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(targetPath, encryptedBytes);
        System.out.println("encrypted certificate saved: " + targetPath);
    }

    private static byte[] encrypt(byte[] plainBytes, String keyEnvName) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM_VALUE);
        cipher.init(Cipher.ENCRYPT_MODE, loadAesKey(keyEnvName), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] encryptedBytes = cipher.doFinal(plainBytes);

        StringBuilder builder = new StringBuilder();
        builder.append("format=").append(FORMAT_VALUE).append('\n');
        builder.append("algorithm=").append(ALGORITHM_VALUE).append('\n');
        builder.append("iv=").append(Base64.getEncoder().encodeToString(iv)).append('\n');
        builder.append("data=").append(Base64.getEncoder().encodeToString(encryptedBytes)).append('\n');
        return builder.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static SecretKeySpec loadAesKey(String keyEnvName) {
        String envName = keyEnvName == null || keyEnvName.trim().isEmpty()
                ? "VAUTH_CERT_AES_KEY"
                : keyEnvName.trim();
        String keyText = System.getenv(envName);
        if (keyText == null || keyText.trim().isEmpty()) {
            throw new IllegalStateException("certificate crypto key env is missing: " + envName);
        }

        byte[] keyBytes = Base64.getDecoder().decode(keyText.trim());
        int keyLength = keyBytes.length;
        if (keyLength != 16 && keyLength != 24 && keyLength != 32) {
            throw new IllegalStateException("certificate crypto key length must be 16, 24 or 32 bytes: " + envName);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}

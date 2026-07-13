package com.infopublish.client.service.impl;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PublishTempFileService {

    @Value("${content-publish.v2.temp-storage-root:${java.io.tmpdir}/info-publish-client/publish-v2}")
    private String storageRoot;

    private final ConcurrentHashMap<String, StoredFile> files = new ConcurrentHashMap<>();

    public StoredFile store(String requestId, MultipartFile file, long maxBytes, long ttlMs) throws IOException {
        cleanupExpired();
        if (file == null || file.isEmpty()) {
            throw new IOException("上传文件为空");
        }
        long size = file.getSize();
        if (maxBytes > 0 && size > maxBytes) {
            throw new IOException("上传文件超过大小限制: " + file.getOriginalFilename());
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        String safeName = sanitizeFileName(file.getOriginalFilename());
        Path root = Paths.get(storageRoot);
        Path dir = root.resolve(sanitizeSegment(requestId));
        Files.createDirectories(dir);
        Path path = dir.resolve(token + "-" + safeName);

        MessageDigest digest = sha256();
        long written = 0L;
        try (InputStream input = file.getInputStream();
             OutputStream output = Files.newOutputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                written += read;
                if (maxBytes > 0 && written > maxBytes) {
                    throw new IOException("上传文件超过大小限制: " + file.getOriginalFilename());
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch (IOException e) {
            deleteQuietly(path);
            throw e;
        }

        StoredFile stored = new StoredFile();
        stored.setToken(token);
        stored.setOriginalFilename(safeName);
        stored.setContentType(file.getContentType());
        stored.setPath(path);
        stored.setSize(written);
        stored.setSha256(hex(digest.digest()));
        stored.setExpiresAt(System.currentTimeMillis() + Math.max(60000L, ttlMs));
        files.put(token, stored);
        return stored;
    }

    public StoredFile get(String token) {
        cleanupExpired();
        if (token == null) {
            return null;
        }
        StoredFile stored = files.get(token);
        if (stored == null) {
            return null;
        }
        if (stored.getExpiresAt() < System.currentTimeMillis()) {
            files.remove(token);
            deleteQuietly(stored.getPath());
            return null;
        }
        return stored;
    }

    public void remove(String token) {
        if (token == null) {
            return;
        }
        StoredFile stored = files.remove(token);
        if (stored != null) {
            deleteQuietly(stored.getPath());
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, StoredFile>> iterator = files.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, StoredFile> entry = iterator.next();
            StoredFile stored = entry.getValue();
            if (stored != null && stored.getExpiresAt() < now) {
                iterator.remove();
                deleteQuietly(stored.getPath());
            }
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String sanitizeSegment(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String sanitizeFileName(String value) {
        String name = value == null || value.trim().isEmpty() ? "upload.bin" : value.trim();
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return name.isEmpty() ? "upload.bin" : name;
    }

    @Data
    public static class StoredFile {
        private String token;
        private String originalFilename;
        private String contentType;
        private Path path;
        private long size;
        private String sha256;
        private long expiresAt;
    }
}

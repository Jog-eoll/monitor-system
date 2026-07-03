package com.publishgateway.udpproxy.service;

import io.minio.*;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MinIO 上传服务
 * 
 * 负责将图片二进制数据直接上传到 MinIO 对象存储。
 * 支持配置开关和失败降级机制，确保系统稳定运行。
 */
@Slf4j
@Service
public class MinioUploadService {

    @Value("${minio.enabled:false}")
    private boolean minioEnabled;

    @Value("${minio.endpoint:}")
    private String endpoint;

    @Value("${minio.access-key:}")
    private String accessKey;

    @Value("${minio.secret-key:}")
    private String secretKey;

    @Value("${minio.bucket-name:monitor-content}")
    private String bucketName;

    @Value("${minio.fallback-to-base64:true}")
    private boolean fallbackToBase64;

    private MinioClient minioClient;
    private boolean initialized = false;

    /**
     * 初始化 MinIO 客户端
     */
    @PostConstruct
    public void init() {
        if (!minioEnabled) {
            log.info("【MinIO】上传功能已禁用，将使用 Base64 方式");
            return;
        }

        if (endpoint == null || endpoint.isEmpty()) {
            log.warn("【MinIO】endpoint 未配置，上传功能禁用");
            return;
        }

        try {
            minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            // 确保桶存在并设置公开策略
            ensureBucketExists();
            initialized = true;
            log.info("【MinIO】初始化成功: endpoint={}, bucket={}", endpoint, bucketName);
        } catch (Exception e) {
            log.error("【MinIO】初始化失败: {}", e.getMessage(), e);
            if (!fallbackToBase64) {
                throw new RuntimeException("MinIO 初始化失败且不允许降级", e);
            }
        }
    }

    /**
     * 上传图片到 MinIO
     *
     * @param data     图片二进制数据
     * @param fileName 原始文件名（可为 null）
     * @param format   图片格式（如 JPEG、PNG）
     * @return MinIO 对象路径，失败且允许降级时返回 null
     * @throws RuntimeException 失败且不允许降级时抛出异常
     */
    public String uploadImage(byte[] data, String fileName, String format) {
        // 未启用或未初始化，直接返回 null（走 Base64）
        if (!minioEnabled || !initialized || minioClient == null) {
            return null;
        }

        if (data == null || data.length == 0) {
            log.warn("【MinIO】上传数据为空，跳过");
            return null;
        }

        try {
            // 生成对象名称：images/2026/03/20/uuid.jpg
            String objectName = generateObjectName(fileName, format);
            String contentType = determineContentType(format);

            // 上传到 MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType(contentType)
                            .build()
            );

            log.info("【MinIO】上传成功: objectName={}, size={}KB, contentType={}",
                    objectName, data.length / 1024, contentType);
            return objectName;

        } catch (Exception e) {
            log.warn("【MinIO】上传失败: {}", e.getMessage());
            if (fallbackToBase64) {
                log.info("【MinIO】降级为 Base64 方式");
                return null;
            }
            throw new RuntimeException("MinIO 上传失败", e);
        }
    }
    /**
     * 上传视频到 MinIO
     *
     * @param data     视频二进制数据
     * @param fileName 原始文件名（用于确定扩展名）
     * @return MinIO 对象路径，失败时返回 null
     */

    public String uploadVideo(byte[] data,String fileName){
        // 未启用或未初始化，直接返回 null（走 Base64）
        if (!minioEnabled || !initialized || minioClient == null) {
            return null;
        }

        if (data == null || data.length == 0) {
            log.warn("【MinIO】上传数据为空，跳过");
            return null;
        }

        try {
            String objectName = generateVideoObjectName(fileName);
            String contentType = determineVideoContentType(fileName);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType(contentType)
                            .build()
            );

            log.info("【MinIO】视频上传成功: objectName={}, size={}MB, contentType={}",
                    objectName, data.length / (1024 * 1024), contentType);
            return objectName;

        } catch (Exception e) {
            log.warn("【MinIO】视频上传失败: {}", e.getMessage());
            return null;
        }
    }










    /**
     * 确保存储桶存在，并设置为公开只读
     */
    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );

        if (!exists) {
            log.info("【MinIO】创建存储桶: {}", bucketName);
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );
        }

        // 设置公开只读策略
        String policy = "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [\n" +
                "    {\n" +
                "      \"Effect\": \"Allow\",\n" +
                "      \"Principal\": {\"AWS\": [\"*\"]},\n" +
                "      \"Action\": [\"s3:GetObject\"],\n" +
                "      \"Resource\": [\"arn:aws:s3:::" + bucketName + "/*\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                        .bucket(bucketName)
                        .config(policy)
                        .build()
        );
        log.info("【MinIO】存储桶 {} 已设置为公开只读", bucketName);
    }

    /**
     * 生成图片对象名称
     * 格式：images/2026/03/20/uuid.jpg
     */
    private String generateObjectName(String fileName, String format) {
        LocalDate today = LocalDate.now();
        String datePath = today.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String extension = determineExtension(fileName, format);
        return String.format("images/%s/%s.%s", datePath, uuid, extension);
    }

    /**
     * 确定文件扩展名
     */
    private String determineExtension(String fileName, String format) {
        // 优先从文件名提取
        if (fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            if (!ext.isEmpty()) {
                return ext;
            }
        }
        // 从格式推断
        if (format != null) {
            switch (format.toUpperCase()) {
                case "JPEG":
                    return "jpg";
                case "PNG":
                    return "png";
                case "GIF":
                    return "gif";
                case "BMP":
                    return "bmp";
                case "PMG":
                    return "pmg";
                default:
                    return format.toLowerCase();
            }
        }
        return "bin";
    }

    /**
     * 确定内容类型
     */
    private String determineContentType(String format) {
        if (format == null) {
            return "application/octet-stream";
        }
        switch (format.toUpperCase()) {
            case "JPEG":
            case "JPG":
                return "image/jpeg";
            case "PNG":
                return "image/png";
            case "GIF":
                return "image/gif";
            case "BMP":
                return "image/bmp";
            default:
                return "application/octet-stream";
        }
    }


    /**
     * 生成视频对象名称
     * 格式：videos/2026/04/07/uuid.mp4
     */
    private String generateVideoObjectName(String fileName) {
        LocalDate today = LocalDate.now();
        String datePath = today.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String extension = "mp4"; // 默认
        if (fileName != null && fileName.contains(".")) {
            extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return String.format("videos/%s/%s.%s", datePath, uuid, extension);
    }

    /**
     * 确定视频 MIME 类型
     */
    private String determineVideoContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        switch (ext) {
            case "mp4":  return "video/mp4";
            case "avi":  return "video/x-msvideo";
            case "mov":  return "video/quicktime";
            case "wmv":  return "video/x-ms-wmv";
            case "mkv":  return "video/x-matroska";
            case "flv":  return "video/x-flv";
            case "ts":   return "video/mp2t";
            default:     return "application/octet-stream";
        }
    }

    /**
     * 获取 MinIO 访问 URL
     */
    public String getFileUrl(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return null;
        }
        return String.format("%s/%s/%s", endpoint, bucketName, objectName);
    }

    /**
     * 检查 MinIO 是否可用
     */
    public boolean isAvailable() {
        return minioEnabled && initialized && minioClient != null;
    }
}

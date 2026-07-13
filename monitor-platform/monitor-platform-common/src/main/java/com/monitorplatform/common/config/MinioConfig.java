package com.monitorplatform.common.config;

import io.minio.MinioClient;
import lombok.Data;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * MinIO 配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /**
     * MinIO 服务端点
     */
    private String endpoint = "";

    /**
     * 访问密钥（必须从环境变量或配置文件读取，禁止硬编码）
     */
    private String accessKey;

    /**
     * 秘密密钥（必须从环境变量或配置文件读取，禁止硬编码）
     */
    private String secretKey;

    /**
     * 默认存储桶名称
     */
    private String bucketName = "monitor-content";

    /**
     * 创建 MinioClient 客户端
     */
    @Bean
    public MinioClient minioClient() {
        // 安全校验：密钥必须通过环境变量或配置中心注入，禁止使用空值
        if (accessKey == null || accessKey.isEmpty()) {
            throw new IllegalArgumentException("MinIO accessKey 未配置，请通过环境变量 MINIO_ACCESS_KEY 或配置文件设置");
        }
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalArgumentException("MinIO secretKey 未配置，请通过环境变量 MINIO_SECRET_KEY 或配置文件设置");
        }
        OkHttpClient httpClient = new OkHttpClient.Builder()
                // 连接超时 30 秒
                .connectTimeout(30, TimeUnit.SECONDS)
                // 读取超时 5 分钟（大文件上传需要）
                .readTimeout(5, TimeUnit.MINUTES)
                // 写入超时 5 分钟（大文件上传需要）
                .writeTimeout(5, TimeUnit.MINUTES)
                // 连接池：最多 5 个空闲连接，存活 5 分钟
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                // 失败后不重试（由我们自己控制重试逻辑）
                .retryOnConnectionFailure(false)
                .build();

        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
    }

    /**
     * 获取文件访问 URL
     */
    public String getFileUrl(String objectName) {
        return String.format("%s/%s/%s", endpoint, bucketName, objectName);
    }
}

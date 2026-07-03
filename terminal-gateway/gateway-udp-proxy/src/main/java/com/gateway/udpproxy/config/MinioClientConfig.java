package com.gateway.udpproxy.config;

import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * MinIO 客户端配置
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioClientConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        MinioClient.Builder builder = MinioClient.builder().endpoint(endpoint);
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            builder.credentials(accessKey, secretKey);
        } else {
            log.warn("[snapshot] minio accessKey/secretKey is empty, use anonymous client");
        }
        return builder.build();
    }
}

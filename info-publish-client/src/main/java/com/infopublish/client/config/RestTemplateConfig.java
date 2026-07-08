package com.infopublish.client.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置
 * 用于HTTP客户端请求（调用管控平台接口）
 */
@Configuration
public class RestTemplateConfig {

    @Value("${http-client.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${http-client.read-timeout-ms:120000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 连接超时：默认 10 秒，可通过 http-client.connect-timeout-ms 覆盖
        factory.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        
        // 读取超时：默认 120 秒，可通过 http-client.read-timeout-ms 覆盖
        factory.setReadTimeout(Math.max(1000, readTimeoutMs));
        
        return new RestTemplate(factory);
    }
}

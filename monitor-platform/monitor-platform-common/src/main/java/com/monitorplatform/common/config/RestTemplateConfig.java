package com.monitorplatform.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类
 * 用于 HTTP 请求，如管控平台与加密网关的通信
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建 RestTemplate 实例
     * 配置连接超时和读取超时
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 设置连接超时时间（毫秒）
        factory.setConnectTimeout(5000);
        // 设置读取超时时间（毫秒）
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}

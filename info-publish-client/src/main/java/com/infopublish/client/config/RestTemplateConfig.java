package com.infopublish.client.config;

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

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 连接超时：5秒
        factory.setConnectTimeout(5000);
        
        // 读取超时：10秒
        factory.setReadTimeout(10000);
        
        return new RestTemplate(factory);
    }
}

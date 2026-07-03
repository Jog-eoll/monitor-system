package com.monitorplatform.provider.config;

import com.monitorplatform.provider.service.JmDNSServiceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JmDNSConfig.class)
public class JmDNSProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JmDNSServiceProvider jmdnsServiceProvider() {
        return new JmDNSServiceProvider();
    }
}

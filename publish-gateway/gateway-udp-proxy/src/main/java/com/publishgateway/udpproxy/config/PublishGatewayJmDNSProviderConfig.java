package com.publishgateway.udpproxy.config;

import com.monitorplatform.provider.config.JmDNSConfig;
import com.monitorplatform.provider.service.JmDNSServiceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges monitor-platform-provider beans into the gateway scan path.
 */
@Configuration
@EnableConfigurationProperties(JmDNSConfig.class)
public class PublishGatewayJmDNSProviderConfig {

    @Bean
    @ConditionalOnMissingBean
    public JmDNSServiceProvider jmdnsServiceProvider() {
        return new JmDNSServiceProvider();
    }
}

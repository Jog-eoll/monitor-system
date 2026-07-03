package com.monitorplatform.registry.client.config;

import com.monitorplatform.registry.client.ServiceRegistryClient;
import com.monitorplatform.registry.client.component.ServiceAutoRegister;
import com.monitorplatform.registry.client.impl.HttpServiceRegistryClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(prefix = "registry.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RegistryClientProperties.class)
public class RegistryClientAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ServiceRegistryClient serviceRegistryClient(RegistryClientProperties properties) {
        return new HttpServiceRegistryClient(
                properties.getServerAddr(),
                properties.getApiPrefix(),
                properties.getRegisterPath()
        );
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ServiceAutoRegister serviceAutoRegister(RegistryClientProperties properties,
                                                   ServiceRegistryClient serviceRegistryClient,
                                                   Environment environment) {
        return new ServiceAutoRegister(properties, serviceRegistryClient, environment);
    }
}

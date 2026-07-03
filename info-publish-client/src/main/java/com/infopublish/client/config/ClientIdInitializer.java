package com.infopublish.client.config;

import com.infopublish.client.service.MonitorPlatformClient;
import com.monitorplatform.registry.client.config.RegistryClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class ClientIdInitializer implements ApplicationRunner, Ordered {

    @Resource
    private MonitorPlatformClient monitorPlatformClient;

    @Resource
    private RegistryClientProperties registryClientProperties;

    @Resource
    private Environment environment;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments args) {
        String clientId = ClientIdResolver.resolve(environment);
        monitorPlatformClient.setClientId(clientId);
        registryClientProperties.setClientId(clientId);
        log.info("[ClientId] current clientId={}", clientId);
    }
}

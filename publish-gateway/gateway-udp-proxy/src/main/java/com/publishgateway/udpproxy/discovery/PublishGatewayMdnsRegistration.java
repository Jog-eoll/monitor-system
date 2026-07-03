package com.publishgateway.udpproxy.discovery;

import com.monitorplatform.provider.service.JmDNSServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class PublishGatewayMdnsRegistration {

    private static final String DEFAULT_SERVICE_NAME = "gateway-udp-proxy";
    private static final int DEFAULT_SERVER_PORT = 8092;

    private final JmDNSServiceProvider jmdnsServiceProvider;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    @Value("${provider.jmdns.enabled:true}")
    private boolean mdnsEnabled;

    @Value("${registry.client.service-name:${spring.application.name:gateway-udp-proxy}}")
    private String serviceName;

    @Value("${server.port:8092}")
    private int serverPort;

    @Value("${registry.client.device-type:publish_gateway}")
    private String deviceType;

    @Value("${registry.client.version:}")
    private String version;

    @Value("${registry.client.manufacturer:}")
    private String manufacturer;

    @Value("${registry.client.model:}")
    private String model;

    @Value("${registry.client.remark:}")
    private String remark;

    public PublishGatewayMdnsRegistration(JmDNSServiceProvider jmdnsServiceProvider) {
        this.jmdnsServiceProvider = jmdnsServiceProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnReady() {
        if (!mdnsEnabled) {
            log.info("[PublishGateway-mDNS] mDNS registration disabled: provider.jmdns.enabled=false");
            return;
        }
        if (!registered.compareAndSet(false, true)) {
            return;
        }

        String resolvedServiceName = StringUtils.hasText(serviceName) ? serviceName.trim() : DEFAULT_SERVICE_NAME;
        int resolvedPort = serverPort > 0 ? serverPort : DEFAULT_SERVER_PORT;
        Map<String, String> props = buildPublicProperties();

        boolean success = jmdnsServiceProvider.register(resolvedServiceName, resolvedPort, props);
        if (success) {
            log.info("[PublishGateway-mDNS] registered: name={}, port={}, props={}",
                    resolvedServiceName, resolvedPort, props);
        } else {
            registered.set(false);
            log.warn("[PublishGateway-mDNS] registration failed: name={}, port={}",
                    resolvedServiceName, resolvedPort);
        }
    }

    private Map<String, String> buildPublicProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("deviceType", StringUtils.hasText(deviceType) ? deviceType.trim() : "publish_gateway");
        props.put("role", "encrypt_gateway");
        props.put("path", "/actuator/health");
        putIfText(props, "version", version);
        putIfText(props, "manufacturer", manufacturer);
        putIfText(props, "model", model);
        putIfText(props, "remark", remark);
        return props;
    }

    private void putIfText(Map<String, String> props, String key, String value) {
        if (StringUtils.hasText(value)) {
            props.put(key, value.trim());
        }
    }
}

package com.infopublish.client.discovery;

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
public class InfoPublishClientMdnsRegistration {

    private static final String DEFAULT_SERVICE_NAME = "info-publish-client";
    private static final int DEFAULT_SERVER_PORT = 7080;

    private final JmDNSServiceProvider jmdnsServiceProvider;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    @Value("${provider.jmdns.enabled:true}")
    private boolean mdnsEnabled;

    @Value("${registry.client.service-name:${spring.application.name:info-publish-client}}")
    private String serviceName;

    @Value("${server.port:7080}")
    private int serverPort;

    @Value("${registry.client.device-type:publish_server}")
    private String deviceType;

    @Value("${registry.client.version:}")
    private String version;

    @Value("${registry.client.manufacturer:}")
    private String manufacturer;

    @Value("${registry.client.model:}")
    private String model;

    @Value("${registry.client.remark:}")
    private String remark;

    public InfoPublishClientMdnsRegistration(JmDNSServiceProvider jmdnsServiceProvider) {
        this.jmdnsServiceProvider = jmdnsServiceProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnReady() {
        if (!mdnsEnabled) {
            log.info("[InfoPublishClient-mDNS] mDNS registration disabled: provider.jmdns.enabled=false");
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
            log.info("[InfoPublishClient-mDNS] registered: name={}, port={}, props={}",
                    resolvedServiceName, resolvedPort, props);
        } else {
            registered.set(false);
            log.warn("[InfoPublishClient-mDNS] registration failed: name={}, port={}",
                    resolvedServiceName, resolvedPort);
        }
    }

    private Map<String, String> buildPublicProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("deviceType", StringUtils.hasText(deviceType) ? deviceType.trim() : "publish_server");
        props.put("role", "info_publish_client");
        props.put("path", "/api/status/overview");
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

package com.gateway.udpproxy.discovery;

import com.gateway.device.core.event.DeviceDiscoveredEvent;
import com.gateway.device.protocol.model.DeviceContext;
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
public class TerminalGatewayMdnsRegistration {

    private static final String DEFAULT_SERVICE_NAME = "terminal-udp-gateway";
    private static final int DEFAULT_SERVER_PORT = 8093;

    private final JmDNSServiceProvider jmdnsServiceProvider;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    @Value("${provider.jmdns.enabled:true}")
    private boolean mdnsEnabled;

    @Value("${registry.client.service-name:${spring.application.name:terminal-udp-gateway}}")
    private String serviceName;

    @Value("${server.port:8093}")
    private int serverPort;

    @Value("${registry.client.device-type:terminal_encrypt_gateway}")
    private String deviceType;

    @Value("${registry.client.version:}")
    private String version;

    @Value("${registry.client.manufacturer:}")
    private String manufacturer;

    @Value("${registry.client.model:}")
    private String model;

    @Value("${registry.client.remark:}")
    private String remark;

    public TerminalGatewayMdnsRegistration(JmDNSServiceProvider jmdnsServiceProvider) {
        this.jmdnsServiceProvider = jmdnsServiceProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnReady() {
        if (!mdnsEnabled) {
            log.info("[TerminalGateway-mDNS] mDNS registration disabled: provider.jmdns.enabled=false");
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
            log.info("[TerminalGateway-mDNS] registered: name={}, port={}, props={}",
                    resolvedServiceName, resolvedPort, props);
        } else {
            registered.set(false);
            log.warn("[TerminalGateway-mDNS] registration failed: name={}, port={}",
                    resolvedServiceName, resolvedPort);
        }
    }

    @EventListener
    public void onDeviceDiscovered(DeviceDiscoveredEvent event) {
        if (!mdnsEnabled) return;
        for (DeviceContext device : event.getDevices()) {
            Map<String, String> props = new LinkedHashMap<>();
            props.put("deviceType", "led_controller");
            props.put("vendor", device.getVendor() != null ? device.getVendor().name() : "unknown");
            props.put("groupLabel", device.getGroupLabel() != null ? device.getGroupLabel() : "-");
            if (device.getSn() != null) props.put("serialNo", device.getSn());
            String serviceName = "led-" + device.getIp().replace(".", "-");
            jmdnsServiceProvider.register(serviceName,
                    device.getPort() > 0 ? device.getPort() : 9520, props);
            log.info("[mDNS] 注册发现设备: {} ({}:{})", serviceName, device.getIp(), device.getPort());
        }
    }

    private Map<String, String> buildPublicProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("deviceType", StringUtils.hasText(deviceType) ? deviceType.trim() : "terminal_encrypt_gateway");
        props.put("role", "decrypt_gateway");
        props.put("path", "/udp-proxy/rules/running");
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

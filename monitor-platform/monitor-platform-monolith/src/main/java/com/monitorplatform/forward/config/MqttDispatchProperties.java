package com.monitorplatform.forward.config;

import com.monitorplatform.mqtt.core.client.MqttSslOptions;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "forward.dispatch")
public class MqttDispatchProperties {

    private String mode = "http";

    private String mqttBrokerUrl = "ssl://127.0.0.1:8883";

    private String mqttUsername;

    private String mqttPassword;

    private String tenantId = "default";

    private String siteId = "site-001";

    private String platformClientId = "monitor-platform-001";

    private int keepAliveSec = 30;

    private int commandTimeoutSec = 60;

    private long reconnectIntervalMs = 30000L;

    private int heartbeatIntervalSec = 30;

    private int qos = 1;

    private boolean authEnabled = true;

    private String deviceDefaultPassword;

    private boolean allowEmptyDevicePassword = true;

    private String platformClientPrefix = "monitor-platform,platform,server";

    private String truststorePath;

    private String truststorePassword;

    public MqttSslOptions buildSslOptions() {
        if (truststorePath == null || truststorePath.trim().isEmpty()) {
            return null;
        }
        MqttSslOptions sslOptions = new MqttSslOptions();
        sslOptions.setTruststorePath(truststorePath);
        sslOptions.setTruststorePassword(truststorePassword);
        sslOptions.setHostnameVerificationEnabled(true);
        return sslOptions;
    }
}

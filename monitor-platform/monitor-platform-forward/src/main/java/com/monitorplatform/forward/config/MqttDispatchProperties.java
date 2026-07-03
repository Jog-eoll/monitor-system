package com.monitorplatform.forward.config;

import com.monitorplatform.mqtt.core.client.MqttSslOptions;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MQTT 下发配置属性
 * <p>
 * 对应配置前缀 forward.dispatch，控制下发模式（http/mqtt/dual）、
 * Broker 连接参数、命令超时等。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "forward.dispatch")
public class MqttDispatchProperties {

    /**
     * 下发模式：http（仅 HTTP）/ mqtt（仅 MQTT）/ dual（双通道，默认 http）
     */
    private String mode = "http";

    /**
     * MQTT Broker 地址，如 ssl://127.0.0.1:8883 或 tcp://127.0.0.1:1883
     */
    private String mqttBrokerUrl = "ssl://127.0.0.1:8883";

    /**
     * MQTT 用户名（可为 null）
     */
    private String mqttUsername;

    /**
     * MQTT 密码（可为 null）
     */
    private String mqttPassword;

    /**
     * 默认租户ID
     */
    private String tenantId = "default";

    /**
     * 默认站点ID
     */
    private String siteId = "site-001";

    /** 平台 MQTT 客户端ID（需全局唯一） */
    private String platformClientId = "monitor-platform-001";

    /**
     * TLS 信任库路径（JKS/PKCS12），用于校验 broker 域名证书链。
     * 留空时使用 JVM 默认信任链。
     */
    private String truststorePath;

    /** 信任库密码 */
    private String truststorePassword;

    /**
     * MQTT 心跳间隔（秒）
     */
    private int keepAliveSec = 30;

    /**
     * 命令超时时间（秒），超过此时间未收到回执则标记超时
     */
    private int commandTimeoutSec = 60;

    /**
     * 初次连接失败后的重连扫描间隔（毫秒）
     */
    private long reconnectIntervalMs = 30000L;

    /**
     * 平台主动发送心跳的间隔（秒）
     */
    private int heartbeatIntervalSec = 30;

    /**
     * MQTT 默认 QoS 级别（0/1/2）
     */
    private int qos = 1;

    private boolean authEnabled = true;

    private String deviceDefaultPassword;

    /** 生产环境必须为 false，拒绝未配置密码的设备接入 */
    private boolean allowEmptyDevicePassword = false;

    private String platformClientPrefix = "monitor-platform,platform,server";

    /**
     * 根据当前配置构建 MqttSslOptions。
     * 当 truststorePath 未配置时返回 null（回退 JVM 默认信任链）。
     */
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

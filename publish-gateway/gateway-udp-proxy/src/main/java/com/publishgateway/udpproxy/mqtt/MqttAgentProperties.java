package com.publishgateway.udpproxy.mqtt;

import com.monitorplatform.mqtt.core.client.MqttSslOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * MQTT Agent 配置 —— 发布网关作为 MQTT 客户端接入管控平台 Broker 的参数。
 * <p>
 * 通过 mqtt-agent.* 前缀在 application.yml 中配置。enabled 默认 false，
 * 仅当显式开启时才会创建 MQTT 连接、订阅下行命令并上行心跳/回执。
 * </p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mqtt-agent")
public class MqttAgentProperties {

    /** 是否启用 MQTT Agent（默认关闭，开启后才连接 Broker） */
    private boolean enabled = false;

    /** Broker 地址，默认走 SSL 端口 */
    private String brokerUrl = "ssl://127.0.0.1:8883";

    /** 登录用户名 */
    private String username;

    /** 登录密码 */
    private String password;

    /** 客户端 ID（需全局唯一） */
    private String clientId = "publish-gateway-001";

    /** 租户 ID */
    private String tenantId = "default";

    /** 站点 ID */
    private String siteId = "site-001";

    /**
     * 设备 ID，默认复用设备注册客户端的 client-id。
     * 注意：@ConfigurationProperties 不会自动解析 ${} 占位符，
     * 实际使用时请通过 {@link #resolveDeviceId()} 获取解析后的值。
     */
    private String deviceId = "${registry.client.client-id:publish-gateway-001}";

    /** 设备类型 */
    private String deviceType = "publish_gateway";

    /**
     * HTTP 转发允许的路径前缀列表。
     * <p>
     * 用于 LocalHttpForwardService 的路径白名单校验，
     * 默认允许 /udp-proxy/config 和 /api/ 前缀。
     * </p>
     */
    private List<String> httpForwardAllowedPaths = Arrays.asList(
            "/udp-proxy/config",
            "/udp-proxy/chain/",
            "/udp-proxy/stop/",
            "/udp-proxy/status/",
            "/udp-proxy/rules",
            "/api/client/commands/",
            "/api/secure-command/",
            "/api/command/"
    );

    /**
     * HTTP 转发超时时间（毫秒）。
     * <p>
     * 用于 LocalHttpForwardService 的 HTTP 调用超时，
     * 默认 5000ms。
     * </p>
     */
    private int httpForwardTimeoutMs = 5000;

    /** MQTT 心跳保活间隔（秒） */
    private int keepAliveSec = 30;

    /** 应用层心跳上行间隔（秒），同时用于 @Scheduled fixedDelay */
    private int heartbeatIntervalSec = 30;

    /** 初次连接失败后的重连扫描间隔（毫秒） */
    private long reconnectIntervalMs = 30000L;

    /** 命令幂等记录保留时间（毫秒） */
    private long commandDedupTtlMs = 86400000L;

    /** 命令幂等记录清理间隔（毫秒） */
    private long dedupCleanupIntervalMs = 600000L;

    /** QoS 级别（0/1/2） */
    private int qos = 1;

    /** 是否清除会话 */
    private boolean cleanSession = true;

    /** 是否自动重连 */
    private boolean autoReconnect = true;

    /**
     * TLS 信任库路径（JKS/PKCS12），用于校验 broker 域名证书链。
     * 留空时使用 JVM 默认信任链（适用于公共 CA 证书）。
     * 公司内部 CA 时必须配置，如 /app/certs/company-ca.jks。
     */
    private String truststorePath;

    /** 信任库密码 */
    private String truststorePassword;

    private boolean registrationEnabled = true;

    private boolean registerOnConnect = true;

    private int registrationIntervalSec = 300;

    private String serviceName = "publish-gateway";

    private String registerHost = "";

    private Integer registerPort;

    private String macAddress;

    private String location;

    private String version;

    private String manufacturer;

    private String model;

    private String remark;

    /** Spring 环境，用于解析 deviceId 中的 ${} 占位符（不参与 equals/hashCode/toString） */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Resource
    private transient Environment environment;

    /**
     * 解析 deviceId 占位符 —— @ConfigurationProperties 字段默认值中的
     * ${registry.client.client-id:publish-gateway-001} 需借助 Environment 解析。
     *
     * @return 解析后的设备 ID；为空时回退到 publish-gateway-001
     */
    public String resolveDeviceId() {
        String id = this.deviceId;
        if (id == null || id.trim().isEmpty()) {
            return "publish-gateway-001";
        }
        if (id.contains("${") && environment != null) {
            try {
                id = environment.resolvePlaceholders(id);
            } catch (Exception e) {
                // 占位符无法解析时保留原值，交由调用方决定是否可用
            }
        }
        return (id == null || id.trim().isEmpty()) ? "publish-gateway-001" : id.trim();
    }

    public String resolveText(String value) {
        if (value == null) {
            return null;
        }
        String resolved = value;
        if (resolved.contains("${") && environment != null) {
            try {
                resolved = environment.resolvePlaceholders(resolved);
            } catch (Exception e) {
                // Keep the raw value and let caller decide whether it is usable.
            }
        }
        return resolved == null ? null : resolved.trim();
    }

    /**
     * 根据当前配置构建 MqttSslOptions。
     * 当 truststorePath 未配置时返回 null（回退 JVM 默认信任链）。
     *
     * @return SSL 选项对象，不需要时返回 null
     */
    public MqttSslOptions buildSslOptions() {
        if (truststorePath == null || truststorePath.trim().isEmpty()) {
            return null;
        }
        MqttSslOptions sslOptions = new MqttSslOptions();
        sslOptions.setTruststorePath(resolveText(truststorePath));
        sslOptions.setTruststorePassword(resolveText(truststorePassword));
        sslOptions.setHostnameVerificationEnabled(true);
        return sslOptions;
    }
}

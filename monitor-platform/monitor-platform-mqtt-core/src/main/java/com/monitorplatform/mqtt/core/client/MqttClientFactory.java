package com.monitorplatform.mqtt.core.client;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

/**
 * Paho MQTT v3 客户端工厂 —— 统一创建 MqttClient 实例。
 * <p>
 * Java 8 下使用 Paho v3 client（非 v5），避免 Java 11+ 依赖。
 * 连接选项支持自动重连、TLS、用户名密码认证。
 * v2 增加 {@link MqttSslOptions} 可选参数：默认 JVM 信任链，私有 CA 时加载公司 CA truststore。
 * </p>
 */
@Slf4j
public final class MqttClientFactory {

    private MqttClientFactory() {
    }

    /**
     * 创建并连接 MQTT 客户端（使用 JVM 默认信任链）。
     *
     * @param brokerUrl  Broker 地址，如 ssl://xxx:8883 或 tcp://xxx:1883
     * @param clientId   客户端 ID（需全局唯一）
     * @param username   用户名（可为 null）
     * @param password   密码（可为 null）
     * @param cleanSession 是否清除会话
     * @param keepAliveSec 心跳间隔（秒）
     * @param autoReconnect  是否自动重连
     * @return 已连接的 MqttClient
     * @throws MqttException 连接失败时抛出
     */
    public static MqttClient createAndConnect(
            String brokerUrl, String clientId,
            String username, String password,
            boolean cleanSession, int keepAliveSec,
            boolean autoReconnect) throws MqttException {
        return createAndConnect(brokerUrl, clientId, username, password,
                cleanSession, keepAliveSec, autoReconnect, null);
    }

    /**
     * 创建并连接 MQTT 客户端（带 SSL 选项）。
     * <p>
     * sslOptions 为 null 或无需自定义 SSLContext 时，回退到 JVM 默认信任链。
     * 信任库（truststore）用于校验 broker 证书链；keystore 预留 mTLS 二期。
     * </p>
     *
     * @param brokerUrl  Broker 地址
     * @param clientId   客户端 ID（需全局唯一）
     * @param username   用户名（可为 null）
     * @param password   密码（可为 null）
     * @param cleanSession 是否清除会话
     * @param keepAliveSec 心跳间隔（秒）
     * @param autoReconnect  是否自动重连
     * @param sslOptions SSL 配置（可为 null）
     * @return 已连接的 MqttClient
     * @throws MqttException 连接失败时抛出
     */
    public static MqttClient createAndConnect(
            String brokerUrl, String clientId,
            String username, String password,
            boolean cleanSession, int keepAliveSec,
            boolean autoReconnect, MqttSslOptions sslOptions) throws MqttException {

        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(cleanSession);
        options.setKeepAliveInterval(keepAliveSec);
        options.setAutomaticReconnect(autoReconnect);
        options.setConnectionTimeout(10);

        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
        }
        if (password != null && !password.isEmpty()) {
            options.setPassword(password.toCharArray());
        }

        if (brokerUrl.startsWith("ssl://")) {
            configureSsl(options, sslOptions, brokerUrl, clientId);
        }

        client.connect(options);
        log.info("[MQTT工厂] 客户端已连接: broker={}, clientId={}, cleanSession={}, sslCustom={}",
                brokerUrl, clientId, cleanSession,
                sslOptions != null && sslOptions.requiresCustomSslContext());

        return client;
    }

    private static void configureSsl(MqttConnectOptions options, MqttSslOptions sslOptions,
                                     String brokerUrl, String clientId) {
        if (sslOptions == null || !sslOptions.requiresCustomSslContext()) {
            try {
                options.setSocketFactory(javax.net.ssl.SSLSocketFactory.getDefault());
                log.info("[MQTT工厂] 使用 JVM 默认信任链: broker={}", brokerUrl);
            } catch (Exception e) {
                log.warn("[MQTT工厂] JVM SSL SocketFactory 初始化失败: {}", e.getMessage());
            }
            return;
        }

        try {
            SSLContext sslContext = buildSslContext(sslOptions);
            options.setSocketFactory(sslContext.getSocketFactory());
            log.info("[MQTT工厂] 已加载自定义 SSLContext: broker={}, truststore={}, keystore={}",
                    brokerUrl, sslOptions.hasTruststore(), sslOptions.hasKeystore());
        } catch (Exception e) {
            log.error("[MQTT工厂] 自定义 SSLContext 加载失败，回退 JVM 默认: clientId={}, {}",
                    clientId, e.getMessage(), e);
            try {
                options.setSocketFactory(javax.net.ssl.SSLSocketFactory.getDefault());
            } catch (Exception ex) {
                log.warn("[MQTT工厂] JVM SSL SocketFactory 也初始化失败: {}", ex.getMessage());
            }
        }
    }

    private static SSLContext buildSslContext(MqttSslOptions sslOptions) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");

        javax.net.ssl.KeyManager[] keyManagers = null;
        if (sslOptions.hasKeystore()) {
            KeyStore keyStore = loadKeyStore(sslOptions.getKeystorePath(),
                    sslOptions.getKeystorePassword());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore,
                    sslOptions.getKeyPassword() != null
                            ? sslOptions.getKeyPassword().toCharArray()
                            : (sslOptions.getKeystorePassword() != null
                                    ? sslOptions.getKeystorePassword().toCharArray() : null));
            keyManagers = kmf.getKeyManagers();
        }

        javax.net.ssl.TrustManager[] trustManagers = null;
        if (sslOptions.hasTruststore()) {
            KeyStore trustStore = loadKeyStore(sslOptions.getTruststorePath(),
                    sslOptions.getTruststorePassword());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
            log.info("[MQTT信任库] 已加载 Company CA: {}", sslOptions.getTruststorePath());
        }

        sslContext.init(keyManagers, trustManagers, null);

        if (!sslOptions.isHostnameVerificationEnabled()) {
            log.warn("[MQTT证书] 主机名校验已关闭");
        }

        return sslContext;
    }

    private static KeyStore loadKeyStore(String path, String password) throws Exception {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("KeyStore path is empty");
        }
        String lower = path.toLowerCase();
        String type = (lower.endsWith(".p12") || lower.endsWith(".pkcs12") || lower.endsWith(".pfx"))
                ? "PKCS12" : "JKS";
        KeyStore ks = KeyStore.getInstance(type);
        char[] pwd = (password != null && !password.isEmpty()) ? password.toCharArray() : null;
        try (FileInputStream fis = new FileInputStream(path)) {
            ks.load(fis, pwd);
        }
        return ks;
    }

    /**
     * 安全断开 MQTT 客户端。
     */
    public static void disconnectQuietly(MqttClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
            log.info("[MQTT工厂] 客户端已断开");
        } catch (MqttException e) {
            log.warn("[MQTT工厂] 断开异常: {}", e.getMessage());
        }
    }
}

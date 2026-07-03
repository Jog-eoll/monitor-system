package com.monitorplatform.mqtt.core.client;

import lombok.Data;

import java.io.Serializable;

/**
 * MQTT SSL/TLS 可选配置 —— 当 broker 使用公司内部 CA 签发的证书时使用。
 * <p>
 * <ul>
 *   <li>truststorePath / truststorePassword：指定信任库，用于校验 broker 证书链</li>
 *   <li>keystorePath / keystorePassword：预留 mTLS 二期使用（单向 TLS 不生效）</li>
 * </ul>
 * 全部字段为空时，回退到 JVM 默认信任链。
 * </p>
 */
@Data
public class MqttSslOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 信任库路径（JKS 或 PKCS12 格式），null 时使用 JVM 默认信任链 */
    private String truststorePath;

    /** 信任库密码 */
    private String truststorePassword;

    /** 客户端证书库路径（mTLS 二期预留），null 时启用 */
    private String keystorePath;

    /** 客户端证书库密码 */
    private String keystorePassword;

    /** 客户端私钥密码 */
    private String keyPassword;

    /** 是否跳过主机名校验（仅调试用，生产必须 false） */
    private boolean hostnameVerificationEnabled = true;

    public boolean hasTruststore() {
        return truststorePath != null && !truststorePath.trim().isEmpty();
    }

    public boolean hasKeystore() {
        return keystorePath != null && !keystorePath.trim().isEmpty();
    }

    /** 是否需要自定义 SSLContext（truststore 或 keystore 任一存在时为 true） */
    public boolean requiresCustomSslContext() {
        return hasTruststore() || hasKeystore();
    }
}

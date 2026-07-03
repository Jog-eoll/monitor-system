package com.publishgateway.udpproxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 安全投递信封配置
 * <p>
 * 用于灰度和回滚：如果联调失败，可临时切回旧扁平 JSON；
 * 解密网关已有 SecureEnvelopeParser 回退逻辑。
 * </p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "secure-delivery.envelope")
public class SecureDeliveryEnvelopeProperties {

    /** 是否启用 v2 信封模式；false 时回退到旧扁平 JSON */
    private boolean enabled = false;

    /** 信封 schema 版本 */
    private String schemaVersion = "2.0";
}

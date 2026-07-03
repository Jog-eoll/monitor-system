package com.gateway.standardization.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 标准化模块配置属性
 * <p>
 * 通过 application.yml 的 standardization.* 前缀配置。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "standardization")
public class StandardizationProperties {

    /** 是否启用标准化消息封装（默认开启） */
    private boolean enabled = true;

    /** 客户端来源校验回调地址（info-publish-client 端口） */
    private int clientPort = 7080;

    /** 客户端校验超时（毫秒） */
    private int clientValidateTimeoutMs = 5000;

    /** 是否启用逐包来源校验回调 */
    private boolean perPacketClientValidationEnabled = false;

    /** 默认 sessionId（当无法从 precheck 获取时使用） */
    private String defaultSessionId = "default-session";
}

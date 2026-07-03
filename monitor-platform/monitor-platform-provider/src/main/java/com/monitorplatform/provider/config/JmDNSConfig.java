package com.monitorplatform.provider.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JmDNS 服务提供者配置
 * 仅控制总开关，具体参数（名称、端口、属性）由各服务调用 register() 时自行传入
 */
@Data
@ConfigurationProperties(prefix = "provider.jmdns")
public class JmDNSConfig {

    /** 是否启用 JmDNS 服务注册 */
    private boolean enabled = true;

    /** mDNS 绑定地址；为空时自动选择非 loopback IPv4 地址 */
    private String bindAddress;

    /** TXT host 对外通告地址；为空时使用绑定地址 */
    private String advertiseHost;
}

package com.monitorplatform.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * JmDNS 服务发现配置
 * 支持从 Nacos 配置中心动态刷新
 * 服务类型固定为 _http._tcp.local.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "content.jmdns")
public class JmDNSConfig {

    /** 是否启用 JmDNS 服务发现 */
    private boolean enabled = true;

    /** JmDNS 监听的多播组地址（IPv4） */
    private String multicastGroup = "224.0.0.251";

    /** JmDNS 监听端口 */
    private int port = 5353;

    /** JmDNS 绑定地址；为空时自动选择非 loopback IPv4 地址 */
    private String bindAddress;

    /** 服务发现轮询间隔（毫秒），0 表示仅监听事件不轮询 */
    private long pollingIntervalMs = 30000;

    /** 服务超时清理时间（毫秒），超过此时间未更新的服务视为离线，0 表示不自动清理 */
    private long serviceTimeoutMs = 120000;

    /** 是否在启动时立即开始扫描 */
    private boolean startOnBoot = false;

    /** 手动指定的网关服务名称关键字，用于过滤目标服务 */
    private List<String> gatewayNameKeywords = new ArrayList<>();
}

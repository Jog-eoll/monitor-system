package com.gateway.udpproxy.snapshot;

import com.gateway.udpproxy.entity.UdpProxyRule;

/**
 * 厂家截图策略
 */
public interface ScreenSnapshotStrategy {

    /**
     * 支持的厂家标识
     */
    String supportedManufacturer();

    /**
     * 截图
     */
    SnapshotResult capture(UdpProxyRule rule, String outputFilePath, int timeoutMs);
}

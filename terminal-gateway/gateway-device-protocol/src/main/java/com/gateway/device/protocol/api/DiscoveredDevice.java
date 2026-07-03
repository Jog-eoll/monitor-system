package com.gateway.device.protocol.api;

import java.util.Map;

/**
 * 广播发现的设备信息 —— 协议无关接口。
 *
 * <p>各协议提供各自的 POJO 实现，core 层仅依赖此接口。</p>
 */
public interface DiscoveredDevice {

    /**
     * 设备 IP 地址
     */
    String getIp();

    /**
     * 回送来源端口
     */
    int getSourcePort();

    /**
     * 协议私有属性（如 JetFileII 的 GG/UU/CPU 版本等）
     */
    Map<String, Object> getAttributes();
}

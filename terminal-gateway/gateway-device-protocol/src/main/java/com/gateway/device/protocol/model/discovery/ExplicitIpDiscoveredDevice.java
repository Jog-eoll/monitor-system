package com.gateway.device.protocol.model.discovery;

import com.gateway.device.protocol.api.DiscoveredDevice;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 显式 IP 注册模式的发现设备 —— 仅含 IP 和端口，无预设属性。
 *
 * <p>用于 {@code DeviceVendorMapping.ips} 配置的直连注册路径，
 * 跳过广播/子网发现，直接通过 {@code processDevice()} 流程完成设备识别与注册。</p>
 */
@Getter
@AllArgsConstructor
public class ExplicitIpDiscoveredDevice implements DiscoveredDevice {

    /**
     * 目标设备 IP
     */
    private final String ip;

    /**
     * 目标端口（来自映射的有效端口）
     */
    private final int sourcePort;

    /**
     * 无预设属性，后续由 {@code DEVICE_INFO_GET} + {@code DeviceInfoEnricher} 填充。
     */
    @Override
    public Map<String, Object> getAttributes() {
        return new HashMap<>();
    }
}

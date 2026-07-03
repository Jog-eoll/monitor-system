package com.gateway.device.protocol.base.colorlight.standard.discovery;

import com.gateway.device.protocol.api.DiscoveredDevice;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ColorLight TCP 探测发现的设备信息 —— 仅 IP:Port 载体。
 *
 * <p>设备信息由后续 {@code DeviceInfoGetHandler} 获取。</p>
 */
@Data
@Builder
public class ColorLightDiscoveredDevice implements DiscoveredDevice {

    /**
     * 设备 IP 地址
     */
    private String ip;

    /**
     * 探测端口（8989 或 80）
     */
    private int sourcePort;

    /**
     * 主板兼容设备名（来自 /api/terminal.json → name）
     */
    private String deviceName;

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (deviceName != null) {
            attrs.put("deviceName", deviceName);
        }
        return attrs;
    }

    @Override
    public String toString() {
        return String.format("%-16s port=%d", ip, sourcePort);
    }
}

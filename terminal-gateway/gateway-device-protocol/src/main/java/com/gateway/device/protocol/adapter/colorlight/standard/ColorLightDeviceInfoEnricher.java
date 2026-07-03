package com.gateway.device.protocol.adapter.colorlight.standard;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.api.DeviceInfoEnricher;
import com.gateway.device.protocol.api.DiscoveredDevice;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * ColorLight 设备信息增强器 —— 从 {@link JsonNode}（由 {@code DeviceInfoGetHandler} 组装）提取设备属性。
 */
@Slf4j
public class ColorLightDeviceInfoEnricher implements DeviceInfoEnricher {

    private void putText(Map<String, Object> attrs, JsonNode node, String key) {
        String value = node.path(key).asText(null);
        if (StringUtils.isNotBlank(value)) {
            attrs.put(key, value);
        }
    }

    private void putLong(Map<String, Object> attrs, JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value != null && value.isNumber()) {
            attrs.put(key, value.longValue());
        }
    }

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.COLOR_LIGHT_STANDARD;
    }

    @Override
    public void enrichJson(DiscoveredDevice dd, JsonNode info, Map<String, Object> attrs) {
        if (info == null || attrs == null) return;

        putText(attrs, info, "sn");
        putText(attrs, info, "model");
        putText(attrs, info, "vername");
        putText(attrs, info, "mac");
        putText(attrs, info, "playingName");

        putLong(attrs, info, "width");
        putLong(attrs, info, "height");
    }
}

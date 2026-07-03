package com.gateway.device.protocol.adapter.novastar.viplexcore;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.api.DeviceInfoEnricher;
import com.gateway.device.protocol.api.DiscoveredDevice;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import lombok.extern.slf4j.Slf4j;


import java.util.*;

/**
 * ViplexCore 设备信息增强器 —— 从固件 JSON 补充平台相关字段。
 *
 * <p>发现阶段已填充基础属性（SN/MAC/产品名等），本类补充固件 JSON 中的
 * 平台特有字段（androidVersion/osVersion 等），这些字段在 rk3288 上不存在。</p>
 */
@Slf4j
public class NovaViplexCoreDeviceInfoEnricher implements DeviceInfoEnricher {

    /**
     * 发现阶段已填充的属性键（通过 ViplexCoreDiscoveredDevice.getAttributes()）
     * + 本类补充的平台字段
     */
    private static final Set<String> KNOWN_KEYS = new HashSet<>(Arrays.asList(
            "sn", "productName", "model", "mac", "fpga", "mainVersion",
            "aliasName", "width", "height",
            "androidVersion", "osVersion", "pcbVersion", "registerAddress"));

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.NOVA_STAR_VIPLEX_CORE;
    }

    @Override
    public void enrichJson(DiscoveredDevice dd, JsonNode info, Map<String, Object> attrs) {
        // MAC 防御性二次格式化
        if (info.has("mac")) {
            attrs.put("mac", ProtocolConstant.formatMac(info.get("mac").asText()));
        }

        // 平台相关字段（rk356x/Android 有 androidVersion/osVersion，rk3288/Linux 无）
        if (info.has("androidVersion"))
            attrs.put("androidVersion", info.get("androidVersion").asText());
        if (info.has("osVersion"))
            attrs.put("osVersion", info.get("osVersion").asText());
        if (info.has("pcbVersion"))
            attrs.put("pcbVersion", info.get("pcbVersion").asText());
        if (info.has("registerAddress"))
            attrs.put("registerAddress", info.get("registerAddress").asText());

        // 精简日志：仅输出仍未映射的字段
        List<String> unmapped = new ArrayList<>();
        Iterator<String> fieldNames = info.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!KNOWN_KEYS.contains(field) && !attrs.containsKey(field)) {
                unmapped.add(field + "=" + info.get(field).asText());
            }
        }
        if (!unmapped.isEmpty()) {
            log.debug("[{}] 固件未映射字段: {}", dd.getIp(), String.join(", ", unmapped));
        }
    }
}

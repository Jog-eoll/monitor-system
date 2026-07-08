package com.gateway.device.protocol.adapter.colorlight.standard;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.api.DeviceComplianceValidator;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.discovery.ComplianceRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * ColorLight 设备合规校验 —— 校验 model 等字段。
 *
 * <p>infoData 为 {@link JsonNode}（来自 {@link ColorLightDeviceRegistrationProvider} 的
 * /api/info.json + /api/dimension.json 合并结果）。</p>
 */
@Slf4j
public class ColorLightComplianceValidator implements DeviceComplianceValidator {

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.COLOR_LIGHT_STANDARD;
    }

    @Override
    public boolean validate(DeviceContext device, Object infoData, List<ComplianceRule> rules) {
        if (CollectionUtils.isEmpty(rules)) {
            return true;
        }

        for (ComplianceRule rule : rules) {
            String field = rule.getField();
            String actual = extractField(infoData, device.getAttributes(), field);

            if (actual == null || !rule.getExpected().contains(actual)) {
                log.debug("[{}] 合规不通过: {}={} not in {}",
                        device.getIp(), field, actual, rule.getExpected());
                return false;
            }
            log.debug("[{}] 合规通过: {}={}", device.getIp(), field, actual);
        }
        return true;
    }

    private String extractField(Object infoData, Map<String, Object> attrs, String field) {
        if (infoData instanceof JsonNode) {
            JsonNode json = (JsonNode) infoData;
            if (json.has(field) && !json.get(field).isNull()) {
                return json.get(field).asText();
            }
        }
        if (attrs != null && attrs.containsKey(field)) {
            Object val = attrs.get(field);
            return val != null ? val.toString() : null;
        }
        return null;
    }
}

package com.gateway.device.protocol.adapter.novastar.viplexcore;

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
 * NovaStar ViplexCore 设备合规校验 —— 校验产品名称为 TB4/T4H。
 *
 * <p>infoData 为 {@link JsonNode}（来自 DEVICE_INFO_GET 的 nvGetFirmwareInfosAsync 回调），
 * 或从 {@code device.getAttributes()} 读取已注册属性。</p>
 */
@Slf4j
public class NovaViplexCoreComplianceValidator implements DeviceComplianceValidator {

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.NOVA_STAR_VIPLEX_CORE;
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

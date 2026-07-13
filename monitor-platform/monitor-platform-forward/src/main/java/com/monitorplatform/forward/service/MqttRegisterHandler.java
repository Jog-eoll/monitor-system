package com.monitorplatform.forward.service;

import com.monitorplatform.forward.feign.DeviceFeignClient;
import com.monitorplatform.mqtt.core.dto.MqttDeviceRegisterMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles device active registration messages from MQTT up/register.
 */
@Slf4j
@Service
public class MqttRegisterHandler {

    @Resource
    private DeviceFeignClient deviceFeignClient;

    public void handleRegister(MqttDeviceRegisterMessage register) {
        if (register == null) {
            log.warn("[MQTT注册] register payload为空，忽略");
            return;
        }

        String instanceId = firstText(register.getInstanceId(), register.getDeviceId());
        if (!hasText(instanceId) || !hasText(register.getHost()) || register.getPort() == null) {
            log.warn("[MQTT注册] 注册字段不完整: instanceId={}, host={}, port={}",
                    instanceId, register.getHost(), register.getPort());
            return;
        }

        // 设备重复注册治理：记录去重键，后端按 deviceType+host+port 检查旧记录并标记离线
        String dedupKey = register.getDeviceType() + ":" + register.getHost() + ":" + register.getPort();
        log.info("[MQTT注册] 设备注册去重键: dedupKey={}, instanceId={}", dedupKey, instanceId);

        Map<String, Object> body = toRegisterBody(register, instanceId);
        body.put("dedupKey", dedupKey);
        try {
            Map<String, Object> response = deviceFeignClient.register(body);
            if (isSuccess(response)) {
                log.info("[MQTT注册] 设备注册成功: instanceId={}, deviceType={}, host={}, port={}",
                        instanceId, register.getDeviceType(), register.getHost(), register.getPort());
            } else {
                log.warn("[MQTT注册] 设备注册失败: instanceId={}, response={}", instanceId, response);
            }
        } catch (Exception e) {
            log.warn("[MQTT注册] 调用设备注册服务异常: instanceId={}, error={}", instanceId, e.getMessage());
        }
    }

    private Map<String, Object> toRegisterBody(MqttDeviceRegisterMessage register, String instanceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceName", firstText(register.getServiceName(), register.getDeviceType()));
        body.put("instanceId", instanceId);
        body.put("host", register.getHost());
        body.put("port", register.getPort());
        body.put("macAddress", register.getMacAddress());
        body.put("deviceType", register.getDeviceType());
        body.put("location", register.getLocation());
        body.put("version", register.getVersion());
        body.put("manufacturer", register.getManufacturer());
        body.put("model", register.getModel());
        body.put("remark", register.getRemark());
        return body;
    }

    private boolean isSuccess(Map<String, Object> response) {
        Object code = response == null ? null : response.get("code");
        return code instanceof Number && ((Number) code).intValue() == 200;
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : (hasText(second) ? second.trim() : null);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

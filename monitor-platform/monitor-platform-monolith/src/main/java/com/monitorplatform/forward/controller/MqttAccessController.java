package com.monitorplatform.forward.controller;

import com.monitorplatform.forward.config.MqttDispatchProperties;
import com.monitorplatform.forward.feign.DeviceFeignClient;
import com.monitorplatform.mqtt.core.dto.MqttAuthRequest;
import com.monitorplatform.mqtt.core.dto.MqttWebhookRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * MQTT broker HTTP auth and lifecycle webhook adapter.
 */
@Slf4j
@RestController
public class MqttAccessController {

    private static final String STATUS_OFFLINE = "\u79bb\u7ebf";

    @Resource
    private MqttDispatchProperties properties;

    @Resource
    private DeviceFeignClient deviceFeignClient;

    @PostMapping({"/mqtt/auth", "/tool/mqtt/auth"})
    public ResponseEntity<Void> auth(@RequestBody(required = false) MqttAuthRequest request) {
        if (!properties.isAuthEnabled()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        if (request == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String clientId = trim(request.normalizedClientId());
        String username = trim(request.getUsername());
        String password = request.getPassword();
        boolean allowed = isPlatformClient(clientId)
                ? verifyPlatformCredential(username, password)
                : verifyDeviceCredential(clientId, username, password);

        if (!allowed) {
            log.warn("[MQTT-AUTH] 拒绝接入: clientId={}, username={}", clientId, username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping({"/mqtt/webhook", "/tool/mqtt/webhook"})
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody(required = false) MqttWebhookRequest request) {
        if (request == null) {
            return ResponseEntity.ok(ok());
        }

        String event = trim(request.getEvent());
        String clientId = trim(request.normalizedClientId());
        if (!hasText(event) || isPlatformClient(clientId)) {
            return ResponseEntity.ok(ok());
        }

        String instanceId = resolveDeviceId(request);
        if (!hasText(instanceId)) {
            log.debug("[MQTT-WEBHOOK] 无法解析设备ID: event={}, clientId={}, username={}",
                    event, clientId, request.getUsername());
            return ResponseEntity.ok(ok());
        }

        try {
            String eventLower = event.toLowerCase();
            if (eventLower.contains("connected")) {
                deviceFeignClient.heartbeat(instanceId);
                log.info("[MQTT-WEBHOOK] 设备连接: instanceId={}, clientId={}", instanceId, clientId);
            } else if (eventLower.contains("disconnected")) {
                deviceFeignClient.updateStatus(instanceId, STATUS_OFFLINE);
                log.info("[MQTT-WEBHOOK] 设备断开: instanceId={}, clientId={}", instanceId, clientId);
            }
        } catch (Exception e) {
            log.warn("[MQTT-WEBHOOK] 更新设备状态异常: event={}, instanceId={}, error={}",
                    event, instanceId, e.getMessage());
        }
        return ResponseEntity.ok(ok());
    }

    private boolean verifyPlatformCredential(String username, String password) {
        if (!hasText(properties.getMqttUsername())) {
            return true;
        }
        return properties.getMqttUsername().equals(username)
                && nullSafeEquals(properties.getMqttPassword(), password);
    }

    private boolean verifyDeviceCredential(String clientId, String username, String password) {
        if (!hasText(clientId) && !hasText(username)) {
            return false;
        }
        String expected = trim(properties.getDeviceDefaultPassword());
        if (!hasText(expected)) {
            return properties.isAllowEmptyDevicePassword();
        }
        return expected.equals(password);
    }

    private String resolveDeviceId(MqttWebhookRequest request) {
        String username = trim(request.getUsername());
        if (hasText(username) && username.contains("&")) {
            String[] parts = username.split("&");
            if (parts.length >= 3 && hasText(parts[2])) {
                return parts[2].trim();
            }
            if (parts.length >= 2 && hasText(parts[parts.length - 1])) {
                return parts[parts.length - 1].trim();
            }
        }
        if (hasText(username) && !isPlatformClient(username)) {
            return username;
        }
        return trim(request.normalizedClientId());
    }

    private boolean isPlatformClient(String clientId) {
        if (!hasText(clientId)) {
            return false;
        }
        if (clientId.equals(properties.getPlatformClientId())) {
            return true;
        }
        String prefixes = properties.getPlatformClientPrefix();
        if (!hasText(prefixes)) {
            return false;
        }
        for (String prefix : prefixes.split(",")) {
            if (hasText(prefix) && clientId.startsWith(prefix.trim())) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> ok() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "success");
        return result;
    }

    private boolean nullSafeEquals(String expected, String actual) {
        return expected == null ? actual == null : expected.equals(actual);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

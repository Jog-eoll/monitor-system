package com.monitorplatform.forward.controller;

import com.monitorplatform.forward.config.MqttDispatchProperties;
import com.monitorplatform.forward.feign.DeviceFeignClient;
import com.monitorplatform.mqtt.core.dto.MqttAclRequest;
import com.monitorplatform.mqtt.core.dto.MqttAclResponse;
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
 * MQTT broker HTTP auth / ACL / lifecycle webhook adapter.
 * <p>
 * 平台侧统一对外暴露三个回调端点供 EMQX 5 调用：
 * <ul>
 *   <li>/mqtt/auth — 连接时鉴权（保留）</li>
 *   <li>/mqtt/acl  — 发布/订阅时鉴权（新增）</li>
 *   <li>/mqtt/webhook — 连接/断开事件通知（保留）</li>
 * </ul>
 * 平台客户端（monitor-forward 等）仅允许向 down/# 发布、向 up/# 订阅；
 * 设备客户端仅允许向自己的 up/# 发布、向自己的 down/# 订阅。
 * 空密码已被拒绝：平台客户端和设备客户端都必须显式配置用户名/密码。
 * </p>
 */
@Slf4j
@RestController
public class MqttAccessController {

    private static final String STATUS_OFFLINE = "离线";

    /** Topic 段数：tenantId/siteId/deviceId/direction/messageType */
    private static final int TOPIC_SEGMENT_COUNT = 5;

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

    @PostMapping({"/mqtt/acl", "/tool/mqtt/acl"})
    public ResponseEntity<Map<String, Object>> acl(@RequestBody(required = false) MqttAclRequest request) {
        if (request == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String clientId = trim(request.normalizedClientId());
        String username = trim(request.getUsername());
        String action = trim(request.getAction());
        String topic = trim(request.getTopic());

        if (!hasText(clientId) || !hasText(action) || !hasText(topic)) {
            log.warn("[MQTT-ACL] 参数不足: clientId={}, action={}, topic={}", clientId, action, topic);
            return buildAclResponse(MqttAclResponse.deny("参数不足"));
        }

        MqttAclResponse decision = isPlatformClient(clientId)
                ? evaluatePlatformAcl(clientId, username, action, topic)
                : evaluateDeviceAcl(clientId, username, action, topic);

        if (decision.isAllowed()) {
            log.debug("[MQTT-ACL] 允许: clientId={}, action={}, topic={}", clientId, action, topic);
        } else {
            log.warn("[MQTT-ACL] 拒绝: clientId={}, action={}, topic={}, reason={}",
                    clientId, action, topic, decision.getReason());
        }
        return buildAclResponse(decision);
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

    // ==================== ACL 决策 ====================

    /**
     * 平台客户端 ACL：仅允许向 down/# 发布、向 up/# 订阅。
     */
    private MqttAclResponse evaluatePlatformAcl(String clientId, String username,
                                                String action, String topic) {
        String direction = extractDirection(topic);
        if (direction == null) {
            return MqttAclResponse.deny("平台客户端 Topic 格式不合法: " + topic);
        }

        String normalizedAction = action.toLowerCase();
        if ("publish".equals(normalizedAction)) {
            if (!"down".equals(direction)) {
                return MqttAclResponse.deny("平台客户端仅允许发布到 down/#, 拒绝 topic=" + topic);
            }
            return MqttAclResponse.allow(null, null, null);
        }
        if ("subscribe".equals(normalizedAction)) {
            if (!"up".equals(direction)) {
                return MqttAclResponse.deny("平台客户端仅允许订阅 up/#, 拒绝 topic=" + topic);
            }
            return MqttAclResponse.allow(null, null, null);
        }
        return MqttAclResponse.deny("不支持的 ACL action: " + action);
    }

    /**
     * 设备客户端 ACL：仅允许发布到自己的 up/#、订阅自己的 down/#。
     */
    private MqttAclResponse evaluateDeviceAcl(String clientId, String username,
                                              String action, String topic) {
        String[] segments = topic.split("/");
        if (segments.length < TOPIC_SEGMENT_COUNT) {
            return MqttAclResponse.deny("设备客户端 Topic 格式不合法: " + topic);
        }
        // segments[0] 为 ""，从 segments[1] 开始
        String tenantId = segments[1];
        String siteId = segments[2];
        String topicDeviceId = segments[3];
        String direction = segments[4];

        if (!clientId.equals(topicDeviceId)) {
            return MqttAclResponse.deny("设备仅允许访问自己的 Topic, clientId=" + clientId
                    + " topicDeviceId=" + topicDeviceId);
        }

        String normalizedAction = action.toLowerCase();
        if ("publish".equals(normalizedAction)) {
            if (!"up".equals(direction)) {
                return MqttAclResponse.deny("设备仅允许发布到 up/#, 拒绝 topic=" + topic);
            }
            return MqttAclResponse.allow(tenantId, siteId, topicDeviceId);
        }
        if ("subscribe".equals(normalizedAction)) {
            if (!"down".equals(direction)) {
                return MqttAclResponse.deny("设备仅允许订阅 down/#, 拒绝 topic=" + topic);
            }
            return MqttAclResponse.allow(tenantId, siteId, topicDeviceId);
        }
        return MqttAclResponse.deny("不支持的 ACL action: " + action);
    }

    private String extractDirection(String topic) {
        if (!hasText(topic)) {
            return null;
        }
        String[] segments = topic.split("/");
        if (segments.length < TOPIC_SEGMENT_COUNT) {
            return null;
        }
        return segments[4];
    }

    private ResponseEntity<Map<String, Object>> buildAclResponse(MqttAclResponse decision) {
        if (decision.isAllowed()) {
            return ResponseEntity.ok(decision.toResultMap());
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(decision.toResultMap());
    }

    // ==================== 认证逻辑 ====================

    private boolean verifyPlatformCredential(String username, String password) {
        if (!hasText(properties.getMqttUsername())) {
            log.warn("[MQTT-AUTH] 平台未配置用户名,拒绝接入");
            return false;
        }
        boolean ok = properties.getMqttUsername().equals(username)
                && nullSafeEquals(properties.getMqttPassword(), password);
        if (!ok) {
            log.warn("[MQTT-AUTH] 平台用户名/密码不匹配: username={}", username);
        }
        return ok;
    }

    private boolean verifyDeviceCredential(String clientId, String username, String password) {
        String expected = trim(properties.getDeviceDefaultPassword());
        if (!hasText(expected)) {
            log.warn("[MQTT-AUTH] 设备默认密码未配置，拒绝所有设备接入");
            return false;
        }
        boolean ok = expected.equals(password);
        if (!ok) {
            log.warn("[MQTT-AUTH] 设备密码不匹配: clientId={}, username={}", clientId, username);
        }
        return ok;
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

package com.monitorplatform.ukey.websocket;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.common.websocket.WebSocketPushConstants;
import com.monitorplatform.common.websocket.WebSocketPushPublisher;
import com.monitorplatform.ukey.entity.UkeyCertificate;
import com.monitorplatform.ukey.feign.RoleFeignClient;
import com.monitorplatform.ukey.service.UkeyCertificateService;
import com.monitorplatform.ukey.service.VAuthAuthServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UKey 状态推送服务
 * 负责构建 UKey 状态 JSON 并通过 WebSocket 广播给所有前端客户端。
 * 在以下事件发生时调用 pushCurrentStatus()：
 *   1. POST /cert/online  客户端上报 ONLINE/OFFLINE
 *   2. POST /cert/disconnect  客户端断开
 *   3. POST /cert/edit  证书状态变更为 REVOKED/LOST
 *   4. 服务关闭时（@PreDestroy）推送 SERVICE_DOWN 通知
 */
@Slf4j
@Service
public class UkeyStatusPushService {

    @Resource
    private UkeyCertificateService ukeyCertificateService;

    @Resource
    private VAuthAuthServerService vAuthAuthServerService;

    @Resource
    private WebSocketPushPublisher webSocketPushPublisher;

    @Resource
    private RoleFeignClient roleFeignClient;

    /** 心跳超时阈值（与 Controller 保持一致） */
    @Value("${client.heartbeat.timeout-seconds:15}")
    private int heartbeatTimeoutSeconds;

    /**
     * 构建当前 UKey 状态的 JSON 字符串（不推送，供 onOpen 初始快照使用）
     *
     * @return JSON 字符串，无法构建时返回 null
     */
    public String buildStatusJson() {
        try {
            long t0 = System.currentTimeMillis();
            UkeyCertificate onlineCert = ukeyCertificateService.getOnlineUkey();
            long t1 = System.currentTimeMillis();
            boolean serverOnline = vAuthAuthServerService.isServerUkeyOnline();
            long t2 = System.currentTimeMillis();
            boolean clientServiceOnline = isClientAlive(onlineCert);
            log.info("[buildStatusJson 耗时] getOnlineUkey: {}ms, isServerUkeyOnline: {}ms", t1 - t0, t2 - t1);

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "UKEY_STATUS");
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("serverUkeyOnline", serverOnline);
            payload.put("clientServiceOnline", clientServiceOnline);

            if (onlineCert == null) {
                payload.put("onlineStatus", "OFFLINE");
                payload.put("certSerialNo", null);
                payload.put("displayName", null);
                payload.put("username", null);
                payload.put("userBound", false);
                payload.put("lastAuthTime", null);
                payload.put("boundClientId", null);
                List<String> reasons = new ArrayList<>();
                if (!serverOnline) reasons.add("SERVER_UKEY_REMOVED");
                if (!clientServiceOnline) reasons.add("CLIENT_SERVICE_DOWN");
                reasons.add("CLIENT_UKEY_REMOVED");
                payload.put("forceLogout", true);
                payload.put("forceLogoutReasons", reasons);
            } else {
                String certStatus = onlineCert.getCertStatus();
                List<String> reasons = new ArrayList<>();
                if (!serverOnline)                reasons.add("SERVER_UKEY_REMOVED");
                if (!clientServiceOnline)          reasons.add("CLIENT_SERVICE_DOWN");
                if ("REVOKED".equals(certStatus)) reasons.add("CERT_REVOKED");
                if ("LOST".equals(certStatus))    reasons.add("CERT_LOST");
                boolean forceLogout = !reasons.isEmpty();

                payload.put("onlineStatus", onlineCert.getOnlineStatus());
                payload.put("certSerialNo", onlineCert.getCertSerialNo());
                payload.put("displayName", onlineCert.getDisplayName() != null
                        ? onlineCert.getDisplayName() : onlineCert.getCertSerialNo());
                appendUserBinding(payload, onlineCert.getCertSerialNo());
                payload.put("lastAuthTime", onlineCert.getLastAuthTime() != null
                        ? onlineCert.getLastAuthTime().toString() : null);
                payload.put("boundClientId", onlineCert.getBoundClientId());
                payload.put("forceLogout", forceLogout);
                payload.put("forceLogoutReasons", reasons);
            }

            return JSON.toJSONString(payload);
        } catch (Exception e) {
            log.error("[UkeyPush] 构建状态 JSON 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建并广播当前 UKey 状态给所有已连接的前端
     * 在状态变化事件发生后调用
     */
    public void pushCurrentStatus() {
        Object statusObj = buildStatusJson();
        if (statusObj == null) {
            log.warn("[UkeyPush] 状态 JSON 构建失败，跳过推送");
            return;
        }
        log.info("[UkeyPush] 推送 UKey 状态变更");
        webSocketPushPublisher.publish(
                WebSocketPushConstants.TOPIC_UKEY_STATUS,
                WebSocketPushConstants.TYPE_UKEY_STATUS,
                statusObj);
    }

    /**
     * 服务关闭前推送 SERVICE_DOWN 通知，让前端将 UKey 状态重置为未就绪
     * 注意：此方法不依赖数据库或其他 bean，直接构建固定 payload 广播，保证关闭时可靠执行
     */
    @PreDestroy
    public void pushServiceShutdown() {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "UKEY_STATUS");
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("serverUkeyOnline", false);
            payload.put("clientServiceOnline", false);
            payload.put("onlineStatus", "OFFLINE");
            payload.put("certSerialNo", null);
            payload.put("displayName", null);
            payload.put("username", null);
            payload.put("userBound", false);
            payload.put("lastAuthTime", null);
            payload.put("boundClientId", null);
            payload.put("forceLogout", true);
            payload.put("forceLogoutReasons", Arrays.asList("SERVICE_DOWN"));
            log.info("[UkeyPush] 服务关闭，推送 SERVICE_DOWN");
            webSocketPushPublisher.publish(
                    WebSocketPushConstants.TOPIC_UKEY_STATUS,
                    WebSocketPushConstants.TYPE_UKEY_STATUS,
                    payload);
        } catch (Exception e) {
            log.warn("[UkeyPush] 服务关闭推送失败（不影响关闭流程）: {}", e.getMessage());
        }
    }

    /**
     * 判断客户端服务是否存活（基于心跳时间，与 Controller.isClientAlive 逻辑一致）
     */
    private boolean isClientAlive(UkeyCertificate cert) {
        if (cert == null) return false;
        LocalDateTime lastBeat = cert.getLastHeartbeatTime();
        if (lastBeat == null) return false;
        long seconds = java.time.Duration.between(lastBeat, LocalDateTime.now()).getSeconds();
        return seconds <= heartbeatTimeoutSeconds;
    }

    private void appendUserBinding(Map<String, Object> payload, String certSerialNo) {
        payload.put("username", null);
        payload.put("userBound", false);
        if (certSerialNo == null || certSerialNo.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, Object> result = roleFeignClient.getAuthContextByUkeyId(certSerialNo);
            int code = safeInt(result == null ? null : result.get("code"), 500);
            if (code != 200) {
                log.warn("[UkeyPush] ukey user not bound or unavailable, certSerialNo={}, code={}",
                        certSerialNo, code);
                return;
            }

            Map<String, Object> data = extractData(result);
            String username = safeStr(data == null ? null : data.get("username"));
            if (username.isEmpty()) {
                log.warn("[UkeyPush] ukey user binding has empty username, certSerialNo={}", certSerialNo);
                return;
            }

            payload.put("username", username);
            payload.put("userBound", true);
        } catch (Exception e) {
            log.warn("[UkeyPush] query ukey user failed, certSerialNo={}, error={}",
                    certSerialNo, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> result) {
        Object data = result == null ? null : result.get("data");
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return null;
    }

    private int safeInt(Object value, int defaultVal) {
        if (value == null) {
            return defaultVal;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private String safeStr(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }
}

package com.monitorplatform.ukey.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * UKey 在线状态 WebSocket 处理器 (STOMP模式)
 * 
 * 当 UKey 状态发生变化时，由 UkeyStatusPushService 调用推送方法主动推送给所有订阅 /topic/ukey-status 的前端。
 *
 * 消息格式（JSON）：
 * {
 *   "type": "UKEY_STATUS",
 *   "onlineStatus": "ONLINE|OFFLINE",
 *   "certSerialNo": "...",
 *   "displayName": "...",
 *   "forceLogout": false,
 *   "forceLogoutReasons": [],
 *   "serverUkeyOnline": true,
 *   "clientServiceOnline": true,
 *   "timestamp": 1234567890
 * }
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "monitor.websocket.enabled", havingValue = "true")
public class UkeyStatusWebSocketHandler {

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 广播UKey状态消息给所有订阅 /topic/ukey-status 的客户端
     *
     * @param message 消息内容（JSON字符串或对象）
     */
    public void broadcast(Object message) {
        try {
            log.info("[UkeyWS] 广播UKey状态消息到 /topic/ukey-status");
            messagingTemplate.convertAndSend("/topic/ukey-status", message);
        } catch (Exception e) {
            log.error("[UkeyWS] 广播UKey状态消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送消息给指定用户
     *
     * @param userId  用户ID
     * @param message 消息内容
     */
    public void sendToUser(String userId, Object message) {
        try {
            log.info("[UkeyWS] 发送UKey状态消息给用户: {}", userId);
            messagingTemplate.convertAndSendToUser(userId, "/topic/ukey-status", message);
        } catch (Exception e) {
            log.error("[UkeyWS] 发送消息给用户失败: userId={}, error={}", userId, e.getMessage(), e);
        }
    }
}

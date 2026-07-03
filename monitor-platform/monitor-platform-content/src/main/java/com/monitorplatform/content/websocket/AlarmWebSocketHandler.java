package com.monitorplatform.content.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 告警WebSocket处理器 (STOMP模式)
 * 用于向前端实时推送告警信息
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "monitor.websocket.enabled", havingValue = "true")
public class AlarmWebSocketHandler {

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 广播消息给所有订阅 /topic/alarm 的客户端
     */
    public void broadcast(Object message) {
        try {
            log.info("[ContentAlarmWS] 广播告警消息到 /topic/alarm");
            messagingTemplate.convertAndSend("/topic/alarm", message);
        } catch (Exception e) {
            log.error("[ContentAlarmWS] 广播告警消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送消息给指定用户
     */
    public void sendToUser(String userId, Object message) {
        try {
            log.info("[ContentAlarmWS] 发送告警消息给用户: {}", userId);
            messagingTemplate.convertAndSendToUser(userId, "/topic/alarm", message);
        } catch (Exception e) {
            log.error("[ContentAlarmWS] 发送消息给用户失败: userId={}, error={}", userId, e.getMessage(), e);
        }
    }
}

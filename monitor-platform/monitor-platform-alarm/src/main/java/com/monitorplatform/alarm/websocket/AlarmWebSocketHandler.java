package com.monitorplatform.alarm.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 告警 WebSocket 处理器 (STOMP模式)
 * 
 * 当新告警触发时，由 AlarmPushService 调用推送方法主动推送给所有订阅 /topic/alarm 的前端。
 *
 * 消息格式（JSON）：
 * {
 *   "type": "ALARM_EVENT",
 *   "id": 101,
 *   "alarmType": "content_violation",
 *   "alarmLevel": "critical",
 *   "chainId": 22,
 *   "deviceId": "PUB-GW-001",
 *   "deviceName": "发布网关01",
 *   "violationType": "sensitive_word",
 *   "violationDetail": "xxx",
 *   "alarmTime": "2026-03-24 10:00:00",
 *   "handleStatus": "pending",
 *   "longitude": "116.397128",
 *   "latitude": "39.916527",
 *   "timestamp": 1234567890
 * }
 * @author zqr
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "monitor.websocket.enabled", havingValue = "true")
public class AlarmWebSocketHandler {

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 广播告警消息给所有订阅 /topic/alarm 的客户端
     *
     * @param message 消息内容（JSON字符串或对象）
     */
    public void broadcast(Object message) {
        try {
            log.info("[AlarmWS] 广播告警消息到 /topic/alarm");
            messagingTemplate.convertAndSend("/topic/alarm", message);
        } catch (Exception e) {
            log.error("[AlarmWS] 广播告警消息失败: {}", e.getMessage(), e);
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
            log.info("[AlarmWS] 发送告警消息给用户: {}", userId);
            messagingTemplate.convertAndSendToUser(userId, "/topic/alarm", message);
        } catch (Exception e) {
            log.error("[AlarmWS] 发送消息给用户失败: userId={}, error={}", userId, e.getMessage(), e);
        }
    }
}

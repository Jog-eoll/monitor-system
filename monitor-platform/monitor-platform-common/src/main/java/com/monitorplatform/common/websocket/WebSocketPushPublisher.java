package com.monitorplatform.common.websocket;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * 发布 WebSocket 推送事件到 Redis。
 */
@Slf4j
@Component
public class WebSocketPushPublisher {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${spring.application.name:unknown-service}")
    private String applicationName;

    public void publish(String topic, String type, Object payload) {
        if (!StringUtils.hasText(topic)) {
            log.warn("[WebSocketPush] topic 为空，跳过推送: type={}", type);
            return;
        }
        try {
            WebSocketPushMessage message = WebSocketPushMessage.of(topic, type, payload, applicationName);
            stringRedisTemplate.convertAndSend(WebSocketPushConstants.REDIS_CHANNEL, JSON.toJSONString(message));
            log.info("[WebSocketPush] 已发布推送事件: topic={}, type={}", topic, type);
        } catch (Exception e) {
            log.warn("[WebSocketPush] 发布推送事件失败，不影响主流程: topic={}, type={}, error={}",
                    topic, type, e.getMessage());
        }
    }
}

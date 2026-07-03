package com.monitorplatform.websocket.listener;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.common.websocket.WebSocketPushMessage;
import com.monitorplatform.websocket.service.WebSocketDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * Redis 推送事件监听器。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "monitor.websocket.enabled", havingValue = "true")
public class WebSocketPushMessageListener implements MessageListener {

    @Resource
    private WebSocketDispatchService webSocketDispatchService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            log.warn("[WebSocketRedis] 收到空消息，已忽略");
            return;
        }

        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            WebSocketPushMessage pushMessage = JSON.parseObject(body, WebSocketPushMessage.class);
            webSocketDispatchService.dispatch(pushMessage);
        } catch (Exception e) {
            log.warn("[WebSocketRedis] 解析或转发消息失败: body={}, error={}", body, e.getMessage());
        }
    }
}

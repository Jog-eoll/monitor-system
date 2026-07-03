package com.monitorplatform.websocket.service.impl;

import com.monitorplatform.common.websocket.WebSocketPushMessage;
import com.monitorplatform.websocket.service.WebSocketDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * Sends Redis push events to STOMP clients.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "monitor.websocket.enabled", havingValue = "true")
public class WebSocketDispatchServiceImpl implements WebSocketDispatchService {

    private static final String ALLOWED_TOPIC_PREFIX = "/topic/";

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public void dispatch(WebSocketPushMessage message) {
        if (message == null) {
            log.warn("[WebSocketDispatch] push message is empty, ignored");
            return;
        }

        String topic = message.getTopic();
        if (!StringUtils.hasText(topic)) {
            log.warn("[WebSocketDispatch] topic is empty, ignored: type={}, source={}",
                    message.getType(), message.getSourceService());
            return;
        }
        if (!topic.startsWith(ALLOWED_TOPIC_PREFIX)) {
            log.warn("[WebSocketDispatch] invalid topic rejected: topic={}, type={}, source={}",
                    topic, message.getType(), message.getSourceService());
            return;
        }

        try {
            messagingTemplate.convertAndSend(topic, message.getPayload());
            log.info("[WebSocketDispatch] message sent: topic={}, type={}, source={}",
                    topic, message.getType(), message.getSourceService());
        } catch (Exception e) {
            log.warn("[WebSocketDispatch] message send failed: topic={}, type={}, error={}",
                    topic, message.getType(), e.getMessage());
        }
    }
}

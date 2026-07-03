package com.monitorplatform.ukey.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import javax.annotation.Resource;

/**
 * STOMP 订阅后立即推送当前 UKey 快照，避免前端只连上但等不到首帧状态。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "monitor.websocket.enabled", havingValue = "true")
public class UkeyStatusSubscribeListener {

    private static final String UKEY_STATUS_TOPIC = "/topic/ukey-status";

    @Resource
    private UkeyStatusPushService ukeyStatusPushService;

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination) || !UKEY_STATUS_TOPIC.equals(destination)) {
            return;
        }

        log.info("[UkeyWS] STOMP client subscribed {}, push current snapshot, sessionId={}",
                destination, accessor.getSessionId());
        ukeyStatusPushService.pushCurrentStatus();
    }
}

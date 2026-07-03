package com.monitorplatform.common.websocket;

import lombok.Data;

import java.io.Serializable;

/**
 * 微服务发布给 WebSocket 服务的统一推送消息。
 */
@Data
public class WebSocketPushMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * STOMP 目标主题，例如 /topic/alarm。
     */
    private String topic;

    /**
     * 事件类型，例如 ALARM_FLAG。
     */
    private String type;

    /**
     * 业务数据。
     */
    private Object payload;

    /**
     * 来源服务名。
     */
    private String sourceService;

    /**
     * 事件时间戳。
     */
    private Long timestamp;

    public static WebSocketPushMessage of(String topic, String type, Object payload, String sourceService) {
        WebSocketPushMessage message = new WebSocketPushMessage();
        message.setTopic(topic);
        message.setType(type);
        message.setPayload(payload);
        message.setSourceService(sourceService);
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
}

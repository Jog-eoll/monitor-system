package com.monitorplatform.common.websocket;

/**
 * WebSocket 推送通道常量。
 */
public final class WebSocketPushConstants {

    public static final String REDIS_CHANNEL = "monitor:websocket:push";

    public static final String TOPIC_ALARM = "/topic/alarm";

    public static final String TOPIC_UKEY_STATUS = "/topic/ukey-status";

    public static final String TYPE_ALARM_FLAG = "ALARM_FLAG";

    public static final String TYPE_VIOLATION_ALARM = "VIOLATION_ALARM";

    public static final String TYPE_UKEY_STATUS = "UKEY_STATUS";

    private WebSocketPushConstants() {
    }
}

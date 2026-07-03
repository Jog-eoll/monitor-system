package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * MQTT 消息信封 —— 所有上行/下行消息的统一包装层。
 * <p>
 * Topic 格式：/{tenantId}/{siteId}/{deviceId}/{direction}/{messageType}
 * <ul>
 *   <li>direction: up（设备→平台）或 down（平台→设备）</li>
 *   <li>messageType: proxy-command / reply / heartbeat / discovery</li>
 * </ul>
 * </p>
 */
@Data
public class MqttEnvelope implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息 ID（UUID，用于幂等和回执关联） */
    private String messageId;

    /** 消息类型：PROXY_COMMAND / REPLY / HEARTBEAT / DISCOVERY */
    private String messageType;

    /** 租户 ID */
    private String tenantId;

    /** 站点 ID */
    private String siteId;

    /** 设备 ID（发送方或接收方） */
    private String deviceId;

    /** 设备类型：publish_gateway / terminal_encrypt_gateway / publish_server / info_board */
    private String deviceType;

    /** 时间戳（epoch millis） */
    private Long timestamp;

    /** 消息体（JSON 字符串，由具体消息类型决定结构） */
    private String payload;
}

package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 回执消息 —— 现场网关执行完命令后上行回报给平台。
 */
@Data
public class MqttReplyMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SCHEMA_VERSION = "1.0";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String ERROR_EXECUTION_FAILED = "EXECUTION_FAILED";
    public static final String ERROR_REJECTED = "REJECTED";

    private String schemaVersion = SCHEMA_VERSION;

    /** 原始命令的 messageId（用于关联） */
    private String commandMessageId;

    /** 执行状态：SUCCESS / FAILED / PROCESSING / REJECTED */
    private String status;

    /** 消息描述 */
    private String message;

    private String errorCode;

    private Integer progress;

    /** 返回数据（可选） */
    private Map<String, Object> data;

    /** 网关设备 ID */
    private String gatewayDeviceId;

    /** 时间戳（epoch millis） */
    private Long timestamp;

    public static MqttReplyMessage success(String commandMessageId, String gatewayDeviceId, Map<String, Object> data) {
        MqttReplyMessage reply = new MqttReplyMessage();
        reply.commandMessageId = commandMessageId;
        reply.gatewayDeviceId = gatewayDeviceId;
        reply.status = STATUS_SUCCESS;
        reply.message = "执行成功";
        reply.data = data;
        reply.progress = 100;
        reply.timestamp = System.currentTimeMillis();
        return reply;
    }

    public static MqttReplyMessage failed(String commandMessageId, String gatewayDeviceId, String errorMsg) {
        MqttReplyMessage reply = new MqttReplyMessage();
        reply.commandMessageId = commandMessageId;
        reply.gatewayDeviceId = gatewayDeviceId;
        reply.status = STATUS_FAILED;
        reply.errorCode = ERROR_EXECUTION_FAILED;
        reply.message = errorMsg;
        reply.timestamp = System.currentTimeMillis();
        return reply;
    }

    public static MqttReplyMessage processing(String commandMessageId, String gatewayDeviceId) {
        MqttReplyMessage reply = new MqttReplyMessage();
        reply.commandMessageId = commandMessageId;
        reply.gatewayDeviceId = gatewayDeviceId;
        reply.status = STATUS_PROCESSING;
        reply.message = "命令已接收，正在执行";
        reply.timestamp = System.currentTimeMillis();
        return reply;
    }

    public static MqttReplyMessage rejected(String commandMessageId, String gatewayDeviceId, String reason) {
        MqttReplyMessage reply = new MqttReplyMessage();
        reply.commandMessageId = commandMessageId;
        reply.gatewayDeviceId = gatewayDeviceId;
        reply.status = STATUS_REJECTED;
        reply.errorCode = ERROR_REJECTED;
        reply.message = reason;
        reply.timestamp = System.currentTimeMillis();
        return reply;
    }
}

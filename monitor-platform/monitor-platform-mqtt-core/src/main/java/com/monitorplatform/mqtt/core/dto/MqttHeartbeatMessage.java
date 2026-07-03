package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 心跳消息 —— 现场网关定期上行，平台据此维护设备在线状态。
 */
@Data
public class MqttHeartbeatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网关设备 ID */
    private String deviceId;

    /** 设备类型 */
    private String deviceType;

    /** 在线状态：ONLINE / OFFLINE */
    private String status;

    /** 时间戳（epoch millis） */
    private Long timestamp;

    /** 附加信息（如版本号、运行时长等） */
    private Map<String, Object> metadata;

    public static MqttHeartbeatMessage online(String deviceId, String deviceType, Map<String, Object> metadata) {
        MqttHeartbeatMessage hb = new MqttHeartbeatMessage();
        hb.deviceId = deviceId;
        hb.deviceType = deviceType;
        hb.status = "ONLINE";
        hb.timestamp = System.currentTimeMillis();
        hb.metadata = metadata;
        return hb;
    }
}

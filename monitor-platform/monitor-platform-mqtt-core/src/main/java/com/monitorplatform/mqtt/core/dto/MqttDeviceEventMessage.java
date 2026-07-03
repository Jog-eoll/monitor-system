package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Device event payload carried by up/event.
 */
@Data
public class MqttDeviceEventMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;

    private String deviceType;

    private String eventType;

    private String eventCode;

    private String level;

    private String message;

    private Long timestamp;

    private Map<String, Object> data;
}

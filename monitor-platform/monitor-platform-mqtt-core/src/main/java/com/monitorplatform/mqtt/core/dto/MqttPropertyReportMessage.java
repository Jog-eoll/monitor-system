package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Device property snapshot carried by up/property.
 */
@Data
public class MqttPropertyReportMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;

    private String deviceType;

    private Long timestamp;

    private Map<String, Object> properties;
}

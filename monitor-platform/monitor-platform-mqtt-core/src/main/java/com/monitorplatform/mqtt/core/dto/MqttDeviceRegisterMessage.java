package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Device active registration payload carried by up/register.
 */
@Data
public class MqttDeviceRegisterMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serviceName;

    private String instanceId;

    private String deviceId;

    private String deviceType;

    private String host;

    private Integer port;

    private String macAddress;

    private String location;

    private String version;

    private String manufacturer;

    private String model;

    private String remark;

    private Long timestamp;

    private Map<String, Object> metadata;
}

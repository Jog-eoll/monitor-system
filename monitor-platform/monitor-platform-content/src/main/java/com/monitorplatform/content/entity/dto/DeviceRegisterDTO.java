package com.monitorplatform.content.entity.dto;

import lombok.Data;

/**
 * Request body compatible with monitor-device /device/registry/auto-register.
 */
@Data
public class DeviceRegisterDTO {

    private String serviceName;

    private String instanceId;

    private String host;

    private Integer port;

    private String macAddress;

    private String deviceType;

    private String location;

    private String version;

    private String manufacturer;

    private String model;

    private String remark;
}

package com.monitorplatform.registry.client.dto;

import lombok.Data;

@Data
public class ServiceRegisterRequest {
    private String clientId;
    private String serviceName;
    private String instanceId;
    private String host;
    private Integer port;
    private String macAddress;
    private String deviceType;
    // 新增字段
    private String location;
    private String version;
    private String manufacturer;
    private String model;
    private String remark;
}

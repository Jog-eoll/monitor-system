package com.monitorplatform.registry.client.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ServiceInstance {
    private Long id;
    private String clientId;
    private String serviceName;
    private String instanceId;
    private String host;
    private Integer port;
    private String macAddress;
    private String deviceType;
    private String status;
    private Integer weight;
    private String clusterName;
    private LocalDateTime registerTime;
    private LocalDateTime lastHeartbeatTime;
}

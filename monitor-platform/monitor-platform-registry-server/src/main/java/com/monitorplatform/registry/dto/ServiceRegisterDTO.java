package com.monitorplatform.registry.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ServiceRegisterDTO {
    private String clientId;

    @NotBlank(message = "服务名称不能为空")
    private String serviceName;
    
    @NotBlank(message = "实例ID不能为空")
    private String instanceId;
    
    @NotBlank(message = "主机地址不能为空")
    private String host;
    
    @NotNull(message = "端口不能为空")
    private Integer port;
    
    private String macAddress;
    
    private String deviceType;
    
    private Integer weight = 1;
    
    private String clusterName = "default";
}

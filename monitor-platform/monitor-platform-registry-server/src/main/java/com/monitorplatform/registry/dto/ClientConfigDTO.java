package com.monitorplatform.registry.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ClientConfigDTO {
    private String clientId;
    private String serviceName;
    private Map<String, Object> config;
    private Boolean enabled = true;
}

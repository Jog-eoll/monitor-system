package com.monitorplatform.registry.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class GatewayDeployLogRequest {
    private String deviceId;
    private String sourceType;
    private String level;

    @NotBlank(message = "message cannot be blank")
    private String message;

    private Map<String, Object> context;
    private LocalDateTime reportTime;

    public void setLogLevel(String logLevel) {
        this.level = logLevel;
    }
}

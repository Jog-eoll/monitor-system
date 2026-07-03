package com.monitorplatform.registry.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class GatewayRegisterResponse {
    private String deviceId;
    private String ip;
    private Integer port;
    private String role;
    private String certSerialNo;
    private String ukeySn;
    private Boolean certificateValid;
    private Boolean certificateBound;
    private String boundClientId;
    private Long configVersion;
    private Map<String, Object> config;
    private LocalDateTime registerTime;
}

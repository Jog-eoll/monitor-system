package com.monitorplatform.registry.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class GatewaySelfTestRequest {
    private Boolean encryptSampleOk;
    private Boolean decryptSampleOk;
    private Boolean platformHandshakeOk;
    private Boolean ukeyStatusOk;
    private Map<String, Object> detail;
    private String errorMessage;
    private LocalDateTime reportTime;
}

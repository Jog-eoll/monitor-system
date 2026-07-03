package com.monitorplatform.registry.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GatewaySelfTestResponse {
    private String deviceId;
    private String overallStatus;
    private Boolean encryptSampleOk;
    private Boolean decryptSampleOk;
    private Boolean platformHandshakeOk;
    private Boolean ukeyStatusOk;
    private LocalDateTime reportTime;
}

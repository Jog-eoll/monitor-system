package com.monitorplatform.alarm.entity.vo;

import lombok.Data;

@Data
public class GatewayActionResultVO {
    private Boolean success;
    private String message;
    private Long alarmId;
    private Long chainId;
    private String infoBoardIp;
    private Integer infoBoardPort;
    private String terminalGatewayIp;
    private String violationFile;
    private Boolean cleanupViolationContent;
}

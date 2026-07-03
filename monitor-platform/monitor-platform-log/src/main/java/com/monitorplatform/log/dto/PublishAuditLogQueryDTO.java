package com.monitorplatform.log.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.monitorplatform.common.entity.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class PublishAuditLogQueryDTO extends PageDTO {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endTime;

    private Long chainId;
    private String chainCode;
    private String sourceIp;
    private String boardIp;
    private String contentType;
    private String status;
    private String complianceStatus;
    private String operatorName;
    private String clientIp;
    private String ip;
    private String type;
    private String logType;
    private String actType;
    private String operationType;
    private String actModule;
    private String operationModule;
    private String resultStatus;
    private String keyword;
}

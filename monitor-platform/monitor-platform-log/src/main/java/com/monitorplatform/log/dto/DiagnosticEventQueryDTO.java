package com.monitorplatform.log.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.monitorplatform.common.entity.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class DiagnosticEventQueryDTO extends PageDTO {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endTime;

    private String eventType;
    private String eventLevel;
    private String stage;
    private String serviceName;
    private String traceId;
    private Long chainId;
    private String contentId;
    private String sourceIp;
    private String boardIp;
    private String operatorId;
    private String operatorName;
    private String ukeyId;
    private String certSerialNo;
    private String resultStatus;
    private String keyword;
}

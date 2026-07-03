package com.monitorplatform.common.log;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DiagnosticLogReport {

    private String traceId;
    private String eventType;
    private String eventLevel;
    private String stage;
    private String serviceName;
    private Long chainId;
    private String chainCode;
    private String contentId;
    private String sourceIp;
    private Integer sourcePort;
    private String boardIp;
    private Integer boardPort;
    private String operatorId;
    private String operatorName;
    private String clientIp;
    private String ukeyId;
    private String certSerialNo;
    private String certName;
    private String signStatus;
    private String verifyStatus;
    private String resultStatus;
    private String summary;
    private String errorCode;
    private String errorMessage;
    private String refTable;
    private String refId;
    private String detailJson;
    private String dedupKey;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime eventTime;
}

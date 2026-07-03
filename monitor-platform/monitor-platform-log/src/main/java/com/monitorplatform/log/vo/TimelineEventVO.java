package com.monitorplatform.log.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class TimelineEventVO {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime eventTime;

    private String source;
    private String eventType;
    private String eventLevel;
    private String stage;
    private String traceId;
    private Long chainId;
    private String chainCode;
    private String contentId;
    private String sourceIp;
    private String boardIp;
    private String operatorName;
    private String clientIp;
    private String certSerialNo;
    private String resultStatus;
    private String summary;
    private String errorMessage;
    private String refTable;
    private String refId;
    private Map<String, Object> detail;
}

package com.monitorplatform.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("diagnostic_event_log")
public class DiagnosticEventLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private String traceId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime eventTime;

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
    private Integer repeatCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime lastRepeatTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updateTime;
}

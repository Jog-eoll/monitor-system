package com.monitorplatform.log.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TimelineQueryDTO {

    private String traceId;
    private String contentId;
    private Long chainId;
    private String sourceIp;
    private String boardIp;
    private String certSerialNo;
    private String alarmId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endTime;

    private Integer limit;
}

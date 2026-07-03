package com.monitorplatform.content.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlarmRecordVO {
    private Long id;
    private String alarmType;
    private String alarmLevel;
    private Long chainId;
    private String boardIp;
    private Integer boardPort;
    private String deviceId;
    private String deviceName;
    private String contentId;
    private String violationType;
    private String violationDetail;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime alarmTime;
    private String handleStatus;
    private String handleOperator;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime handleTime;
    private String handleRemark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
    private String contentType;
    private String contentData;
    private String contentFileUrl;
    private String infoBoardIp;
    private Integer infoBoardPort;
    private String longitude;
    private String latitude;
}

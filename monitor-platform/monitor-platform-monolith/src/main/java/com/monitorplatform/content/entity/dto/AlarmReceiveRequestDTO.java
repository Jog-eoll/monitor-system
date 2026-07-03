package com.monitorplatform.content.entity.dto;

import lombok.Data;

@Data
public class AlarmReceiveRequestDTO {
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
    private String alarmTime;
}

package com.monitorplatform.alarm.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 接收告警DTO
 */
@Data
public class AlarmReceiveDTO {
    
    @NotBlank(message = "告警类型不能为空")
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
    
    @NotNull(message = "告警时间不能为空")
    private LocalDateTime alarmTime;
}

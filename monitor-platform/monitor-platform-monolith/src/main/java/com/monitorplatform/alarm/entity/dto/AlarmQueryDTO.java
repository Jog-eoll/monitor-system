package com.monitorplatform.alarm.entity.dto;

import lombok.Data;

/**
 * 告警查询DTO
 */
@Data
public class AlarmQueryDTO {
    
    private String alarmType;
    
    private String alarmLevel;
    
    private String deviceId;

    /** 情报板IP过滤，对应数据库 board_ip 字段 */
    private String infoBoardIp;
    
    private String handleStatus;
    
    private String startTime;
    
    private String endTime;
    
    private Integer pageNum = 1;
    
    private Integer pageSize = 10;
}

package com.monitorplatform.alarm.entity.dto;

import lombok.Data;

/**
 * 仪表盘概览DTO
 */
@Data
public class DashboardOverviewDTO {
    
    /**
     * 总报警数
     */
    private Long totalAlarms;
    
    /**
     * 总报警数较昨日变化百分比
     */
    private Double totalChangePercent;
    
    /**
     * 紧急告警数
     */
    private Long urgentAlarms;
    
    /**
     * 紧急告警数较昨日变化百分比
     */
    private Double urgentChangePercent;
    
    /**
     * 已处理数
     */
    private Long processedAlarms;
    
    /**
     * 已处理数较昨日变化百分比
     */
    private Double processedChangePercent;
    
    /**
     * 在线设备数
     */
    private Long onlineDevices;
    
    /**
     * 在线设备数较昨日变化百分比
     */
    private Double onlineDevicesChangePercent;
}

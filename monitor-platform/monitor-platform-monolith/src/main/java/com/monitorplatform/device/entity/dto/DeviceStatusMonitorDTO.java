package com.monitorplatform.device.entity.dto;

import lombok.Data;

/**
 * 设备状态监控DTO
 */
@Data
public class DeviceStatusMonitorDTO {
    
    /**
     * 设备类型
     */
    private String deviceType;
    
    /**
     * 设备类型显示名称
     */
    private String deviceTypeName;
    
    /**
     * 总数
     */
    private Integer totalCount;
    
    /**
     * 在线数
     */
    private Integer onlineCount;
    
    /**
     * 离线数
     */
    private Integer offlineCount;
    
    /**
     * 部分异常数（需升级等）
     */
    private Integer partialCount;
    
    /**
     * 状态标签（全部在线/部分异常等）
     */
    private String statusLabel;
}

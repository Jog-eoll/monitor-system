package com.monitorplatform.device.entity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备详情DTO（含监控数据）
 */
@Data
public class DeviceDetailDTO {
    
    // ========== 基本信息 ==========
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String deviceTypeLabel;
    private String ipAddress;
    private Integer port;
    /** MAC地址（发布网关等设备使用，格式: AA-BB-CC-DD-EE-FF） */
    private String mac;
    private String status;
    private String location;
    private Long regionId;
    private Double longitude;   // 经度
    private Double latitude;    // 纬度
    
    // ========== 硬件监控 ==========
    private Double diskUsage;
    private Double temperature;
    private Integer networkSpeed;  // KB/s
    
    // ========== 运行状态 ==========
    private Long uptime;  // 运行时长（秒）
    private String firmwareVersion;
    private String softwareVersion;
    private String manufacturer;
    private String model;
    
    // ========== 告警信息 ==========
    private Integer alarmCount;  // 告警次数
    private String lastAlarmTime;
    private String lastAlarmType;
    
    // ========== 时间信息 ==========
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastOnlineTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastHeartbeat;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    
    // ========== 扩展信息 ==========
    private String remark;
    
    /**
     * 设备特有属性（不同设备类型有不同属性）
     */
    private Map<String, Object> specificAttributes;
}

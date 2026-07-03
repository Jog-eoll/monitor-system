package com.monitorplatform.device.entity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一设备DTO（用于跨设备类型查询）
 */
@Data
public class UnifiedDeviceDTO {
    
    /**
     * 设备ID
     */
    private String deviceId;
    
    /**
     * 设备名称
     */
    private String deviceName;
    
    /**
     * 设备类型: publish_server/publish_gateway/terminal_encrypt_gateway/content_server/info_board
     */
    private String deviceType;
    
    /**
     * 设备类型显示名称
     */
    private String deviceTypeLabel;
    
    /**
     * IP地址
     */
    private String ipAddress;
    
    /**
     * 端口
     */
    private Integer port;

    /**
     * MAC地址（发布网关等设备使用，格式: AA-BB-CC-DD-EE-FF）
     */
    private String mac;
    
    /**
     * 状态: 在线/离线/告警/故障
     */
    private String status;
    
    /**
     * 位置
     */
    private String location;

    private Long regionId;
    
    /**
     * 经度
     */
    private Double longitude;
    
    /**
     * 纬度
     */
    private Double latitude;
    
    /**
     * 厂家标识（如 sigma/nova/colorlight，仅情报板有效）
     */
    private String manufacturer;

    /**
     * 固件/软件版本
     */
    private String version;
    
    /**
     * 最后在线时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastOnlineTime;
    
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 扩展信息（JSON字符串）
     */
    private String extra;
}

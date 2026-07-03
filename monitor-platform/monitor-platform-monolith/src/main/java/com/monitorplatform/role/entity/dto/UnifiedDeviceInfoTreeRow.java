package com.monitorplatform.role.entity.dto;

import lombok.Data;

/**
 * unified_device_info 拼区域设备树时的行数据。
 */
@Data
public class UnifiedDeviceInfoTreeRow {

    private Long id;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String ipAddress;
    /** 所属区域ID（关联 region.id） */
    private Long regionId;
}

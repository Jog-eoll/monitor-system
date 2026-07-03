package com.monitorplatform.device.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 统一设备心跳DTO
 */
@Data
public class DeviceHeartbeatDTO {
    
    @NotBlank(message = "设备ID不能为空")
    private String deviceId;
    
    /**
     * 设备状态: 在线/离线/正常/异常
     */
    private String status;
    
    /**
     * 扩展性能数据或属性
     */
    private Map<String, Object> extraData;
}

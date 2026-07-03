package com.monitorplatform.device.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 设备命令下发DTO
 */
@Data
public class DeviceCommandDTO {
    
    /**
     * 设备ID
     */
    @NotBlank(message = "设备ID不能为空")
    private String deviceId;
    
    /**
     * 设备类型: camera/gateway/server/terminal/monitor_client
     */
    @NotBlank(message = "设备类型不能为空")
    private String deviceType;
    
    /**
     * 命令类型: reboot/upgrade/config/blackscreen/block_traffic/restore
     */
    @NotBlank(message = "命令类型不能为空")
    private String commandType;
    
    /**
     * 命令参数
     */
    private Map<String, Object> params;
    
    /**
     * 操作人
     */
    private String operator;
    
    /**
     * 备注
     */
    private String remark;
}

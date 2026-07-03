package com.monitorplatform.device.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量操作DTO
 */
@Data
public class BatchOperationDTO {
    
    /**
     * 设备ID列表
     */
    @NotEmpty(message = "设备ID列表不能为空")
    private List<String> deviceIds;
    
    /**
     * 设备类型: camera/gateway/server/terminal/monitor_client
     */
    @NotNull(message = "设备类型不能为空")
    private String deviceType;
    
    /**
     * 操作类型: refresh_status/export/reboot/upgrade
     */
    @NotNull(message = "操作类型不能为空")
    private String operationType;
    
    /**
     * 操作参数（可选）
     */
    private String operationParam;
}

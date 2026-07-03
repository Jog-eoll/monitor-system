package com.gateway.device.protocol.model;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 设备级命令 —— 经 DeviceCommandFactory 拆分后的单台设备命令。
 */
@Data
@Builder
public class DeviceCommand {

    /**
     * 关联的批量子任务 ID
     */
    private String taskId;

    /**
     * 目标能力（替代旧 CommandType + requiredCapability 的双字段）
     */
    private DeviceCapability<?> capability;

    /**
     * 目标设备
     */
    private DeviceContext target;

    /**
     * 指令参数 —— 通过 {@code capability.paramsType()} 获知应构造的具体类型。
     */
    private CommandParams params;
}

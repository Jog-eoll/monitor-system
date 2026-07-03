package com.gateway.device.protocol.model;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 批量指令请求 —— 业务层提交的逻辑指令。
 */
@Data
@Builder
public class BatchCommandRequest {

    /**
     * 请求 ID（业务层生成，用于幂等）
     */
    private String requestId;

    /**
     * 目标能力
     */
    private DeviceCapability<?> capability;

    /**
     * 设备筛选条件
     */
    private DeviceSelector selector;

    /**
     * 指令参数 —— 通过 {@code capability.paramsType()} 获知应构造的具体类型。
     */
    private CommandParams params;
}

package com.gateway.device.protocol.model;

import com.gateway.device.protocol.common.constant.DeviceVendor;
import lombok.Builder;
import lombok.Data;

/**
 * 设备级命令结果 —— 批量任务中单台设备的执行结果。
 */
@Data
@Builder
public class DeviceCommandResult {

    /**
     * 设备 ID
     */
    private String deviceId;

    /**
     * 厂商
     */
    private DeviceVendor vendor;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 标准错误码
     */
    private String code;

    /**
     * 消息
     */
    private String message;

    /**
     * 响应数据
     */
    private Object data;

    /**
     * 耗时（毫秒）
     */
    private long costMillis;

    /**
     * 从 CommandResult 转换
     */
    public static DeviceCommandResult from(String deviceId, DeviceVendor vendor, CommandResult result) {
        return DeviceCommandResult.builder()
                .deviceId(deviceId)
                .vendor(vendor)
                .success(result.isSuccess())
                .code(result.getCode())
                .message(result.getMessage())
                .data(result.getData())
                .costMillis(result.getCostMillis())
                .build();
    }
}

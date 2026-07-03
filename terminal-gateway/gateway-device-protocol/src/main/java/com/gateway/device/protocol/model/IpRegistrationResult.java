package com.gateway.device.protocol.model;

import com.gateway.device.protocol.common.constant.DeviceVendor;
import lombok.Builder;
import lombok.Data;

/**
 * 单 IP 注册结果 —— IP 注册任务中单个 IP 的注册执行结果。
 */
@Data
@Builder
public class IpRegistrationResult {

    /**
     * 目标 IP
     */
    private String ip;

    /**
     * 厂商
     */
    private DeviceVendor vendor;

    /**
     * 是否注册成功
     */
    private boolean success;

    /**
     * 标准错误码（成功时为空）
     */
    private String errorCode;

    /**
     * 消息
     */
    private String message;

    /**
     * 注册成功后的设备 ID
     */
    private String deviceId;

    /**
     * 耗时（毫秒）
     */
    private long costMillis;

    /**
     * 成功结果
     */
    public static IpRegistrationResult success(String ip, DeviceVendor vendor,
                                               String deviceId, long costMillis) {
        return IpRegistrationResult.builder()
                .ip(ip)
                .vendor(vendor)
                .success(true)
                .deviceId(deviceId)
                .costMillis(costMillis)
                .build();
    }

    /**
     * 失败结果
     */
    public static IpRegistrationResult failure(String ip, DeviceVendor vendor,
                                               String errorCode, String message) {
        return IpRegistrationResult.builder()
                .ip(ip)
                .vendor(vendor)
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}

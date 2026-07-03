package com.gateway.device.protocol.api;

import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceContext;

/**
 * 设备注册信息提供者 —— 独立于  命令体系。
 *
 * <p>由 {@code AutoDiscoveryService} 在注册阶段直接调用，
 * 各厂商各自实现所需 API 调用编排（与对外查询命令无委托关系）。</p>
 */
public interface DeviceRegistrationProvider {

    DeviceVendor vendor();

    /**
     * 获取设备注册所需原始信息，返回 {@code byte[]}（二进制协议）或 {@code JsonNode}（HTTP/SDK 协议）。
     */
    Object fetchRegistrationInfo(DeviceContext device);

    /**
     * 是否支持显式 IP 注册模式（直接通过 IP:端口连接设备获取注册信息）。
     * SDK 通道等非 IP 直连模式应返回 {@code false}。
     *
     * @return 默认 {@code true}
     */
    default boolean supportsExplicitIp() {
        return true;
    }

    /**
     * 注册完成后回调（设备已注册到 DeviceManagementService，deviceId 已解析）。
     * 子类可在此持久化凭据等后置操作。
     */
    default void postRegister(DeviceContext device) {
    }
}

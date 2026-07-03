package com.gateway.device.protocol.api;

import com.gateway.device.protocol.model.DeviceAuthEntry;

/**
 * 设备认证凭据存储接口。
 *
 * <p>以 {@code deviceId} 为唯一标识，管理设备认证凭据。
 * 不同厂商共用此接口，具体实现在 gateway-device-core 中。</p>
 */
public interface DeviceAuthStore {

    /**
     * 按 deviceId 获取凭据。
     *
     * @return 已存储凭据，不存在时返回 null
     */
    DeviceAuthEntry get(String deviceId);

    /**
     * 认证通过后更新凭据。
     * 仅当凭据与已存储不同时才写入。
     */
    void update(String deviceId, DeviceAuthEntry entry);

    /**
     * 移除设备凭据。
     */
    void remove(String deviceId);
}

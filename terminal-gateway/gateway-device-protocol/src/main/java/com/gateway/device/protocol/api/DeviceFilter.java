package com.gateway.device.protocol.api;

import com.gateway.device.protocol.model.DeviceContext;

/**
 * 设备筛选条件 —— 单一匹配判定。
 */
@FunctionalInterface
public interface DeviceFilter {
    boolean matches(DeviceContext device);
}

package com.gateway.device.protocol.api;

import com.gateway.device.protocol.model.DeviceCommand;
import com.gateway.device.protocol.model.DeviceContext;

/**
 * 统一命令 → 厂商命令对象 映射。
 *
 * @param <T> 厂商命令对象类型
 */
public interface CommandMapper<T> {

    /**
     * 将设备级统一命令映射为厂商特有命令对象
     */
    T map(DeviceCommand command, DeviceContext device);
}

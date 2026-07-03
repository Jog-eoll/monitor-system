package com.gateway.device.core.executor;

import com.gateway.device.protocol.model.BatchCommandRequest;
import com.gateway.device.protocol.model.DeviceCommand;
import com.gateway.device.protocol.model.DeviceContext;

/**
 * 设备级命令工厂 —— 将批量请求拆分为单台设备命令。
 */
public class DeviceCommandFactory {

    public DeviceCommand create(BatchCommandRequest request, DeviceContext device) {
        return DeviceCommand.builder()
                .capability(request.getCapability())
                .target(device)
                .params(request.getParams())
                .build();
    }
}

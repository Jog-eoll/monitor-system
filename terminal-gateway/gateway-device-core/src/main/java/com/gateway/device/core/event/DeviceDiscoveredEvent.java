package com.gateway.device.core.event;

import com.gateway.device.protocol.model.DeviceContext;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 设备发现完成事件。
 */
@Getter
@Setter
public class DeviceDiscoveredEvent extends ApplicationEvent {

    private final List<DeviceContext> devices;

    public DeviceDiscoveredEvent(Object source, List<DeviceContext> devices) {
        super(source);
        this.devices = devices;
    }
}

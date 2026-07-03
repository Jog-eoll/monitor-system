package com.gateway.device.core.router;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import lombok.Getter;

/**
 * 无适配器支持指定设备+能力时抛出。
 */
@Getter
public class UnsupportedCapabilityException extends RuntimeException {

    private final String deviceId;
    private final DeviceVendor vendor;
    private final DeviceCapability<?> capability;

    public UnsupportedCapabilityException(String deviceId, DeviceVendor vendor, DeviceCapability<?> capability) {
        super(String.format("设备 %s (vendor=%s) 不支持能力 %s", deviceId, vendor, capability));
        this.deviceId = deviceId;
        this.vendor = vendor;
        this.capability = capability;
    }
}

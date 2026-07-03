package com.gateway.device.core.router;

import com.gateway.device.protocol.api.VendorProtocolAdapter;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceContext;

import java.util.List;

/**
 * 协议路由器 —— 自动装配 VendorProtocolAdapter Bean，按 vendor+capability 路由。
 */
public class ProtocolRouter {

    private final List<VendorProtocolAdapter> adapters;

    public ProtocolRouter(List<VendorProtocolAdapter> adapters) {
        this.adapters = adapters;
    }

    /**
     * 按 device.vendor + capability 精确路由。
     *
     * @throws UnsupportedCapabilityException 无适配器支持时抛出
     */
    public VendorProtocolAdapter route(DeviceContext device, DeviceCapability<?> capability) {
        return adapters.stream()
                .filter(a -> a.supports(device, capability))
                .findFirst()
                .orElseThrow(() -> new UnsupportedCapabilityException(
                        device.getDeviceId(), device.getVendor(), capability));
    }

    /**
     * 获取所有适配器
     */
    public List<VendorProtocolAdapter> all() {
        return adapters;
    }

    /**
     * 按厂商查找适配器
     */
    public VendorProtocolAdapter findByVendor(DeviceVendor vendor) {
        return adapters.stream()
                .filter(a -> a.vendor().equals(vendor))
                .findFirst()
                .orElse(null);
    }
}

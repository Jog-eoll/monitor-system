package com.gateway.device.protocol.adapter.novastar.standard;

import com.gateway.device.protocol.api.DeviceInfoEnricher;
import com.gateway.device.protocol.common.constant.DeviceVendor;

/**
 * NovaStandard 设备信息增强 —— 广播阶段已包含 SN，无需额外解析。
 */
public class NovaStandardDeviceInfoEnricher implements DeviceInfoEnricher {

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.NOVA_STAR_STANDARD;
    }
}

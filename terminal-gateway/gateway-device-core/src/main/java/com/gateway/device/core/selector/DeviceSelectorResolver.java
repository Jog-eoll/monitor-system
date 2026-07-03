package com.gateway.device.core.selector;

import com.gateway.device.core.store.DeviceRegistryManager;
import com.gateway.device.protocol.api.DeviceFilter;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.DeviceSelector;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备筛选器 —— 将 {@link DeviceSelector} 转为 Filter 链并遍历匹配。
 */
public class DeviceSelectorResolver {

    private final DeviceRegistryManager deviceRegistry;

    public DeviceSelectorResolver(DeviceRegistryManager deviceRegistry) {
        this.deviceRegistry = deviceRegistry;
    }

    public List<DeviceContext> resolve(DeviceSelector selector) {
        List<DeviceFilter> filters = selector.toFilters();
        return deviceRegistry.list().stream()
                .filter(device -> filters.stream().allMatch(f -> f.matches(device)))
                .collect(Collectors.toList());
    }
}

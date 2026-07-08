package com.gateway.device.core.service;

import com.gateway.device.core.store.DeviceRegistryManager;
import com.gateway.device.protocol.model.DeviceContext;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 设备管理服务 —— 对外部模块暴露的设备注册/查询/管理 API。
 *
 * <p>com.gateway.device 内部代码应直接使用 {@link com.gateway.device.core.store.DeviceRegistryManager}，
 * 本服务仅作为外部模块的统一入口外观。</p>
 */
public class DeviceManagementService {

    private final DeviceRegistryManager deviceRegistry;

    public DeviceManagementService(DeviceRegistryManager deviceRegistry) {
        this.deviceRegistry = deviceRegistry;
    }

    /**
     * 按 ID 查询
     */
    public Optional<DeviceContext> get(String deviceId) {
        return deviceRegistry.get(deviceId);
    }

    /**
     * 列出所有设备
     */
    public Collection<DeviceContext> listAll() {
        return deviceRegistry.list();
    }

    /**
     * 注册或更新设备
     */
    public void register(DeviceContext device) {
        deviceRegistry.register(device);
    }

    /**
     * 标记设备在线
     */
    public void markOnline(String deviceId) {
        deviceRegistry.markOnline(deviceId);
    }

    /**
     * 标记设备离线
     */
    public void markOffline(String deviceId) {
        deviceRegistry.markOffline(deviceId);
    }

    /**
     * 移除设备
     */
    public void remove(String deviceId) {
        deviceRegistry.remove(deviceId);
    }

    /**
     * 列出在线设备
     */
    public Collection<DeviceContext> listValid() {
        return deviceRegistry.list().stream()
                .filter(DeviceContext::isOnline)
                .filter(DeviceContext::isLoggedIn)
                .collect(Collectors.toList());
    }

    /**
     * 设备总数
     */
    public int count() {
        return deviceRegistry.size();
    }
}

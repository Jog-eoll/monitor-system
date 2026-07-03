package com.gateway.device.core.service;

import com.gateway.device.core.store.DeviceRegistryManager;
import com.gateway.device.protocol.model.DeviceContext;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 设备管理服务 —— 设备注册/查询/离线管理。
 */
public class DeviceManagementService {

    private final DeviceRegistryManager deviceRegistry;

    public DeviceManagementService(DeviceRegistryManager deviceRegistry) {
        this.deviceRegistry = deviceRegistry;
    }

    /**
     * 批量注册设备
     */
    public void registerAll(Collection<DeviceContext> devices) {
        devices.forEach(deviceRegistry::register);
    }

    /**
     * 注册单台设备
     */
    public void register(DeviceContext device) {
        deviceRegistry.register(device);
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
     * 按厂商筛选
     */
    public List<DeviceContext> listByVendor(String vendor) {
        return deviceRegistry.list().stream()
                .filter(d -> vendor.equals(d.getVendor()))
                .collect(Collectors.toList());
    }

    /**
     * 列出在线设备
     */
    public List<DeviceContext> listOnline() {
        return deviceRegistry.list().stream()
                .filter(DeviceContext::isOnline)
                .collect(Collectors.toList());
    }

    /**
     * 移除设备
     */
    public void remove(String deviceId) {
        deviceRegistry.remove(deviceId);
    }

    /**
     * 标记离线
     */
    public void markOffline(String deviceId) {
        deviceRegistry.markOffline(deviceId);
    }

    /**
     * 标记在线
     */
    public void markOnline(String deviceId) {
        deviceRegistry.markOnline(deviceId);
    }

    /**
     * 设备总数
     */
    public int count() {
        return deviceRegistry.size();
    }
}

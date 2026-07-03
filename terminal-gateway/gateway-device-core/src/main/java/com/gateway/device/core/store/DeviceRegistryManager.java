package com.gateway.device.core.store;

import com.gateway.device.protocol.model.DeviceContext;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 设备注册表管理器 —— 维护 deviceId → DeviceContext 映射。
 */
public class DeviceRegistryManager {

    private final ConcurrentMap<String, DeviceContext> devices = new ConcurrentHashMap<>();

    public void register(DeviceContext device) {
        devices.put(device.getDeviceId(), device);
    }

    public Optional<DeviceContext> get(String deviceId) {
        return Optional.ofNullable(devices.get(deviceId));
    }

    public Collection<DeviceContext> list() {
        return Collections.unmodifiableCollection(devices.values());
    }

    public void remove(String deviceId) {
        devices.remove(deviceId);
    }

    public void markOffline(String deviceId) {
        setOnline(deviceId, false);
    }

    public void markOnline(String deviceId) {
        setOnline(deviceId, true);
    }

    private void setOnline(String deviceId, boolean online) {
        devices.computeIfPresent(deviceId, (id, device) ->
                DeviceContext.builder()
                        .deviceId(device.getDeviceId())
                        .ip(device.getIp())
                        .port(device.getPort())
                        .vendor(device.getVendor())
                        .transportType(device.getTransportType())
                        .groupLabel(device.getGroupLabel())
                        .online(online)
                        .capabilities(device.getCapabilities())
                        .attributes(device.getAttributes())
                        .sn(device.getSn())
                        .macAddr(device.getMacAddr())
                        .width(device.getWidth())
                        .height(device.getHeight())
                        .build());
    }

    public int size() {
        return devices.size();
    }

    public void clear() {
        devices.clear();
    }
}

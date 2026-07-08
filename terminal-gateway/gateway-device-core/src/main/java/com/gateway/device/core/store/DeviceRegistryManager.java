package com.gateway.device.core.store;

import com.gateway.device.protocol.model.DeviceContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 设备注册表管理器 —— 维护 deviceId → DeviceContext 映射。
 *
 * <p>com.gateway.device 内部存储层，devices 增删改操作集中管理。
 * 外部模块应通过 {@link com.gateway.device.core.service.DeviceManagementService} 访问设备信息。</p>
 */
public class DeviceRegistryManager {

    private final ConcurrentMap<String, DeviceContext> devices = new ConcurrentHashMap<>();

    public void register(DeviceContext device) {
        devices.put(device.getDeviceId(), device);
    }

    public Optional<DeviceContext> get(String deviceId) {
        return Optional.ofNullable(devices.get(deviceId));
    }

    public List<DeviceContext> list() {
        return new ArrayList<>(devices.values());
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
                        .loggedIn(device.isLoggedIn())
                        .capabilities(device.getCapabilities())
                        .attributes(device.getAttributes())
                        .sn(device.getSn())
                        .macAddr(device.getMacAddr())
                        .width(device.getWidth())
                        .height(device.getHeight())
                        .build());
    }

    /**
     * 标记设备已登录。
     */
    public void markLoggedIn(String deviceId) {
        devices.computeIfPresent(deviceId, (id, device) ->
                DeviceContext.builder()
                        .deviceId(device.getDeviceId())
                        .ip(device.getIp())
                        .port(device.getPort())
                        .vendor(device.getVendor())
                        .transportType(device.getTransportType())
                        .groupLabel(device.getGroupLabel())
                        .online(device.isOnline())
                        .loggedIn(true)
                        .capabilities(device.getCapabilities())
                        .attributes(device.getAttributes())
                        .sn(device.getSn())
                        .macAddr(device.getMacAddr())
                        .width(device.getWidth())
                        .height(device.getHeight())
                        .build());
    }

    /**
     * 标记设备已登出。
     */
    public void markLoggedOut(String deviceId) {
        devices.computeIfPresent(deviceId, (id, device) ->
                DeviceContext.builder()
                        .deviceId(device.getDeviceId())
                        .ip(device.getIp())
                        .port(device.getPort())
                        .vendor(device.getVendor())
                        .transportType(device.getTransportType())
                        .groupLabel(device.getGroupLabel())
                        .online(device.isOnline())
                        .loggedIn(false)
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

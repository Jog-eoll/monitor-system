package com.gateway.device.core.store;

import com.gateway.device.protocol.api.DeviceAuthStore;
import com.gateway.device.protocol.model.DeviceAuthEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 设备认证管理器 —— 以 deviceId 为 key 管理凭据。
 *
 * <p>实现 {@link DeviceAuthStore}，认证通过后调用 {@link #update(String, DeviceAuthEntry)} 持久化。
 * 不同厂商共用此实例。</p>
 */
@Slf4j
public class DeviceAuthManager implements DeviceAuthStore {

    private final ConcurrentMap<String, DeviceAuthEntry> store = new ConcurrentHashMap<>();

    @Override
    public DeviceAuthEntry get(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return null;
        }
        return store.get(deviceId);
    }

    @Override
    public void update(String deviceId, DeviceAuthEntry entry) {
        if (deviceId == null || deviceId.isEmpty() || entry == null) {
            return;
        }
        store.put(deviceId, entry);
        log.debug("DeviceAuthManager updated for deviceId: {}", deviceId);
    }

    @Override
    public void remove(String deviceId) {
        if (deviceId != null) {
            store.remove(deviceId);
        }
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }
}

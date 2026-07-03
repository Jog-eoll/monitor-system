package com.gateway.device.core.selector;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Builds candidate device ids from external target fields.
 */
public final class DeviceSelectorIds {

    private DeviceSelectorIds() {
    }

    public static Set<String> fromDeviceIdAndIp(String deviceId, String ip) {
        Set<String> ids = new LinkedHashSet<>();
        if (StringUtils.hasText(deviceId)) {
            ids.add(deviceId.trim());
        }
        if (StringUtils.hasText(ip)) {
            ids.add(ip.trim());
        }
        return ids;
    }
}

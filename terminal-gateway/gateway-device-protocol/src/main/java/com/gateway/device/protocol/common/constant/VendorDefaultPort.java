package com.gateway.device.protocol.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link DeviceVendor} → 默认端口关联枚举，保持两者职责独立。
 *
 * <p>端口仅作为厂商默认值，上层调用传入的端口优先。</p>
 */
@Getter
@AllArgsConstructor
public enum VendorDefaultPort {

    JET_FILE_II(DeviceVendor.JET_FILE_II_STANDARD, 9520),
    NOVA_STAR_STANDARD(DeviceVendor.NOVA_STAR_STANDARD, 16600),
    NOVA_STAR_VIPLEX_CORE(DeviceVendor.NOVA_STAR_VIPLEX_CORE, 0),   // SDK 通道，无端口概念
    COLOR_LIGHT_STANDARD(DeviceVendor.COLOR_LIGHT_STANDARD, 8989);

    private static final Map<DeviceVendor, Integer> MAP =
            Arrays.stream(values()).collect(Collectors.toMap(
                    VendorDefaultPort::getVendor, VendorDefaultPort::getPort));

    private final DeviceVendor vendor;
    private final int port;

    /**
     * 按厂商查找默认端口，未找到返回 0。
     */
    public static int getDefaultPort(DeviceVendor vendor) {
        return MAP.getOrDefault(vendor, 0);
    }

}

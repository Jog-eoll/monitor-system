package com.gateway.device.protocol.common.discovery;

/**
 * 设备发现共享常量 —— 广播地址、厂商默认端口、超时等。
 *
 * <p>从 {@code DeviceVendorMapping}、{@code DeviceDiscovery}、
 * {@code NovaStandardDeviceDiscovery}、{@code NovaStandardAvonConst}
 * 中提取，消除跨文件冗余。</p>
 */
public final class DiscoveryConst {

    /**
     * 全局广播地址
     */
    public static final String BROADCAST_HOST = "255.255.255.255";
    /**
     * NovaStar AVON 设备监听端口
     */
    public static final int[] NOVA_STAR_DISCOVERY_PORTS = {16601, 16611};

    private DiscoveryConst() {
    }
}

package com.gateway.device.protocol.common.capability.expand;

/**
 * NovaStar Standard 协议私有能力（AVON UDP 广播发现）。
 *
 * <p>Standard 协议通过 {@code DeviceDiscoveryProvider} + {@code DeviceInfoEnricher} 实现发现，
 * 不通过 {@code CapabilityHandler}，当前无命令级能力定义。后续如有 AVON 帧协议命令在此扩展。</p>
 */
public final class NovaStandardCapability {
    private NovaStandardCapability() {
    }
    // 暂无 — 发现通过 DeviceDiscoveryProvider 实现
}

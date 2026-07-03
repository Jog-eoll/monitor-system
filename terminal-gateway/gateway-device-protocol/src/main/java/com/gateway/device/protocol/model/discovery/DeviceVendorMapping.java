package com.gateway.device.protocol.model.discovery;

import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.common.discovery.DiscoveryConst;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * 厂商发现映射配置 —— 每个厂商一条，承载端口/子网/超时/并发等发现参数。
 *
 * <p>共享常量参见 {@link DiscoveryConst}。</p>
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceVendorMapping {

    // ════════════════════════════════════════════════════
    // 字段
    // ════════════════════════════════════════════════════

    /**
     * 目标端口（用于广播发现或 TCP 探测），0 表示按厂商自动选择默认端口。
     */
    private int port;

    /**
     * 显式 IP 列表（可选）—— 存在时启用直连注册模式，跳过广播/子网发现。
     * 为空或 null 时回退广播/SDK 发现模式。
     */
    private List<String> ips;

    /**
     * 屏蔽 IP 列表（可选）—— 广播/子网/SDK 扫描时跳过这些 IP。
     * 不影响显式 IP 注册（ips）路径。
     */
    private List<String> blockIps;

    /**
     * 是否为独立 SDK 发现（非 byte-UDP 广播）。
     * SDK 厂商不需要端口，通过 {@code DeviceDiscoveryProvider#discover(int)} 直连发现。
     */
    private boolean sdk;

    /**
     * 厂商
     */
    private DeviceVendor vendor;

    /**
     * CIDR 子网列表（用于 ColorLight 等无广播厂商的 TCP 端口探测）。
     * 空列表或 null 表示不启用子网探测。
     */
    private List<String> subnets;

    /**
     * 发现超时（毫秒），默认 {@link DiscoveryConst#DEFAULT_TIMEOUT_MS}
     */
    private int timeoutMs = DiscoveryConst.DEFAULT_TIMEOUT_MS;

    /**
     * 并行探测线程数，默认 {@link DiscoveryConst#DEFAULT_CONCURRENCY}
     */
    private int concurrency = DiscoveryConst.DEFAULT_CONCURRENCY;

    // ════════════════════════════════════════════════════
    // 便捷方法
    // ════════════════════════════════════════════════════

    /**
     * 是否有显式 IP 列表（非空）
     */
    public boolean hasIps() {
        return CollectionUtils.isNotEmpty(ips);
    }

    /**
     * 是否有屏蔽 IP 列表（非空）
     */
    public boolean hasBlockIps() {
        return CollectionUtils.isNotEmpty(blockIps);
    }

    /**
     * 有效端口：配置值(>0)优先，否则按厂商默认
     */
    public int effectivePort() {
        if (port > 0) return port;
        return vendor != null ? VendorDefaultPort.getDefaultPort(vendor) : 0;
    }

    /**
     * 有效广播地址
     */
    public String effectiveBroadcastHost() {
        return DiscoveryConst.BROADCAST_HOST;
    }

    /**
     * 有效子网列表（永不返回 null）
     */
    public List<String> effectiveSubnets() {
        return CollectionUtils.isNotEmpty(subnets) ? subnets : Collections.emptyList();
    }
}

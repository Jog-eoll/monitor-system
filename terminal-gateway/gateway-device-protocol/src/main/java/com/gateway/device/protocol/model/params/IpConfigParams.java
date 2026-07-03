package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 设备 IP 配置参数 —— 所有字段均为可选（null=不修改）。
 *
 * <p>模式由 IP 字段决定：提供 {@code ip} → 静态配置，否则 → DHCP 自动获取。</p>
 */
@Data
@Builder
public class IpConfigParams implements CommandParams {

    /**
     * 目标设备唯一标识（必填，与 DeviceContext.deviceId 交叉校验，防误操作）
     */
    private String deviceId;

    /**
     * 新 IP 地址（提供则静态模式，null=切换 DHCP）
     */
    private String ip;

    /**
     * 新子网掩码（null=不修改，仅静态模式有效）
     */
    private String mask;

    /**
     * 新网关（null=不修改，仅静态模式有效）
     */
    private String gateway;

    /**
     * 首选 DNS 服务器地址（null=不修改）
     */
    private String dns1;

    /**
     * 备用 DNS 服务器地址（null=不修改）
     */
    private String dns2;
}

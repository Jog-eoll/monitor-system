package com.gateway.device.protocol.model;

import com.gateway.device.protocol.common.constant.DeviceVendor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * IP 注册请求 —— 程序化按需注册设备。
 *
 * <p>提供 IP 列表 + 厂商 + 端口即可触发异步注册流水线，
 * 无需在 YAML 中静态配置 {@code device.discovery.mappings[].ips}。</p>
 */
@Data
@Builder
public class IpRegistrationRequest {

    /**
     * 请求 ID（可选，调用方生成用于幂等）
     */
    private String requestId;

    /**
     * 厂商类型（分发键）
     */
    private DeviceVendor vendor;

    /**
     * 目标设备 IP 列表
     */
    private List<String> ips;

    /**
     * 端口，0 表示使用厂商默认端口
     */
    private int port;
}

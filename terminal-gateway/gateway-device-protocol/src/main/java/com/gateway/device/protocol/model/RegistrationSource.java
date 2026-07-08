package com.gateway.device.protocol.model;

/**
 * 设备注册来源 —— 标识触发设备注册/更新的调用路径。
 *
 * @author Claude
 */
public enum RegistrationSource {

    /**
     * 定时自动扫描发现（UDP 广播 / TCP 子网探测）
     */
    CONFIG_AUTO_DISCOVERY,

    /**
     * YAML 配置中显式指定的 IP 地址
     */
    CONFIG_IP,

    /**
     * API 手动提交的 IP 注册任务
     */
    MANUAL_IP,

    /**
     * SDK 直连扫描发现
     */
    SDK_DISCOVERY
}

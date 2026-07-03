package com.gateway.device.protocol.common.constant;

/**
 * 传输协议类型 —— 供 NettyTransportManager 选择 Channel 类型。
 */
public enum TransportType {

    /**
     * UDP 数据报协议（广播发现 + 二进制通信）
     */
    UDP,

    /**
     * TCP 流协议（私有二进制协议通信）
     */
    TCP,

    /**
     * HTTP REST API 协议
     */
    HTTP,

    /**
     * 原生 SDK 通道（非 Socket I/O）
     */
    NATIVE_SDK,
    ;
}

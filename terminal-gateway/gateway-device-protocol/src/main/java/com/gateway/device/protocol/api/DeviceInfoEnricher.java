package com.gateway.device.protocol.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.common.constant.DeviceVendor;

import java.util.Map;

/**
 * 设备信息增强器 —— 将 DEVICE_INFO_GET 查询结果合并到广播属性中。
 *
 * <p>各厂商各自实现，由 {@code AutoDiscoveryService} 调用。</p>
 */
public interface DeviceInfoEnricher {

    DeviceVendor vendor();

    /**
     * 从 DEVICE_INFO_GET 响应数据中提取信息，合并到 attrs。
     */
    default void enrich(DiscoveredDevice dd, byte[] infoData, Map<String, Object> attrs) {
    }

    /**
     * 从 DEVICE_INFO_GET 的 JsonNode 响应中提取信息（SDK 类协议使用）。
     */
    default void enrichJson(DiscoveredDevice dd, JsonNode info, Map<String, Object> attrs) {
    }
}

package com.gateway.device.protocol.api;

import com.gateway.device.protocol.common.constant.DeviceVendor;

import java.util.Collections;
import java.util.List;

/**
 * 设备发现提供者 —— 各协议各自实现广播请求构建与回复解析。
 *
 * <p>每个厂商一个实现，Spring 自动注入到 AutoDiscoveryService。</p>
 */
public interface DeviceDiscoveryProvider {

    DeviceVendor vendor();

    /**
     * 构建广播请求字节
     */
    byte[] buildBroadcastRequest();

    /**
     * 解析设备回送报文
     */
    DiscoveredDevice parseReply(byte[] data, String senderIp, int senderPort);

    /**
     * 用于身份比对的属性键列表
     */
    default List<String> identityKeys() {
        return Collections.emptyList();
    }

    /**
     * SDK 直连发现（适用于无法通过 byte-UDP 广播发现的厂商，如 ViplexCore）。
     *
     * <p>默认返回空列表表示使用传统 byte-UDP 路径。
     * 子类覆写以实现 SDK 驱动的自包含发现流程（搜索+登录+取信息）。</p>
     *
     * @param timeoutMs 发现超时（毫秒）
     * @return 发现的设备列表（已含完整属性和登录凭证）
     */
    default List<DiscoveredDevice> discover(int timeoutMs) {
        return Collections.emptyList();
    }
}

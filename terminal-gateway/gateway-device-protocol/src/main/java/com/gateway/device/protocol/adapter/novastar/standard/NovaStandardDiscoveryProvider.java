package com.gateway.device.protocol.adapter.novastar.standard;

import com.gateway.device.protocol.api.DeviceDiscoveryProvider;
import com.gateway.device.protocol.api.DiscoveredDevice;
import com.gateway.device.protocol.base.novastar.standard.discovery.NovaStandardAvonCodec;
import com.gateway.device.protocol.base.novastar.standard.discovery.NovaStandardAvonReply;
import com.gateway.device.protocol.common.constant.DeviceVendor;

import java.util.Collections;
import java.util.List;

/**
 * NovaStandard 设备发现提供者 —— 基于 AVON UDP 广播协议。
 *
 * <p>AVON 是 NovaStandard 设备搜索的私有协议，与标准帧协议 (0xAA/0xCC) 独立。
 * SN 为设备唯一序列号标识，MAC 地址当前 AVON 协议不支持。</p>
 */
public class NovaStandardDiscoveryProvider implements DeviceDiscoveryProvider {

    private final NovaStandardAvonCodec avonCodec = new NovaStandardAvonCodec();

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.NOVA_STAR_STANDARD;
    }

    @Override
    public byte[] buildBroadcastRequest() {
        return avonCodec.buildSearchRequest();
    }

    @Override
    public DiscoveredDevice parseReply(byte[] raw, String sourceIp, int sourcePort) {
        NovaStandardAvonReply reply = avonCodec.parseReply(raw, sourceIp, sourcePort);
        if (reply == null) {
            return null;
        }
        return reply.toDiscoveredDevice();
    }

    /**
     * 设备标识键：SN 为主标识。MAC 当前协议不支持。
     */
    @Override
    public List<String> identityKeys() {
        return Collections.singletonList("sn");
    }
}

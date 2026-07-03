package com.gateway.device.protocol.base.colorlight.standard.discovery;

import com.gateway.device.protocol.api.DeviceDiscoveryProvider;
import com.gateway.device.protocol.api.DiscoveredDevice;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ColorLight 设备发现提供者 —— 通过 Netty TCP 子网探测发现设备。
 * 后者通过 {@link ColorLightSubnetProbe} 执行 Netty TCP 端口探测。</p>
 *
 * <p>设备唯一标识：serialno（序列号）。</p>
 */
@Slf4j
public class ColorLightDiscoveryProvider implements DeviceDiscoveryProvider {

    private static final List<String> IDENTITY_KEYS = Collections.emptyList();

    private final ColorLightSubnetProbe probe;

    public ColorLightDiscoveryProvider(ColorLightSubnetProbe probe) {
        this.probe = probe;
    }

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.COLOR_LIGHT_STANDARD;
    }

    @Override
    public byte[] buildBroadcastRequest() {
        return null;
    }

    @Override
    public DiscoveredDevice parseReply(byte[] data, String senderIp, int senderPort) {
        return null;
    }

    @Override
    public List<String> identityKeys() {
        return IDENTITY_KEYS;
    }

    @Override
    public List<DiscoveredDevice> discover(int timeoutMs) {
        List<ColorLightDiscoveredDevice> devices = probe.probeSubnets();
        return new ArrayList<>(devices);
    }
}

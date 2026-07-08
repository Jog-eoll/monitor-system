package com.gateway.device.protocol.adapter.jetfileii.standard;

import com.gateway.device.protocol.api.DeviceDiscoveryProvider;
import com.gateway.device.protocol.api.DiscoveredDevice;
import com.gateway.device.protocol.base.jetfileii.standard.PacketBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.discovery.DeviceDiscovery;
import com.gateway.device.protocol.common.constant.DeviceVendor;

import java.util.Arrays;
import java.util.List;

/**
 * JetFileII 设备发现提供者 —— 0x0301 连接测试广播。
 *
 * <p>广播阶段用 IP 做临时标识。真实唯一标识（serialNo/macAddr）需通过
 * TCP 连接读取（使用 0x0112 READ_SYSINFO 命令）。</p>
 */
public class JetFileIIDiscoveryProvider implements DeviceDiscoveryProvider {

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.JET_FILE_II_STANDARD;
    }

    @Override
    public byte[] buildBroadcastRequest() {
        return PacketBuilder.create(MainCmd.TEST, SubCmd.TEST_CONNECT)
                .broadcast().needReply().buildBytes();
    }

    /**
     * 解析 0x0301 回送 Arg 格式 (12B):
     * [0-1] CPU 版本, [2-3] Reserved (未知), [4-7] IP(LE), [8] GG, [9] UU, [10-11] Rev
     */
    @Override
    public DiscoveredDevice parseReply(byte[] raw, String sourceIp, int sourcePort) {
        return DeviceDiscovery.parseReply(raw, sourceIp, sourcePort);
    }

    /**
     * 设备标识键：serialNo（主标识，来自 CONFIG.SYS），macAddr（次标识）。
     * 广播阶段均为 null，需 TCP 读取 CONFIG.SYS 后填充。
     */
    @Override
    public List<String> identityKeys() {
        return Arrays.asList("serialNo", "macAddr");
    }
}

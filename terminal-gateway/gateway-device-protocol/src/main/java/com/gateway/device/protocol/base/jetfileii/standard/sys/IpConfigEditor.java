package com.gateway.device.protocol.base.jetfileii.standard.sys;

import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.model.params.IpConfigParams;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;

/**
 * CONFIG.SYS 网络配置编排器 —— 封装 读→改→写 三步操作。
 *
 * <p>使用方式:
 * <pre>{@code
 *   IpConfigEditor editor = new IpConfigEditor();
 *   byte[] data = editor.readConfig(ft);
 *   byte[] modified = editor.applyConfig(data, params);
 *   editor.writeConfig(ft, modified);
 * }</pre>
 */
@Slf4j
public class IpConfigEditor {

    /**
     * 从设备读取 CONFIG.SYS 并校验最小长度。
     *
     * @param ft 已配置地址的 FileTransfer
     * @return CONFIG.SYS 原始字节
     * @throws IOException 读取失败或尺寸异常
     */
    public byte[] readConfig(FileTransfer ft) throws IOException {
        byte[] data = ft.readSysFile(ConfigSysLayout.FILE_NAME);
        if (data == null || data.length < ConfigSysLayout.MIN_SIZE) {
            throw new IOException("CONFIG.SYS 尺寸异常: "
                    + (data == null ? "null" : data.length + "B")
                    + " (最小 " + ConfigSysLayout.MIN_SIZE + "B)");
        }
        log.debug("[IpConfig] 读取 CONFIG.SYS: {}B, 当前IP={}",
                data.length,
                ConfigSysLayout.bytesToIpLE(data, ConfigSysLayout.OFF_IP));
        return data;
    }

    /**
     * 将 {@link IpConfigParams} 中的非 null 字段覆盖写入 data 副本。
     *
     * <p>字段映射：{@code params.getMask()} → 掩码偏移，{@code params.getDns()} 取第一项 → DNS 偏移</p>
     *
     * @param data   CONFIG.SYS 原始数据
     * @param params 要修改的字段（null 字段保持不变）
     * @return 修改后的数据副本
     */
    public byte[] applyConfig(byte[] data, IpConfigParams params) {
        byte[] copy = Arrays.copyOf(data, data.length);

        // DHCP / 静态模式标志
        boolean dhcp = params.getIp() == null;
        boolean wasDhcp = ConfigSysLayout.isDhcp(copy);
        ConfigSysLayout.writeDhcpFlag(copy, dhcp);
        if (dhcp != wasDhcp) {
            log.debug("[IpConfig] 模式: {} → {}", wasDhcp ? "DHCP" : "静态", dhcp ? "DHCP" : "静态");
        }

        if (params.getIp() != null) {
            String old = ConfigSysLayout.bytesToIpLE(copy, ConfigSysLayout.OFF_IP);
            ConfigSysLayout.writeIpLE(copy, ConfigSysLayout.OFF_IP, params.getIp());
            log.debug("[IpConfig] IP: {} → {}", old, params.getIp());
        }
        if (params.getGateway() != null) {
            String old = ConfigSysLayout.bytesToIpLE(copy, ConfigSysLayout.OFF_GATEWAY);
            ConfigSysLayout.writeIpLE(copy, ConfigSysLayout.OFF_GATEWAY, params.getGateway());
            log.debug("[IpConfig] 网关: {} → {}", old, params.getGateway());
        }
        if (params.getMask() != null) {
            String old = ConfigSysLayout.bytesToIpLE(copy, ConfigSysLayout.OFF_NETMASK);
            ConfigSysLayout.writeIpLE(copy, ConfigSysLayout.OFF_NETMASK, params.getMask());
            log.debug("[IpConfig] 掩码: {} → {}", old, params.getMask());
        }
        String dns = params.getDns1();
        if (dns != null && !dns.isEmpty()) {
            String old = ConfigSysLayout.bytesToIpLE(copy, ConfigSysLayout.OFF_DNS);
            ConfigSysLayout.writeIpLE(copy, ConfigSysLayout.OFF_DNS, dns);
            log.debug("[IpConfig] DNS: {} → {}", old, dns);
        }

        return copy;
    }

    /**
     * 将修改后的 CONFIG.SYS 写回设备。
     *
     * @param ft   FileTransfer
     * @param data 修改后的数据
     * @throws IOException 写入失败
     */
    public void writeConfig(FileTransfer ft, byte[] data) throws IOException {
        ft.writeSysFile(ConfigSysLayout.FILE_NAME, data, 1024);
        log.info("[IpConfig] CONFIG.SYS 写入完成, {}B", data.length);
    }
}

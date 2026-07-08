package com.gateway.device.protocol.base.novastar.standard.discovery;

import lombok.Data;

import java.util.List;

/**
 * AVON 广播回复 —— 对应设备回送的 JSON 结构。
 *
 * <p>Jackson 直接将 JSON 字段映射到此 POJO。</p>
 */
@Data
public class NovaStandardAvonReply {

    /**
     * 设备 IP (非 JSON 字段，由调用方填入)
     */
    private String ip;

    /**
     * 回复来源端口 (非 JSON 字段)
     */
    private int sourcePort;

    private String aliasName;

    private String sn;

    private String productName;

    private String platform;

    private int tcpPort;

    private int ftpPort;

    private int syssetTcpPort;

    private int syssetFtpPort;

    private int width;

    private int height;

    private int rotation;

    private int encodeType;

    private boolean logined;

    private boolean privacy;

    private String key;

    private List<String> loginedUsernames;

    /**
     * 转换为通用发现设备对象，直接嵌入本 POJO。MAC 当前 AVON 协议不支持，设为 null。
     */
    public NovaStandardDiscoveredDevice toDiscoveredDevice() {
        return NovaStandardDiscoveredDevice.builder()
                .ip(ip)
                .sourcePort(sourcePort)
                .avon(this)
                .macAddr(null)
                .build();
    }
}

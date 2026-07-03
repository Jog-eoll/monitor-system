package com.gateway.device.protocol.base.novastar.standard.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * AVON 广播回复 —— 对应设备回送的 JSON 结构。
 *
 * <p>Jackson 直接将 JSON 字段映射到此 POJO。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NovaStandardAvonReply {

    /**
     * 设备 IP (非 JSON 字段，由调用方填入)
     */
    @JsonIgnoreProperties
    private String ip;

    /**
     * 回复来源端口 (非 JSON 字段)
     */
    @JsonIgnoreProperties
    private int sourcePort;

    @JsonProperty("aliasName")
    private String aliasName;

    @JsonProperty("sn")
    private String sn;

    @JsonProperty("productName")
    private String productName;

    @JsonProperty("platform")
    private String platform;

    @JsonProperty("tcpPort")
    private int tcpPort;

    @JsonProperty("ftpPort")
    private int ftpPort;

    @JsonProperty("syssetTcpPort")
    private int syssetTcpPort;

    @JsonProperty("syssetFtpPort")
    private int syssetFtpPort;

    @JsonProperty("width")
    private int width;

    @JsonProperty("height")
    private int height;

    @JsonProperty("rotation")
    private int rotation;

    @JsonProperty("encodeType")
    private int encodeType;

    @JsonProperty("logined")
    private boolean logined;

    @JsonProperty("privacy")
    private boolean privacy;

    @JsonProperty("key")
    private String key;

    @JsonProperty("loginedUsernames")
    private List<String> loginedUsernames = Collections.emptyList();

    /**
     * 转换为通用发现设备对象。MAC 当前 AVON 协议不支持，设为 null。
     */
    public NovaStandardDiscoveredDevice toDiscoveredDevice() {
        return NovaStandardDiscoveredDevice.builder()
                .ip(ip)
                .sourcePort(sourcePort)
                .deviceName(aliasName != null ? aliasName : "")
                .sn(sn)
                .productName(productName)
                .platform(platform)
                .tcpPort(tcpPort)
                .ftpPort(ftpPort)
                .syssetTcpPort(syssetTcpPort)
                .syssetFtpPort(syssetFtpPort)
                .width(width)
                .height(height)
                .rotation(rotation)
                .encodeType(encodeType)
                .logined(logined)
                .privacy(privacy)
                .key(key)
                .loginedUsernames(loginedUsernames)
                .macAddr(null)
                .build();
    }
}

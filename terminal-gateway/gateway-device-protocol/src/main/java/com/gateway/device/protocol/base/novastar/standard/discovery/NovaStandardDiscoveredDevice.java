package com.gateway.device.protocol.base.novastar.standard.discovery;

import com.gateway.device.protocol.api.DiscoveredDevice;

import lombok.Builder;
import lombok.Data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NovaStandard AVON 广播发现的设备信息。
 *
 * <p>设备唯一标识：SN（序列号），MAC 地址当前 AVON 协议不支持。</p>
 */
@Data
@Builder
public class NovaStandardDiscoveredDevice implements DiscoveredDevice {

    /**
     * 支持的子产品型号
     */
    public static final String PRODUCT_T4H = "T4H";
    public static final String PRODUCT_TB4 = "TB4";
    private static final List<String> SUPPORTED_PRODUCTS = Arrays.asList(PRODUCT_T4H, PRODUCT_TB4);

    private String ip;
    private int sourcePort;
    private String deviceName;

    /**
     * 产品序列号（唯一标识）
     */
    private String sn;
    /**
     * 产品型号 (如 T4H, TB4)
     */
    private String productName;
    /**
     * 硬件平台 (如 rk3288)
     */
    private String platform;
    /**
     * TCP 通信端口
     */
    private int tcpPort;
    /**
     * FTP 端口
     */
    private int ftpPort;
    /**
     * 系统设置 TCP 端口
     */
    private int syssetTcpPort;
    /**
     * 系统设置 FTP 端口
     */
    private int syssetFtpPort;
    /**
     * 屏幕宽度
     */
    private int width;
    /**
     * 屏幕高度
     */
    private int height;
    /**
     * 屏幕旋转
     */
    private int rotation;
    /**
     * 编码类型
     */
    private int encodeType;
    /**
     * 是否已登录
     */
    private boolean logined;
    /**
     * 隐私模式
     */
    private boolean privacy;
    /**
     * 连接密钥
     */
    private String key;
    /**
     * 已登录用户名列表
     */
    @Builder.Default
    private List<String> loginedUsernames = java.util.Collections.emptyList();

    /**
     * MAC 地址（当前 AVON 协议不支持，预留）
     */
    @Builder.Default
    private String macAddr = null;

    /**
     * 是否为受支持的子产品型号
     */
    public boolean isSupportedProduct() {
        return productName != null && SUPPORTED_PRODUCTS.contains(productName);
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("deviceName", deviceName);
        attrs.put("sn", sn);
        attrs.put("productName", productName);
        attrs.put("platform", platform);
        attrs.put("tcpPort", tcpPort);
        attrs.put("ftpPort", ftpPort);
        attrs.put("syssetTcpPort", syssetTcpPort);
        attrs.put("syssetFtpPort", syssetFtpPort);
        attrs.put("width", width);
        attrs.put("height", height);
        attrs.put("rotation", rotation);
        attrs.put("encodeType", encodeType);
        attrs.put("logined", logined);
        attrs.put("privacy", privacy);
        attrs.put("key", key);
        attrs.put("loginedUsernames", loginedUsernames);
        attrs.put("macAddr", macAddr);
        return attrs;
    }

    @Override
    public String toString() {
        return String.format("%-16s tcp=%d sn=%s name=%s", ip, tcpPort, sn, deviceName);
    }
}

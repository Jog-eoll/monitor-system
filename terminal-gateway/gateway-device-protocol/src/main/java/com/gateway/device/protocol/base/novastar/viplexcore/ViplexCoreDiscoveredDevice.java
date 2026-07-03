package com.gateway.device.protocol.base.novastar.viplexcore;

import com.gateway.device.protocol.api.DiscoveredDevice;
import com.gateway.device.protocol.common.constant.ProtocolConstant;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ViplexCore SDK 发现的设备。
 *
 * <p>与 {@code NovaStandardDiscoveredDevice} 不同，此类来自 SDK 的
 * {@code nvSearchTerminalAsync} 回调 JSON，非 AVON UDP 广播。</p>
 */
@Data
@Builder
public class ViplexCoreDiscoveredDevice implements DiscoveredDevice {

    private String ip;
    private int sourcePort;
    /**
     * 终端 TCP 命令端口（16603）
     */
    private int tcpPort;
    /**
     * 终端 FTP 端口（16602）
     */
    private int ftpPort;
    private String sn;
    private String productName;
    private String model;
    private String mac;
    private String fpga;
    private String mainVersion;
    private String aliasName;
    private String platform;
    private Boolean hasPassWord;
    private Integer width;
    private Integer height;

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("sn", sn);
        attrs.put("productName", productName);
        attrs.put("model", model);
        attrs.put("mac", ProtocolConstant.formatMac(mac));
        attrs.put("fpga", fpga);
        attrs.put("mainVersion", mainVersion);
        attrs.put("aliasName", aliasName);
        if (tcpPort > 0) attrs.put("tcpPort", tcpPort);
        if (ftpPort > 0) attrs.put("ftpPort", ftpPort);
        if (platform != null) attrs.put("platform", platform);
        if (hasPassWord != null) attrs.put("hasPassWord", hasPassWord);
        if (width != null) attrs.put("width", width);
        if (height != null) attrs.put("height", height);
        return attrs;
    }
}

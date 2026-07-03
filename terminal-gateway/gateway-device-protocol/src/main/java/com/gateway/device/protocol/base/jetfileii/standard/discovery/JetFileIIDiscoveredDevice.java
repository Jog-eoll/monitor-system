package com.gateway.device.protocol.base.jetfileii.standard.discovery;

import com.gateway.device.protocol.api.DiscoveredDevice;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * JetFileII 广播搜索发现的设备信息。
 *
 * <p>通过 0x0301 连接测试命令的广播回送解析得到基本网络信息。
 * serialNo/macAddr 需通过 TCP 连接读取 CONFIG.SYS 后填充，
 * 作为设备的真实唯一标识。</p>
 */
@Data
@Builder
public class JetFileIIDiscoveredDevice implements DiscoveredDevice {

    /**
     * 设备 IP 地址（广播阶段临时标识）
     */
    private String ip;

    /**
     * 组地址 GG（可配置，默认 1）
     */
    private int gg;

    /**
     * 单元地址 UU（可配置，默认 1）
     */
    private int uu;

    /**
     * CPU 程序版本
     */
    private int cpuVersion;

    /**
     * FPGA 固件版本
     */
    private int fpgaVersion;

    /**
     * 回送来源端口
     */
    private int sourcePort;

    /**
     * 产品序列号（来自 CONFIG.SYS，需 TCP 读取后填充）
     */
    private String serialNo;

    /**
     * MAC 地址（来自 CONFIG.SYS，需 TCP 读取后填充）
     */
    private String macAddr;

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("ip", ip);
        attrs.put("gg", gg);
        attrs.put("uu", uu);
        attrs.put("cpuVersion", cpuVersion);
        attrs.put("fpgaVersion", fpgaVersion);
        attrs.put("serialNo", serialNo);
        attrs.put("macAddr", macAddr);
        return attrs;
    }

    public String getAddress() {
        return String.format("GG=%d UU=%d", gg, uu);
    }

    @Override
    public String toString() {
        String id = serialNo != null ? serialNo : ip;
        return String.format("%-16s SN=%s GG=%-3d UU=%-3d CPU=v%d  FPGA=v%d",
                ip, id, gg, uu, cpuVersion, fpgaVersion);
    }
}

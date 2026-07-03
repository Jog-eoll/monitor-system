package com.infopublish.client.service;

import java.util.List;

/**
 * ARP 静态绑定服务接口
 *
 * <p>通过 netsh 命令将"情报板IP"静态绑定到"发布网关MAC"，
 * 使本机将发往情报板的流量实际送到发布网关（ARP欺骗/重定向）。
 *
 * <p>需要以管理员权限运行（launch4j manifest 应配置 requireAdministrator）。
 */
public interface ArpBindService {

    /**
     * 执行静态 ARP 绑定
     *
     * @param targetIp   情报板（被劫持的目标）IP，如 192.168.1.50
     * @param gatewayMac 发布网关真实 MAC，如 00-30-b4-01-62-98
     * @return 执行结果描述
     */
    String bind(String targetIp, String gatewayMac);

    /**
     * 解除静态 ARP 绑定
     *
     * @param targetIp 情报板 IP
     */
    String unbind(String targetIp);

    /**
     * 查询当前绑定状态
     */
    ArpStatus getStatus();

    /**
     * 列出所有网卡信息（供前端选择/参考）
     */
    List<InterfaceInfo> listInterfaces();

    /**
     * 查询本机网卡索引（诊断用）
     */
    int findInterfaceIndex();

    // ========== 内部 DTO ==========

    /**
     * ARP 绑定状态
     */
    class ArpStatus {
        private boolean bound;
        private boolean verified;
        private String  targetIp;
        private String  gatewayMac;
        private int     ifIdx;
        private String  lastOutput;

        public boolean isBound()           { return bound; }
        public void    setBound(boolean b) { this.bound = b; }
        public boolean isVerified()        { return verified; }
        public void    setVerified(boolean v) { this.verified = v; }
        public String  getTargetIp()       { return targetIp; }
        public void    setTargetIp(String v)   { this.targetIp = v; }
        public String  getGatewayMac()     { return gatewayMac; }
        public void    setGatewayMac(String v) { this.gatewayMac = v; }
        public int     getIfIdx()          { return ifIdx; }
        public void    setIfIdx(int v)     { this.ifIdx = v; }
        public String  getLastOutput()     { return lastOutput; }
        public void    setLastOutput(String v) { this.lastOutput = v; }
    }

    /**
     * 网卡信息
     */
    class InterfaceInfo {
        private int    idx;
        private String state;
        private String name;

        public int    getIdx()          { return idx; }
        public void   setIdx(int v)     { this.idx = v; }
        public String getState()        { return state; }
        public void   setState(String v){ this.state = v; }
        public String getName()         { return name; }
        public void   setName(String v) { this.name = v; }
    }
}

package com.infopublish.client.service;

import java.util.List;

/**
 * UDP 端口归属查询服务接口
 *
 * <p>通过 Windows iphlpapi.dll 的 GetExtendedUdpTable API 查询 UDP 端口归属进程。
 */
public interface UdpPortOwnerService {

    /**
     * 查询当前系统所有 UDP 端口及其归属进程 PID
     *
     * @return UDP 端口条目列表；API 不可用或调用失败时返回空列表
     */
    List<UdpEntry> queryUdpTable();

    /** API 是否可用 */
    boolean isAvailable();

    /**
     * UDP 端口表条目
     */
    class UdpEntry {
        public final String localIp;
        public final int localPort;
        public final long owningPid;

        public UdpEntry(String localIp, int localPort, long owningPid) {
            this.localIp = localIp;
            this.localPort = localPort;
            this.owningPid = owningPid;
        }

        @Override
        public String toString() {
            return localIp + ":" + localPort + " -> PID=" + owningPid;
        }
    }
}

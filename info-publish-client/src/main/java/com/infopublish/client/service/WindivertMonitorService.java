package com.infopublish.client.service;

import java.util.Map;

/**
 * WinDivert 旁路监听服务。
 *
 * <p>Phase 1 只维护来源 UDP 端口归属 PID 缓存，不接管、不重定向、不注入流量。
 */
public interface WindivertMonitorService {

    /**
     * 查询来源 IP:Port 对应的 PID。
     */
    PidLookupResult findPid(String sourceIp, int sourcePort);

    /**
     * WinDivert 未命中或不可用时是否允许回退到 GetExtendedUdpTable。
     */
    boolean isFallbackToUdpTableEnabled();

    /**
     * 查询运行状态。
     */
    Map<String, Object> getStatus();

    /**
     * 清空 PID 缓存和计数。
     */
    void clear();

    class PidLookupResult {
        private final boolean hit;
        private final boolean available;
        private final long pid;
        private final String sourceIp;
        private final int sourcePort;
        private final String remoteIp;
        private final int remotePort;
        private final String reasonCode;
        private final long eventTime;

        private PidLookupResult(boolean hit, boolean available, long pid,
                                String sourceIp, int sourcePort,
                                String remoteIp, int remotePort,
                                String reasonCode, long eventTime) {
            this.hit = hit;
            this.available = available;
            this.pid = pid;
            this.sourceIp = sourceIp;
            this.sourcePort = sourcePort;
            this.remoteIp = remoteIp;
            this.remotePort = remotePort;
            this.reasonCode = reasonCode;
            this.eventTime = eventTime;
        }

        public static PidLookupResult hit(String sourceIp, int sourcePort, long pid, long eventTime) {
            return hit(sourceIp, sourcePort, null, -1, pid, eventTime);
        }

        public static PidLookupResult hit(String sourceIp, int sourcePort,
                                          String remoteIp, int remotePort,
                                          long pid, long eventTime) {
            return new PidLookupResult(true, true, pid, sourceIp, sourcePort,
                    remoteIp, remotePort, "WINDIVERT_MATCH", eventTime);
        }

        public static PidLookupResult miss(String reasonCode) {
            return new PidLookupResult(false, true, -1L, null, -1, null, -1, reasonCode, 0L);
        }

        public static PidLookupResult unavailable(String reasonCode) {
            return new PidLookupResult(false, false, -1L, null, -1, null, -1, reasonCode, 0L);
        }

        public boolean isHit() { return hit; }
        public boolean isAvailable() { return available; }
        public long getPid() { return pid; }
        public String getSourceIp() { return sourceIp; }
        public int getSourcePort() { return sourcePort; }
        public String getRemoteIp() { return remoteIp; }
        public int getRemotePort() { return remotePort; }
        public String getReasonCode() { return reasonCode; }
        public long getEventTime() { return eventTime; }
    }
}

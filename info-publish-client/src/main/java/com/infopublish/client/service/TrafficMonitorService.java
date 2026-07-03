package com.infopublish.client.service;

/**
 * 流量来源合法性校验服务接口
 *
 * <p>监管通信数据的来源合法性，而非流量数量。
 * 每次接收到 TCP/UDP 数据时调用 checkAndRecordSource()，
 * 内部通过 ProcessBindService 查询白名单（由进程 PID 反查的合法 IP:Port 集合），
 * 若来源不在白名单中则生成告警，提示运维人员拔出 UKey。
 */
public interface TrafficMonitorService {

    /**
     * 检查并记录一次数据接收事件
     *
     * <p>校验来源 IP:Port 是否在合法白名单中。
     * 若为非法来源：生成告警消息并记录 WARN 日志。
     * 若为合法来源或白名单为空（降级）：直接返回 true。
     *
     * @param sourceIp   来源 IP 字符串
     * @param sourcePort 来源端口号
     * @return 来源合法返回 true；非法返回 false 并产生告警
     */
    boolean checkAndRecordSource(String sourceIp, int sourcePort);

    /**
     * 获取当前未读告警消息
     *
     * @return 告警消息对象；若无告警则返回 null
     */
    AlertMessage getAlertMessage();

    /**
     * 清除当前告警（用户点击"我知道了"后调用）
     */
    void clearAlert();

    /**
     * 告警消息数据结构
     */
    class AlertMessage {
        private final String message;
        private final long alertTime;
        private final String sourceIp;
        private final int sourcePort;

        public AlertMessage(String message, long alertTime, String sourceIp, int sourcePort) {
            this.message = message;
            this.alertTime = alertTime;
            this.sourceIp = sourceIp;
            this.sourcePort = sourcePort;
        }

        public String getMessage() { return message; }
        public long getAlertTime() { return alertTime; }
        public String getSourceIp() { return sourceIp; }
        public int getSourcePort() { return sourcePort; }
    }
}

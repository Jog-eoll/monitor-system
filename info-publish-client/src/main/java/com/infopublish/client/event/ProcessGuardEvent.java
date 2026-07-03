package com.infopublish.client.event;

/**
 * 进程守护事件。
 *
 * <p>当已绑定的目标进程 PID 消亡时发布，由 UKey 认证处理器按 UKey 拔出流程清理本地授权状态。</p>
 */
public class ProcessGuardEvent {

    private final long pid;
    private final String processName;
    private final String source;
    private final String reason;

    public ProcessGuardEvent(long pid, String processName, String source, String reason) {
        this.pid = pid;
        this.processName = processName;
        this.source = source;
        this.reason = reason;
    }

    public long getPid() {
        return pid;
    }

    public String getProcessName() {
        return processName;
    }

    public String getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }
}

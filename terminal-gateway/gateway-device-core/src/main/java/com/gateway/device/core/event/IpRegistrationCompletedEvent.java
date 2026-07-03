package com.gateway.device.core.event;

import com.gateway.device.protocol.model.BatchTaskStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * IP 注册任务完成事件。
 */
@Getter
public class IpRegistrationCompletedEvent extends ApplicationEvent {

    private final String taskId;
    private final BatchTaskStatus status;
    private final int successCount;
    private final int failedCount;
    private final int total;

    public IpRegistrationCompletedEvent(Object source, String taskId,
                                        BatchTaskStatus status,
                                        int successCount, int failedCount, int total) {
        super(source);
        this.taskId = taskId;
        this.status = status;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.total = total;
    }
}

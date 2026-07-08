package com.gateway.device.core.event.logger;

import com.gateway.device.protocol.model.BatchTaskStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * 批量任务完成事件。
 */
@Getter
@Setter
public class BatchTaskCompletedEvent extends ApplicationEvent {

    private final String taskId;
    private final BatchTaskStatus status;

    public BatchTaskCompletedEvent(Object source, String taskId, BatchTaskStatus status) {
        super(source);
        this.taskId = taskId;
        this.status = status;
    }
}

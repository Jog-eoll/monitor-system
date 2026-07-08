package com.gateway.device.core.event.bus;

import com.gateway.device.protocol.model.BatchCommandRequest;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 批量命令提交事件 —— 通过事件总线提交批量命令任务。
 *
 * <p>可由外部调用方直接发布，也可由 {@code BatchCommandService} 桥接发布，
 * 最终由 {@code DeviceSubmitEventListener} 统一消费执行。</p>
 */
@Getter
public class BatchCommandSubmitEvent extends ApplicationEvent {

    private final BatchCommandRequest request;
    private final String taskId;

    public BatchCommandSubmitEvent(Object source, BatchCommandRequest request, String taskId) {
        super(source);
        this.request = request;
        this.taskId = taskId;
    }
}

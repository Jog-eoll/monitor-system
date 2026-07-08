package com.gateway.device.core.event.logger;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 批量任务提交事件。
 */
@Getter
public class BatchTaskSubmittedEvent extends ApplicationEvent {

    private final String taskId;
    private final DeviceCapability<?> capability;
    private final int deviceCount;

    public BatchTaskSubmittedEvent(Object source, String taskId,
                                   DeviceCapability<?> capability, int deviceCount) {
        super(source);
        this.taskId = taskId;
        this.capability = capability;
        this.deviceCount = deviceCount;
    }
}

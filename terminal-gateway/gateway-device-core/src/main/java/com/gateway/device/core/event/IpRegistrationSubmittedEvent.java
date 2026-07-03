package com.gateway.device.core.event;

import com.gateway.device.protocol.common.constant.DeviceVendor;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * IP 注册任务提交事件。
 */
@Getter
public class IpRegistrationSubmittedEvent extends ApplicationEvent {

    private final String taskId;
    private final DeviceVendor vendor;
    private final int ipCount;

    public IpRegistrationSubmittedEvent(Object source, String taskId,
                                        DeviceVendor vendor, int ipCount) {
        super(source);
        this.taskId = taskId;
        this.vendor = vendor;
        this.ipCount = ipCount;
    }
}

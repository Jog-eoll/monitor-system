package com.gateway.device.core.event.bus;

import com.gateway.device.protocol.model.IpRegistrationRequest;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * IP 注册提交事件 —— 通过事件总线提交 IP 注册任务。
 *
 * <p>可由外部调用方直接发布，也可由 {@code IpRegistrationService} 桥接发布，
 * 最终由 {@code DeviceSubmitEventListener} 统一消费执行。</p>
 */
@Getter
public class IpRegistrationSubmitEvent extends ApplicationEvent {

    private final IpRegistrationRequest request;
    private final String taskId;

    public IpRegistrationSubmitEvent(Object source, IpRegistrationRequest request, String taskId) {
        super(source);
        this.request = request;
        this.taskId = taskId;
    }
}

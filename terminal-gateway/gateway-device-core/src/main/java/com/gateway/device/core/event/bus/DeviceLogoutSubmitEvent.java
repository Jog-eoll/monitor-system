package com.gateway.device.core.event.bus;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 设备登出提交事件 —— 通过事件总线发起设备登出。
 *
 * <p>最终由 {@code DeviceSubmitEventListener} 统一消费执行，
 * 成功则发布 {@code DeviceLoggedOutEvent}。</p>
 */
@Getter
public class DeviceLogoutSubmitEvent extends ApplicationEvent {

    /**
     * 待登出的设备 ID 列表
     */
    private final List<String> deviceIds;

    public DeviceLogoutSubmitEvent(Object source, List<String> deviceIds) {
        super(source);
        this.deviceIds = deviceIds;
    }
}

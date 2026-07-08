package com.gateway.device.core.event.logger;

import com.gateway.device.protocol.model.DeviceContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 设备登出事件 —— 设备显式注销成功时发布，各协议按需实现。
 *
 * <p>登出后设备在注册表中保留但 {@code loggedIn=false}，
 * 后续自动注册及功能分发均被拦截。</p>
 */
@Getter
public class DeviceLoggedOutEvent extends ApplicationEvent {

    private final DeviceContext device;

    public DeviceLoggedOutEvent(Object publisher, DeviceContext device) {
        super(publisher);
        this.device = device;
    }
}

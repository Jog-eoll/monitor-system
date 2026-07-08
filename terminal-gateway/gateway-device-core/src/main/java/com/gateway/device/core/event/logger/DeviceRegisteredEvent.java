package com.gateway.device.core.event.logger;

import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.RegistrationSource;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 设备注册成功事件 —— 统一覆盖自动发现、手动注册、定时更新等所有场景。
 *
 * <p>每当设备通过注册流水线成功注册或信息更新时发布。
 * 监听者可通过 {@link #isNew()} 区分首次注册和信息刷新。</p>
 */
@Getter
public class DeviceRegisteredEvent extends ApplicationEvent {

    private final DeviceContext device;
    private final RegistrationSource source;
    private final boolean isNew;

    /**
     * @param source    事件发布者
     * @param device    注册成功的设备上下文
     * @param regSource 注册来源
     * @param isNew     是否为新设备注册（首次），{@code false} 表示已有设备信息更新
     */
    public DeviceRegisteredEvent(Object source, DeviceContext device,
                                 RegistrationSource regSource, boolean isNew) {
        super(source);
        this.device = device;
        this.source = regSource;
        this.isNew = isNew;
    }
}

package com.gateway.device.core.service;

import com.gateway.device.core.event.bus.DeviceLogoutSubmitEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

/**
 * 设备登出服务 —— 桥接代理，将登出请求转为事件提交到统一执行入口。
 *
 * <p>不执行校验和业务逻辑，仅发布 {@link DeviceLogoutSubmitEvent}，
 * 由 {@code DeviceSubmitEventListener} 统一消费执行。</p>
 */
@Slf4j
public class DeviceLogoutService {

    private final ApplicationEventPublisher eventPublisher;

    public DeviceLogoutService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 提交设备登出请求。
     *
     * @param deviceIds 待登出的设备 ID 列表
     */
    public void submit(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            log.warn("登出请求 deviceIds 为空，已忽略");
            return;
        }
        eventPublisher.publishEvent(new DeviceLogoutSubmitEvent(this, deviceIds));
        log.info("设备登出已桥接提交: deviceIds={}", deviceIds);
    }
}

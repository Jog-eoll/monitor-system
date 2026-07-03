package com.monitorplatform.device.config;

import com.monitorplatform.device.service.UnifiedDeviceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 设备状态定时检测任务
 */
@Component
public class DeviceStatusCheckTask {

    @Resource
    private UnifiedDeviceService unifiedDeviceService;

    /**
     * 统一检测所有设备状态（每分钟执行一次）
     * 仅更新在线/离线，告警状态由告警服务管理，不在此覆盖
     */
    @Scheduled(fixedRate = 60000)
    public void checkAllDeviceStatus() {
        unifiedDeviceService.checkAllDeviceStatus();
    }
}

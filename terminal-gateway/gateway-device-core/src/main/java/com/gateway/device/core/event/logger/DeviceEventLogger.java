package com.gateway.device.core.event.logger;

import com.gateway.device.core.event.bus.BatchCommandSubmitEvent;
import com.gateway.device.core.event.bus.IpRegistrationSubmitEvent;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 设备事件日志监听器 —— 消费所有设备相关事件，INFO 级别记录。
 */
@Slf4j
@Component
public class DeviceEventLogger {

    @EventListener
    public void onDeviceRegistered(DeviceRegisteredEvent event) {
        DeviceContext d = event.getDevice();
        log.info("[事件] 设备注册成功[{}]: DeviceId={} IP={} SN={} MAC={} 厂商={} 分组={} 来源={}",
                event.isNew() ? "新增" : "更新",
                d.getDeviceId(), d.getIp(), d.getSn(), d.getMacAddr(),
                d.getVendor(), d.getGroupLabel(), event.getSource());
    }

    @EventListener
    public void onBatchTaskSubmitted(BatchTaskSubmittedEvent event) {
        log.info("[事件] 批量任务[提交]: taskId={} 能力={} 设备数={}",
                event.getTaskId(), event.getCapability(), event.getDeviceCount());
    }

    @EventListener
    public void onBatchTaskCompleted(BatchTaskCompletedEvent event) {
        log.info("[事件] 批量任务[完成]: taskId={} 状态={}",
                event.getTaskId(), event.getStatus());
    }

    @EventListener
    public void onBatchCommandSubmit(BatchCommandSubmitEvent event) {
        log.info("[事件][总线] 批量命令提交: taskId={} 能力={}",
                event.getTaskId(), event.getRequest().getCapability());
    }

    @EventListener
    public void onIpRegistrationSubmit(IpRegistrationSubmitEvent event) {
        log.info("[事件][总线] IP注册提交: taskId={} 厂商={} IPs={}",
                event.getTaskId(), event.getRequest().getVendor(), event.getRequest().getIps());
    }

    @EventListener
    public void onDeviceLoggedOut(DeviceLoggedOutEvent event) {
        DeviceContext d = event.getDevice();
        log.info("[事件] 设备已登出: deviceId={} IP={} 厂商={}",
                d.getDeviceId(), d.getIp(), d.getVendor());
    }
}

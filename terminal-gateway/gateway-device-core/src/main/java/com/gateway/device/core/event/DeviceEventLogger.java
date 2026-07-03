package com.gateway.device.core.event;

import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备事件日志监听器 —— 消费所有设备相关事件，INFO 级别记录。
 */
@Slf4j
@Component
public class DeviceEventLogger {

    @EventListener
    public void onDeviceDiscovered(DeviceDiscoveredEvent event) {
        List<DeviceContext> devices = event.getDevices();
        log.info("[事件] 设备发现: {} 台", devices.size());
        for (DeviceContext d : devices) {
            log.info("[{}] 厂商={} 分组={} SN={}",
                    d.getIp(), d.getVendor(), d.getGroupLabel(),
                    d.getSn() != null ? d.getSn() : "-");
        }
    }

    @EventListener
    public void onBatchTaskSubmitted(BatchTaskSubmittedEvent event) {
        log.info("[事件] 批量任务提交: taskId={} 能力={} 设备数={}",
                event.getTaskId(), event.getCapability(), event.getDeviceCount());
    }

    @EventListener
    public void onBatchTaskCompleted(BatchTaskCompletedEvent event) {
        log.info("[事件] 批量任务完成: taskId={} 状态={}",
                event.getTaskId(), event.getStatus());
    }

    @EventListener
    public void onIpRegistrationSubmitted(IpRegistrationSubmittedEvent event) {
        log.info("[事件] IP注册任务提交: taskId={} 厂商={} IP数={}",
                event.getTaskId(), event.getVendor(), event.getIpCount());
    }

    @EventListener
    public void onIpRegistrationCompleted(IpRegistrationCompletedEvent event) {
        log.info("[事件] IP注册任务完成: taskId={} 状态={} 成功={}/{}",
                event.getTaskId(), event.getStatus(),
                event.getSuccessCount(), event.getTotal());
    }
}

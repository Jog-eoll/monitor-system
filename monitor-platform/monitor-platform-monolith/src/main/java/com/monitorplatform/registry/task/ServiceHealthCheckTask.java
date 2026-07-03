package com.monitorplatform.registry.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.registry.entity.ServiceInstance;
import com.monitorplatform.registry.mapper.ServiceInstanceMapper;
import com.monitorplatform.registry.service.ServiceRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class ServiceHealthCheckTask {

    private static final int HEARTBEAT_TIMEOUT_SECONDS = 30;
    private static final String STATUS_ONLINE = "在线";
    private static final String STATUS_OFFLINE = "离线";

    @Autowired
    private ServiceInstanceMapper serviceInstanceMapper;

    @Autowired
    private ServiceRegistryService serviceRegistryService;

    @Scheduled(fixedRate = 10000)
    public void checkServiceHealth() {
        try {
            LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ServiceInstance::getStatus, STATUS_ONLINE);
            List<ServiceInstance> instances = serviceInstanceMapper.selectList(wrapper);

            LocalDateTime timeoutThreshold = LocalDateTime.now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);
            for (ServiceInstance instance : instances) {
                if (instance.getLastOnlineTime() != null
                        && instance.getLastOnlineTime().isBefore(timeoutThreshold)) {
                    log.warn("service instance heartbeat timeout: deviceId={}", instance.getDeviceId());
                    serviceRegistryService.updateServiceStatus(instance.getDeviceId(), STATUS_OFFLINE);
                }
            }
        } catch (Exception e) {
            log.error("service health check failed", e);
        }
    }
}

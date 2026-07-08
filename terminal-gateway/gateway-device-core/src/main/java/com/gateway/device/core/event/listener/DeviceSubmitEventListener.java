package com.gateway.device.core.event.listener;

import com.gateway.device.core.event.bus.BatchCommandSubmitEvent;
import com.gateway.device.core.event.bus.DeviceLogoutSubmitEvent;
import com.gateway.device.core.event.bus.IpRegistrationSubmitEvent;
import com.gateway.device.core.event.logger.BatchTaskSubmittedEvent;
import com.gateway.device.core.event.logger.DeviceLoggedOutEvent;
import com.gateway.device.core.event.logger.DeviceRegisteredEvent;
import com.gateway.device.core.executor.DeviceCommandExecutor;
import com.gateway.device.core.executor.DeviceCommandFactory;
import com.gateway.device.core.router.ProtocolRouter;
import com.gateway.device.core.selector.DeviceSelectorResolver;
import com.gateway.device.core.service.AutoDiscoveryService;
import com.gateway.device.core.store.DeviceRegistryManager;
import com.gateway.device.core.task.InMemoryBatchTaskManager;
import com.gateway.device.core.task.InMemoryIpRegistrationTaskManager;
import com.gateway.device.protocol.api.DeviceRegistrationProvider;
import com.gateway.device.protocol.api.VendorProtocolAdapter;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.*;
import com.gateway.device.protocol.model.discovery.DeviceVendorMapping;
import com.gateway.device.protocol.model.discovery.ExplicitIpDiscoveredDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备提交事件监听器 —— 统一执行入口。
 *
 * <p>消费 Submit 类事件（{@link BatchCommandSubmitEvent}、{@link IpRegistrationSubmitEvent}），
 * 执行校验、设备解析、任务创建和业务下发逻辑。
 * 同时监听 {@link DeviceRegisteredEvent} 触发注册后业务下发。</p>
 *
 * <p>服务类模式（{@code Service.submit()}）和事件提交模式（直接发布事件）
 * 两条路径最终都在这里汇聚执行。</p>
 */
@Slf4j
public class DeviceSubmitEventListener {

    private final DeviceSelectorResolver selectorResolver;
    private final InMemoryBatchTaskManager batchTaskManager;
    private final DeviceCommandFactory commandFactory;
    private final DeviceCommandExecutor commandExecutor;
    private final ApplicationEventPublisher eventPublisher;

    private final AutoDiscoveryService autoDiscoveryService;
    private final DeviceRegistryManager deviceRegistry;
    private final InMemoryIpRegistrationTaskManager ipTaskManager;
    private final ProtocolRouter router;
    private final Map<DeviceVendor, DeviceRegistrationProvider> registrationProviders;
    private final ThreadPoolTaskExecutor executor;

    public DeviceSubmitEventListener(
            DeviceSelectorResolver selectorResolver,
            InMemoryBatchTaskManager batchTaskManager,
            DeviceCommandFactory commandFactory,
            DeviceCommandExecutor commandExecutor,
            ApplicationEventPublisher eventPublisher,
            AutoDiscoveryService autoDiscoveryService,
            DeviceRegistryManager deviceRegistry,
            InMemoryIpRegistrationTaskManager ipTaskManager,
            ProtocolRouter router,
            List<DeviceRegistrationProvider> regProviders,
            ThreadPoolTaskExecutor executor) {
        this.selectorResolver = selectorResolver;
        this.batchTaskManager = batchTaskManager;
        this.commandFactory = commandFactory;
        this.commandExecutor = commandExecutor;
        this.eventPublisher = eventPublisher;
        this.autoDiscoveryService = autoDiscoveryService;
        this.deviceRegistry = deviceRegistry;
        this.ipTaskManager = ipTaskManager;
        this.router = router;
        this.registrationProviders = new EnumMap<>(DeviceVendor.class);
        for (DeviceRegistrationProvider rp : regProviders) {
            registrationProviders.put(rp.vendor(), rp);
        }
        this.executor = executor;
    }

    // ════════════════════════════════════════════════════
    // 批量命令提交
    // ════════════════════════════════════════════════════

    @EventListener
    public void onBatchCommandSubmit(BatchCommandSubmitEvent event) {
        BatchCommandRequest request = event.getRequest();
        String taskId = event.getTaskId();

        try {
            List<DeviceContext> targets = selectorResolver.resolve(request.getSelector());

            // 按设备就绪状态过滤（在线 + 已登录）
            List<DeviceContext> readyTargets = targets.stream()
                    .filter(d -> {
                        VendorProtocolAdapter adapter = router.findByVendor(d.getVendor());
                        return adapter != null && adapter.isDeviceReady(d);
                    })
                    .collect(Collectors.toList());

            if (readyTargets.isEmpty()) {
                if (!targets.isEmpty() && request.getSelector().isExplicit()) {
                    // 显式指定设备全部未就绪 → 创建任务并逐台返回异常
                    log.warn("[事件] 显式指定设备全部未就绪: taskId={} 匹配数={}",
                            taskId, targets.size());
                    BatchTask task = batchTaskManager.createTask(request, targets, taskId);
                    for (DeviceContext device : targets) {
                        VendorProtocolAdapter adapter = router.findByVendor(device.getVendor());
                        String errorCode = adapter != null
                                ? adapter.getNotReadyErrorCode(device)
                                : StandardErrorCode.DEVICE_OFFLINE;
                        batchTaskManager.completeDevice(taskId, device.getDeviceId(),
                                CommandResult.failure(errorCode,
                                        "设备未就绪: " + device.getDeviceId()));
                    }
                } else {
                    log.warn("[事件] 批量命令无就绪设备: taskId={}", taskId);
                    batchTaskManager.createEmptyTask(request, taskId);
                    batchTaskManager.completeEmpty(taskId);
                }
                return;
            }

            BatchTask task = batchTaskManager.createTask(request, readyTargets, taskId);
            for (DeviceContext device : readyTargets) {
                DeviceCommand command = commandFactory.create(request, device);
                commandExecutor.submit(taskId, device, command);
            }

            log.info("[事件] 批量命令已提交: taskId={} 能力={} 设备数={}",
                    taskId, request.getCapability(), readyTargets.size());
            eventPublisher.publishEvent(new BatchTaskSubmittedEvent(
                    this, taskId, request.getCapability(), readyTargets.size()));

        } catch (Exception e) {
            log.error("[事件] 批量命令提交失败: taskId={}", taskId, e);
        }
    }

    // ════════════════════════════════════════════════════
    // IP 注册提交
    // ════════════════════════════════════════════════════

    @EventListener
    public void onIpRegistrationSubmit(IpRegistrationSubmitEvent event) {
        IpRegistrationRequest request = event.getRequest();
        String taskId = event.getTaskId();

        try {
            List<String> ips = request.getIps();
            DeviceVendor vendor = request.getVendor();

            if (ips == null || ips.isEmpty()) {
                log.warn("[事件] IP 注册请求 IP 列表为空: taskId={}", taskId);
                return;
            }
            if (vendor == null) {
                log.warn("[事件] IP 注册请求 vendor 为空: taskId={}", taskId);
                return;
            }

            VendorProtocolAdapter adapter = router.findByVendor(vendor);
            if (adapter == null) {
                log.warn("[事件] 厂商 {} 无可用协议适配器: taskId={}", vendor, taskId);
                return;
            }
            DeviceRegistrationProvider regProvider = registrationProviders.get(vendor);
            if (regProvider == null) {
                log.warn("[事件] 厂商 {} 无可用注册信息提供者: taskId={}", vendor, taskId);
                return;
            }
            if (!regProvider.supportsExplicitIp()) {
                log.warn("[事件] 厂商 {} 不支持显式 IP 注册模式: taskId={}", vendor, taskId);
                return;
            }

            DeviceVendorMapping mapping = new DeviceVendorMapping();
            mapping.setVendor(vendor);
            mapping.setPort(request.getPort());
            int effectivePort = mapping.effectivePort();

            IpRegistrationTask task = ipTaskManager.createTask(request, ips.size(), taskId);

            for (String ip : ips) {
                String targetIp = ip.trim();
                executor.execute(() -> executeOneIp(taskId, targetIp, vendor, effectivePort, mapping));
            }

            log.info("[事件] IP 注册任务已提交: taskId={} 厂商={} IP数={}",
                    taskId, vendor, ips.size());

        } catch (Exception e) {
            log.error("[事件] IP 注册提交失败: taskId={}", taskId, e);
        }
    }

    private void executeOneIp(String taskId, String ip, DeviceVendor vendor,
                              int port, DeviceVendorMapping mapping) {
        long start = System.currentTimeMillis();
        try {
            ExplicitIpDiscoveredDevice dd = new ExplicitIpDiscoveredDevice(ip, port);
            DeviceContext device = autoDiscoveryService.registerDevice(
                    dd, mapping, RegistrationSource.MANUAL_IP);
            long cost = System.currentTimeMillis() - start;

            IpRegistrationResult result;
            if (device != null) {
                result = IpRegistrationResult.success(ip, vendor, device.getDeviceId(), cost);
                log.info("[{}] IP 注册成功: deviceId={} cost={}ms", ip, device.getDeviceId(), cost);
            } else {
                result = IpRegistrationResult.failure(ip, vendor,
                        "REG_FAIL", "注册流水线返回 null");
                log.warn("[{}] IP 注册失败: 注册流水线返回 null", ip);
            }

            boolean last = ipTaskManager.completeIp(taskId, ip, result);
            if (last) {
                ipTaskManager.get(taskId).ifPresent(task ->
                        log.info("[事件] IP 注册任务完成: taskId={} 状态={} 成功={}/{}",
                                taskId, task.getStatus(),
                                task.getSuccessCount(), task.getTotal()));
            }
        } catch (Exception e) {
            log.error("[{}] IP 注册异常: {}", ip, e.getMessage(), e);
            IpRegistrationResult result = IpRegistrationResult.failure(ip, vendor,
                    "SYS_ERR", e.getMessage());
            boolean last = ipTaskManager.completeIp(taskId, ip, result);
            if (last) {
                ipTaskManager.get(taskId).ifPresent(
                        task -> log.info("[事件] IP 注册任务完成(异常): taskId={} 状态={} 成功={}/{}",
                                taskId, task.getStatus(),
                                task.getSuccessCount(), task.getTotal()));
            }
        }
    }

    // ════════════════════════════════════════════════════
    // 设备登出提交
    // ════════════════════════════════════════════════════

    @EventListener
    public void onDeviceLogoutSubmit(DeviceLogoutSubmitEvent event) {
        List<String> deviceIds = event.getDeviceIds();
        if (deviceIds == null || deviceIds.isEmpty()) {
            log.warn("[事件] 登出提交: deviceIds 为空");
            return;
        }

        for (String deviceId : deviceIds) {
            DeviceContext device = deviceRegistry.get(deviceId).orElse(null);
            if (device == null) {
                log.warn("[事件] 登出失败: 设备不存在 deviceId={}", deviceId);
                continue;
            }
            if (!device.isLoggedIn()) {
                log.debug("[事件] 设备未登录，跳过登出 deviceId={}", deviceId);
                continue;
            }

            DeviceRegistrationProvider regProvider = registrationProviders.get(device.getVendor());
            if (regProvider == null) {
                log.warn("[事件] 登出失败: 无注册提供者 vendor={} deviceId={}",
                        device.getVendor(), deviceId);
                continue;
            }

            boolean success = regProvider.logout(device);
            if (success) {
                deviceRegistry.markLoggedOut(deviceId);
                eventPublisher.publishEvent(new DeviceLoggedOutEvent(this, device));
            } else {
                log.warn("[事件] 登出失败: deviceId={} vendor={}", deviceId, device.getVendor());
            }
        }
    }
}

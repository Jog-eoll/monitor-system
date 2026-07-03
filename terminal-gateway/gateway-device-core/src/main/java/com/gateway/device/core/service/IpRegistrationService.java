package com.gateway.device.core.service;

import com.gateway.device.core.event.IpRegistrationCompletedEvent;
import com.gateway.device.core.event.IpRegistrationSubmittedEvent;
import com.gateway.device.core.router.ProtocolRouter;
import com.gateway.device.core.task.InMemoryIpRegistrationTaskManager;
import com.gateway.device.protocol.api.DeviceRegistrationProvider;
import com.gateway.device.protocol.api.VendorProtocolAdapter;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.IpRegistrationRequest;
import com.gateway.device.protocol.model.IpRegistrationResult;
import com.gateway.device.protocol.model.IpRegistrationTask;
import com.gateway.device.protocol.model.discovery.DeviceVendorMapping;
import com.gateway.device.protocol.model.discovery.ExplicitIpDiscoveredDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * IP 注册服务 —— 程序化按需注册设备，参考 {@link BatchCommandService} 模式。
 *
 * <p>接受 IP 列表 + 厂商 + 端口，异步执行完整的设备注册流水线
 * （信息获取 → 合规校验 → 属性补充 → 注册），并发布事件供外部监听。</p>
 */
@Slf4j
public class IpRegistrationService {

    private final AutoDiscoveryService autoDiscoveryService;
    private final InMemoryIpRegistrationTaskManager taskManager;
    private final ProtocolRouter router;
    private final Map<DeviceVendor, DeviceRegistrationProvider> registrationProviders;
    private final ThreadPoolTaskExecutor executor;
    private final ApplicationEventPublisher eventPublisher;

    public IpRegistrationService(AutoDiscoveryService autoDiscoveryService,
                                 InMemoryIpRegistrationTaskManager taskManager,
                                 ProtocolRouter router,
                                 List<DeviceRegistrationProvider> regProviders,
                                 ThreadPoolTaskExecutor executor,
                                 ApplicationEventPublisher eventPublisher) {
        this.autoDiscoveryService = autoDiscoveryService;
        this.taskManager = taskManager;
        this.router = router;
        this.registrationProviders = new EnumMap<>(DeviceVendor.class);
        for (DeviceRegistrationProvider rp : regProviders) {
            registrationProviders.put(rp.vendor(), rp);
        }
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 提交 IP 注册任务，每 IP 异步执行，立即返回任务对象。
     *
     * @param request 注册请求
     * @return 任务对象（含 taskId，状态为 RUNNING，可通过 {@link InMemoryIpRegistrationTaskManager#get} 轮询）
     */
    public IpRegistrationTask submit(IpRegistrationRequest request) {
        return doSubmit(request, null);
    }

    /**
     * 异步提交 IP 注册任务。
     *
     * <p>返回 CompletableFuture，当所有 IP 注册完成时完成；
     * 超时任务通过 {@code TaskHousekeeper} 扫描后以 {@code TimeoutException} 异常完成。</p>
     *
     * @param request 注册请求
     * @return 在全部 IP 注册完成后提供 IpRegistrationTask 的 CompletableFuture
     */
    public CompletableFuture<IpRegistrationTask> submitAsync(IpRegistrationRequest request) {
        CompletableFuture<IpRegistrationTask> future = new CompletableFuture<>();
        doSubmit(request, future);
        return future;
    }

    /**
     * 提交核心逻辑。
     *
     * @param future 非空时注册到 taskManager，任务完成/超时时自动完成；null 时仅返回 IpRegistrationTask
     */
    private IpRegistrationTask doSubmit(IpRegistrationRequest request,
                                        CompletableFuture<IpRegistrationTask> future) {
        // 1. 校验
        List<String> ips = request.getIps();
        DeviceVendor vendor = request.getVendor();
        if (ips == null || ips.isEmpty()) {
            log.warn("IP 注册请求 IP 列表为空");
            throw new IllegalArgumentException("ips 不能为空");
        }
        if (vendor == null) {
            log.warn("IP 注册请求 vendor 为空");
            throw new IllegalArgumentException("vendor 不能为空");
        }

        // 2. 前置校验：厂商是否具备 IP 注册所需组件（与 YAML ips 配置的注册流程一致）
        VendorProtocolAdapter adapter = router.findByVendor(vendor);
        if (adapter == null) {
            throw new IllegalArgumentException(
                    "厂商 " + vendor + " 无可用协议适配器，不支持 IP 注册");
        }
        DeviceRegistrationProvider regProvider = registrationProviders.get(vendor);
        if (regProvider == null) {
            throw new IllegalArgumentException(
                    "厂商 " + vendor + " 无可用注册信息提供者，不支持 IP 注册");
        }
        if (!regProvider.supportsExplicitIp()) {
            throw new IllegalArgumentException(
                    "厂商 " + vendor + " 基于 SDK 通道，不支持显式 IP 注册模式");
        }

        // 3. 解析端口（0 → 厂商默认）
        DeviceVendorMapping mapping = new DeviceVendorMapping();
        mapping.setVendor(vendor);
        mapping.setPort(request.getPort());
        int effectivePort = mapping.effectivePort();

        // 4. 创建任务
        IpRegistrationTask task = taskManager.createTask(request, ips.size());

        // 5. 注册 future
        if (future != null) {
            taskManager.registerFuture(task.getTaskId(), future);
            future.whenComplete((r, ex) -> taskManager.removeFuture(task.getTaskId()));
        }

        // 6. 每 IP 异步执行
        String taskId = task.getTaskId();
        for (String ip : ips) {
            String targetIp = ip.trim();
            executor.execute(() -> executeOneIp(taskId, targetIp, vendor, effectivePort, mapping));
        }

        // 7. 发布提交事件
        log.info("IP 注册任务 {} 已提交: 厂商={}, IP数={}", taskId, vendor, ips.size());
        eventPublisher.publishEvent(new IpRegistrationSubmittedEvent(
                this, taskId, vendor, ips.size()));
        return task;
    }

    /**
     * 单 IP 注册执行（运行在线程池上）
     */
    private void executeOneIp(String taskId, String ip, DeviceVendor vendor,
                              int port, DeviceVendorMapping mapping) {
        long start = System.currentTimeMillis();
        try {
            ExplicitIpDiscoveredDevice dd = new ExplicitIpDiscoveredDevice(ip, port);
            DeviceContext device = autoDiscoveryService.registerDevice(dd, mapping);
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

            boolean last = taskManager.completeIp(taskId, ip, result);
            if (last) {
                IpRegistrationTask task = taskManager.get(taskId).orElse(null);
                if (task != null) {
                    log.info("IP 注册任务 {} 完成: 状态={} 成功={}/{}",
                            taskId, task.getStatus(), task.getSuccessCount(), task.getTotal());
                    eventPublisher.publishEvent(new IpRegistrationCompletedEvent(
                            this, taskId, task.getStatus(),
                            task.getSuccessCount(), task.getFailedCount(), task.getTotal()));
                }
            }
        } catch (Exception e) {
            log.error("[{}] IP 注册异常: {}", ip, e.getMessage(), e);
            IpRegistrationResult result = IpRegistrationResult.failure(ip, vendor,
                    "SYS_ERR", e.getMessage());
            boolean last = taskManager.completeIp(taskId, ip, result);
            if (last) {
                taskManager.get(taskId).ifPresent(
                        task -> eventPublisher.publishEvent(new IpRegistrationCompletedEvent(
                                this, taskId, task.getStatus(),
                                task.getSuccessCount(), task.getFailedCount(), task.getTotal())));
            }
        }
    }
}

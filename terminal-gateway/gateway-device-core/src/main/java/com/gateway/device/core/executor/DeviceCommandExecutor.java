package com.gateway.device.core.executor;

import com.gateway.device.core.event.logger.BatchTaskCompletedEvent;
import com.gateway.device.core.router.ProtocolRouter;
import com.gateway.device.core.task.InMemoryBatchTaskManager;
import com.gateway.device.protocol.api.VendorProtocolAdapter;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.BatchTask;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceCommand;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * 设备命令执行器 —— 线程池异步执行，编排 route → adapter.execute。
 *
 * <p>同一设备串行化：每个 deviceId 对应一个 {@link DeviceSerialExecutor}，
 * 保证同一设备的指令严格 FIFO 顺序执行，不同设备可并行。</p>
 */
@Slf4j
public class DeviceCommandExecutor {
    private final ThreadPoolTaskExecutor executor;
    private final ProtocolRouter protocolRouter;
    private final InMemoryBatchTaskManager taskManager;
    private final ApplicationEventPublisher eventPublisher;
    private final ConcurrentMap<String, DeviceSerialExecutor> deviceExecutors = new ConcurrentHashMap<>();

    public DeviceCommandExecutor(ThreadPoolTaskExecutor executor,
                                 ProtocolRouter protocolRouter,
                                 InMemoryBatchTaskManager taskManager,
                                 ApplicationEventPublisher eventPublisher) {
        this.executor = executor;
        this.protocolRouter = protocolRouter;
        this.taskManager = taskManager;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 提交单台设备命令，同设备串行执行。
     */
    public void submit(String taskId, DeviceContext device, DeviceCommand command) {
        String deviceId = device.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            executor.execute(() -> execute(taskId, device, command));
            return;
        }
        DeviceSerialExecutor serialExec = deviceExecutors.computeIfAbsent(
                deviceId, k -> new DeviceSerialExecutor(executor));
        serialExec.execute(() -> execute(taskId, device, command));
    }

    /**
     * 查询设备是否正在执行任务或队列中有待执行任务。
     */
    public boolean isBusy(String deviceId) {
        if (deviceId == null) return false;
        DeviceSerialExecutor exec = deviceExecutors.get(deviceId);
        return exec != null && exec.hasPending();
    }

    private void execute(String taskId, DeviceContext device, DeviceCommand command) {
        DeviceCapability<?> cap = command.getCapability();
        if (cap == null) {
            recordAndMaybeFire(taskId, device.getDeviceId(),
                    CommandResult.failure(StandardErrorCode.INVALID_PARAM, "未指定能力"));
            return;
        }

        try {
            VendorProtocolAdapter adapter = protocolRouter.route(device, cap);
            DeviceCommand cmd = DeviceCommand.builder()
                    .taskId(taskId)
                    .capability(cap)
                    .target(device)
                    .params(command.getParams())
                    .build();
            CommandResult result = adapter.execute(device, cmd);
            recordAndMaybeFire(taskId, device.getDeviceId(), result);
        } catch (Exception e) {
            log.warn("设备 {} 命令执行失败: {}", device.getDeviceId(), e.getMessage());
            recordAndMaybeFire(taskId, device.getDeviceId(),
                    CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage()));
        }
    }

    private void recordAndMaybeFire(String taskId, String deviceId, CommandResult result) {
        boolean isLast = taskManager.completeDevice(taskId, deviceId, result);
        if (isLast) {
            BatchTask task = taskManager.get(taskId).orElse(null);
            if (task != null) {
                BatchTaskCompletedEvent event = new BatchTaskCompletedEvent(
                        this, taskId, task.getStatus());
                eventPublisher.publishEvent(event);
                log.info("批量任务 {} 完成: status={} success={}/{}",
                        taskId, task.getStatus(), task.getSuccessCount(), task.getTotal());
            }
        }
    }

    // ════════════════════════════════════════════════════
    // 设备串行执行器
    // ════════════════════════════════════════════════════

    /**
     * 单设备 FIFO 串行执行器 —— 保证同一设备的指令严格按顺序执行。
     *
     * <p>任务入队后若当前无活跃任务则立即调度到共享线程池。
     * 任务完成后自动调度下一个，无任务时空闲不占线程。</p>
     */
    private static class DeviceSerialExecutor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private final Executor delegate;
        private Runnable active;

        DeviceSerialExecutor(Executor delegate) {
            this.delegate = delegate;
        }

        synchronized void execute(Runnable r) {
            tasks.offer(() -> {
                try {
                    r.run();
                } finally {
                    scheduleNext();
                }
            });
            if (active == null) {
                scheduleNext();
            }
        }

        synchronized void scheduleNext() {
            active = tasks.poll();
            if (active != null) {
                delegate.execute(active);
            }
        }

        synchronized boolean hasPending() {
            return active != null || !tasks.isEmpty();
        }
    }
}

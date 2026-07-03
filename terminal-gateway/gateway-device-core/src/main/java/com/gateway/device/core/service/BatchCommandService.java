package com.gateway.device.core.service;

import com.gateway.device.core.event.BatchTaskSubmittedEvent;
import com.gateway.device.core.executor.DeviceCommandExecutor;
import com.gateway.device.core.executor.DeviceCommandFactory;
import com.gateway.device.core.selector.DeviceSelectorResolver;
import com.gateway.device.core.task.InMemoryBatchTaskManager;
import com.gateway.device.protocol.model.BatchCommandRequest;
import com.gateway.device.protocol.model.BatchTask;
import com.gateway.device.protocol.model.DeviceCommand;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 批量命令服务 —— 统一入口，编排筛选→拆分→提交。
 */
@Slf4j
public class BatchCommandService {
    private final DeviceSelectorResolver selectorResolver;
    private final InMemoryBatchTaskManager taskManager;
    private final DeviceCommandFactory commandFactory;
    private final DeviceCommandExecutor commandExecutor;
    private final ApplicationEventPublisher eventPublisher;

    public BatchCommandService(DeviceSelectorResolver selectorResolver,
                               InMemoryBatchTaskManager taskManager,
                               DeviceCommandFactory commandFactory,
                               DeviceCommandExecutor commandExecutor,
                               ApplicationEventPublisher eventPublisher) {
        this.selectorResolver = selectorResolver;
        this.taskManager = taskManager;
        this.commandFactory = commandFactory;
        this.commandExecutor = commandExecutor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 提交批量指令。
     *
     * @param request 批量指令请求
     * @return 批量任务（含 taskId，供后续查询）
     */
    public BatchTask submit(BatchCommandRequest request) {
        return doSubmit(request, null);
    }

    /**
     * 异步提交批量指令。
     *
     * <p>返回 CompletableFuture，当所有设备命令执行完毕时完成；
     * 超时任务通过 TaskHousekeeper 扫描后以 TimeoutException 异常完成。</p>
     *
     * @param request 批量指令请求
     * @return 在任务完成时提供 BatchTask 的 CompletableFuture
     */
    public CompletableFuture<BatchTask> submitAsync(BatchCommandRequest request) {
        CompletableFuture<BatchTask> future = new CompletableFuture<>();
        doSubmit(request, future);
        return future;
    }

    /**
     * 提交批量指令核心逻辑。
     *
     * @param future 非空时注册到 taskManager，任务完成/超时时自动完成；null 时仅返回 BatchTask
     */
    private BatchTask doSubmit(BatchCommandRequest request, CompletableFuture<BatchTask> future) {
        List<DeviceContext> targets = selectorResolver.resolve(request.getSelector());

        if (targets.isEmpty()) {
            log.warn("请求 {} 无匹配设备", request.getRequestId());
            BatchTask task = taskManager.createEmptyTask(request);
            if (future != null) {
                future.complete(task);
            }
            return task;
        }

        BatchTask task = taskManager.createTask(request, targets);

        if (future != null) {
            taskManager.registerFuture(task.getTaskId(), future);
            future.whenComplete((r, ex) -> taskManager.removeFuture(task.getTaskId()));
        }

        for (DeviceContext device : targets) {
            DeviceCommand command = commandFactory.create(request, device);
            commandExecutor.submit(task.getTaskId(), device, command);
        }

        log.info("批量任务 {} 已提交: 能力={}, 设备数={}", task.getTaskId(), request.getCapability(), targets.size());
        eventPublisher.publishEvent(new BatchTaskSubmittedEvent(
                this, task.getTaskId(), request.getCapability(), targets.size()));
        return task;
    }
}

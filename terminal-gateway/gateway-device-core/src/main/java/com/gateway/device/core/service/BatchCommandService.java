package com.gateway.device.core.service;

import com.gateway.device.core.event.bus.BatchCommandSubmitEvent;
import com.gateway.device.core.task.InMemoryBatchTaskManager;
import com.gateway.device.protocol.model.BatchCommandRequest;
import com.gateway.device.protocol.model.BatchTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 批量命令服务 —— 桥接代理，将请求转为事件提交到统一执行入口。
 *
 * <p>不执行校验和业务逻辑，仅创建 {@link CompletableFuture} 并发布
 * {@link BatchCommandSubmitEvent}，由 {@code DeviceSubmitEventListener} 统一消费执行。</p>
 */
@Slf4j
public class BatchCommandService {

    private final InMemoryBatchTaskManager taskManager;
    private final ApplicationEventPublisher eventPublisher;

    public BatchCommandService(InMemoryBatchTaskManager taskManager,
                               ApplicationEventPublisher eventPublisher) {
        this.taskManager = taskManager;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 同步提交批量指令，阻塞等待任务完成。
     *
     * @param request 批量指令请求
     * @return 已完成的 BatchTask
     */
    public BatchTask submit(BatchCommandRequest request) throws InterruptedException, ExecutionException {
        return submitAsync(request).get();
    }

    /**
     * 异步提交批量指令。
     *
     * @param request 批量指令请求
     * @return 在任务完成时提供 BatchTask 的 CompletableFuture
     */
    public CompletableFuture<BatchTask> submitAsync(BatchCommandRequest request) {
        final String taskId = request.getRequestId() != null
                ? request.getRequestId() : UUID.randomUUID().toString();
        CompletableFuture<BatchTask> future = new CompletableFuture<>();
        taskManager.registerFuture(taskId, future);
        future.whenComplete((r, ex) -> taskManager.removeFuture(taskId));
        eventPublisher.publishEvent(new BatchCommandSubmitEvent(this, request, taskId));
        log.info("批量命令已桥接提交: taskId={}", taskId);
        return future;
    }
}

package com.gateway.device.core.service;

import com.gateway.device.core.event.bus.IpRegistrationSubmitEvent;
import com.gateway.device.core.task.InMemoryIpRegistrationTaskManager;
import com.gateway.device.protocol.model.IpRegistrationRequest;
import com.gateway.device.protocol.model.IpRegistrationTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * IP 注册服务 —— 桥接代理，将请求转为事件提交到统一执行入口。
 *
 * <p>不执行校验和业务逻辑，仅创建 {@link CompletableFuture} 并发布
 * {@link IpRegistrationSubmitEvent}，由 {@code DeviceSubmitEventListener} 统一消费执行。</p>
 */
@Slf4j
public class IpRegistrationService {

    private final InMemoryIpRegistrationTaskManager taskManager;
    private final ApplicationEventPublisher eventPublisher;

    public IpRegistrationService(InMemoryIpRegistrationTaskManager taskManager,
                                 ApplicationEventPublisher eventPublisher) {
        this.taskManager = taskManager;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 同步提交 IP 注册任务，阻塞等待完成。
     *
     * @param request 注册请求
     * @return 已完成的 IpRegistrationTask
     */
    public IpRegistrationTask submit(IpRegistrationRequest request) throws InterruptedException, ExecutionException {
        return submitAsync(request).get();
    }

    /**
     * 异步提交 IP 注册任务。
     *
     * @param request 注册请求
     * @return 在任务完成时提供 IpRegistrationTask 的 CompletableFuture
     */
    public CompletableFuture<IpRegistrationTask> submitAsync(IpRegistrationRequest request) {
        final String taskId = request.getRequestId() != null
                ? request.getRequestId() : UUID.randomUUID().toString();
        CompletableFuture<IpRegistrationTask> future = new CompletableFuture<>();
        taskManager.registerFuture(taskId, future);
        future.whenComplete((r, ex) -> taskManager.removeFuture(taskId));
        eventPublisher.publishEvent(new IpRegistrationSubmitEvent(this, request, taskId));
        log.info("IP 注册已桥接提交: taskId={}", taskId);
        return future;
    }
}

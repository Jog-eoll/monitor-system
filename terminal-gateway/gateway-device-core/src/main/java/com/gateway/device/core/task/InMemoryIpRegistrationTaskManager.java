package com.gateway.device.core.task;

import com.gateway.device.protocol.model.IpRegistrationRequest;
import com.gateway.device.protocol.model.IpRegistrationResult;
import com.gateway.device.protocol.model.IpRegistrationTask;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;

/**
 * 内存 IP 注册任务管理器 —— 创建/查询/更新/清理 {@link IpRegistrationTask}。
 */
public class InMemoryIpRegistrationTaskManager {

    private final ConcurrentMap<String, IpRegistrationTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<IpRegistrationTask>> futures = new ConcurrentHashMap<>();

    /**
     * 创建 IP 注册任务
     */
    public IpRegistrationTask createTask(IpRegistrationRequest request, int ipCount) {
        String taskId = request.getRequestId() != null
                ? request.getRequestId()
                : UUID.randomUUID().toString();
        IpRegistrationTask task = new IpRegistrationTask(taskId, request.getVendor(), ipCount);
        tasks.put(taskId, task);
        return task;
    }

    /**
     * 注册任务对应的 CompletableFuture，任务完成或超时时自动完成。
     */
    public void registerFuture(String taskId, CompletableFuture<IpRegistrationTask> future) {
        futures.put(taskId, future);
    }

    /**
     * 手动移除 future 注册（兜底清理）。
     */
    public void removeFuture(String taskId) {
        futures.remove(taskId);
    }

    /**
     * 查询任务
     */
    public Optional<IpRegistrationTask> get(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * 记录单个 IP 注册结果。
     *
     * @return true 表示此 IP 是最后一个，注册任务刚刚全部完成
     */
    public boolean completeIp(String taskId, String ip, IpRegistrationResult result) {
        boolean[] finished = {false};
        tasks.computeIfPresent(taskId, (id, task) -> {
            finished[0] = task.completeIp(ip, result);
            return task;
        });
        if (finished[0]) {
            CompletableFuture<IpRegistrationTask> future = futures.remove(taskId);
            if (future != null) {
                tasks.computeIfPresent(taskId, (id, task) -> {
                    future.complete(task);
                    return task;
                });
            }
        }
        return finished[0];
    }

    /**
     * 标记超时任务（超过 maxAge 未完成）
     */
    public void markTimeoutBefore(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        tasks.forEach((id, task) -> {
            if (task.getCreatedAt().isBefore(cutoff) && !task.isFinished()) {
                task.markTimeout();
                CompletableFuture<IpRegistrationTask> future = futures.remove(id);
                if (future != null) {
                    future.completeExceptionally(
                            new TimeoutException("IP 注册任务超时: " + id));
                }
            }
        });
    }

    /**
     * 删除已完成且超过指定时间的任务
     */
    public void removeFinishedBefore(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        tasks.entrySet().removeIf(entry -> {
            IpRegistrationTask task = entry.getValue();
            return task.isFinished() && task.getCreatedAt().isBefore(cutoff);
        });
    }

    /**
     * 删除指定任务
     */
    public void remove(String taskId) {
        tasks.remove(taskId);
    }

    /**
     * 活跃任务数
     */
    public int activeCount() {
        return tasks.size();
    }

    public void clear() {
        tasks.clear();
    }
}

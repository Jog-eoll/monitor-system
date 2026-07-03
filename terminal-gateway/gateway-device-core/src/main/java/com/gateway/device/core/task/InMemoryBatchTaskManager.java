package com.gateway.device.core.task;

import com.gateway.device.protocol.model.BatchCommandRequest;
import com.gateway.device.protocol.model.BatchTask;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;

/**
 * 内存批量任务管理器 —— 创建/查询/更新/清理 BatchTask。
 */
public class InMemoryBatchTaskManager {

    private final ConcurrentMap<String, BatchTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<BatchTask>> futures = new ConcurrentHashMap<>();

    /**
     * 创建批量任务
     */
    public BatchTask createTask(BatchCommandRequest request, List<DeviceContext> targets) {
        String taskId = request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString();
        BatchTask task = new BatchTask(taskId, request.getCapability(), targets.size());
        tasks.put(taskId, task);
        return task;
    }

    /**
     * 创建空任务（无匹配设备）
     */
    public BatchTask createEmptyTask(BatchCommandRequest request) {
        String taskId = request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString();
        BatchTask task = new BatchTask(taskId, request.getCapability(), 0);
        tasks.put(taskId, task);
        return task;
    }

    /**
     * 注册任务对应的 CompletableFuture，任务完成或超时时自动移除并完成。
     */
    public void registerFuture(String taskId, CompletableFuture<BatchTask> future) {
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
    public Optional<BatchTask> get(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * 记录单台设备结果。
     *
     * @return true 表示此设备是最后一个，批量任务刚刚全部完成
     */
    public boolean completeDevice(String taskId, String deviceId, CommandResult result) {
        boolean[] finished = {false};
        tasks.computeIfPresent(taskId, (id, task) -> {
            finished[0] = task.completeDevice(deviceId, result);
            return task;
        });
        if (finished[0]) {
            CompletableFuture<BatchTask> future = futures.remove(taskId);
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
     * 标记超时任务
     */
    public void markTimeoutBefore(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        tasks.forEach((id, task) -> {
            if (task.getCreatedAt().isBefore(cutoff) && !task.isFinished()) {
                task.markTimeout();
                CompletableFuture<BatchTask> future = futures.remove(id);
                if (future != null) {
                    future.completeExceptionally(
                            new TimeoutException("批量任务超时: " + id));
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
            BatchTask task = entry.getValue();
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
     * 获取所有任务 ID
     */
    public int activeCount() {
        return tasks.size();
    }

    public void clear() {
        tasks.clear();
    }
}

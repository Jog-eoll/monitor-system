package com.gateway.device.protocol.model;

import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import lombok.Getter;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量任务 —— 内存任务对象，聚合所有设备级结果。
 */
public class BatchTask {

    @Getter
    private final String taskId;
    @Getter
    private final DeviceCapability<?> capability;
    @Getter
    private final int total;
    @Getter
    private final Instant createdAt;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final ConcurrentMap<String, DeviceCommandResult> results = new ConcurrentHashMap<>();
    private final AtomicBoolean finalized = new AtomicBoolean(false);
    private volatile BatchTaskStatus status;

    public BatchTask(String taskId, DeviceCapability<?> capability, int total) {
        this.taskId = taskId;
        this.capability = capability;
        this.total = total;
        this.createdAt = Instant.now();
        this.status = BatchTaskStatus.RUNNING;
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    public BatchTaskStatus getStatus() {
        if (status == BatchTaskStatus.RUNNING && isFinished()) {
            status = calculateStatus();
        }
        return status;
    }

    public Collection<DeviceCommandResult> getResults() {
        return Collections.unmodifiableCollection(results.values());
    }

    /**
     * 记录单个设备结果。
     *
     * @return true 表示此任务是最后一个设备，批量任务刚刚完成
     */
    public boolean completeDevice(String deviceId, CommandResult result) {
        DeviceCommandResult deviceResult = DeviceCommandResult.from(
                deviceId, null, result);
        results.put(deviceId, deviceResult);
        if (result.isSuccess()) {
            successCount.incrementAndGet();
        } else {
            failedCount.incrementAndGet();
        }
        if (isFinished() && finalized.compareAndSet(false, true)) {
            status = calculateStatus();
            return true;
        }
        return false;
    }

    public void markTimeout() {
        this.status = BatchTaskStatus.TIMEOUT;
    }

    public boolean isFinished() {
        return successCount.get() + failedCount.get() >= total;
    }

    private BatchTaskStatus calculateStatus() {
        if (total <= 0) return BatchTaskStatus.FAILED;
        if (successCount.get() == total) return BatchTaskStatus.SUCCESS;
        if (failedCount.get() == total) return BatchTaskStatus.FAILED;
        return BatchTaskStatus.PARTIAL_SUCCESS;
    }
}

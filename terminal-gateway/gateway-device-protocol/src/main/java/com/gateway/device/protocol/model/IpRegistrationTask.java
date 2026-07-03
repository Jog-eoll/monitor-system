package com.gateway.device.protocol.model;

import com.gateway.device.protocol.common.constant.DeviceVendor;
import lombok.Getter;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP 注册任务 —— 内存任务对象，聚合所有 IP 级注册结果。
 *
 * <p>线程安全：使用 {@link ConcurrentHashMap} + {@link AtomicInteger} +
 * {@link AtomicBoolean} + {@code volatile} 保证并发完成时的正确性。</p>
 */
public class IpRegistrationTask {

    @Getter
    private final String taskId;
    @Getter
    private final DeviceVendor vendor;
    @Getter
    private final int total;
    @Getter
    private final Instant createdAt;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final ConcurrentMap<String, IpRegistrationResult> results = new ConcurrentHashMap<>();
    private final AtomicBoolean finalized = new AtomicBoolean(false);
    private volatile BatchTaskStatus status;

    public IpRegistrationTask(String taskId, DeviceVendor vendor, int total) {
        this.taskId = taskId;
        this.vendor = vendor;
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

    public Collection<IpRegistrationResult> getResults() {
        return Collections.unmodifiableCollection(results.values());
    }

    /**
     * 记录单个 IP 注册结果。
     *
     * @return true 表示此 IP 是最后一个，注册任务刚刚全部完成
     */
    public boolean completeIp(String ip, IpRegistrationResult result) {
        results.put(ip, result);
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

    /**
     * 标记为超时
     */
    public void markTimeout() {
        this.status = BatchTaskStatus.TIMEOUT;
    }

    /**
     * 是否所有 IP 都已完成（成功或失败）
     */
    public boolean isFinished() {
        return successCount.get() + failedCount.get() >= total;
    }

    private BatchTaskStatus calculateStatus() {
        if (successCount.get() == total) return BatchTaskStatus.SUCCESS;
        if (failedCount.get() == total) return BatchTaskStatus.FAILED;
        return BatchTaskStatus.PARTIAL_SUCCESS;
    }
}

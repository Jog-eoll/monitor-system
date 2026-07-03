package com.gateway.device.core.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/**
 * 任务管家 —— 定时扫描超时任务，清理过期任务。
 */
@Slf4j
public class TaskHousekeeper {

    private final InMemoryBatchTaskManager taskManager;
    private final InMemoryIpRegistrationTaskManager ipTaskManager;

    public TaskHousekeeper(InMemoryBatchTaskManager taskManager,
                           InMemoryIpRegistrationTaskManager ipTaskManager) {
        this.taskManager = taskManager;
        this.ipTaskManager = ipTaskManager;
    }

    /**
     * 每 10 秒扫描超时任务（超过 60 秒未完成标记为超时）
     */
    @Scheduled(fixedDelay = 10_000)
    public void scanTimeoutTasks() {
        taskManager.markTimeoutBefore(Duration.ofSeconds(60));
        ipTaskManager.markTimeoutBefore(Duration.ofSeconds(60));
    }

    /**
     * 每分钟清理已完成超过 30 分钟的任务
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanExpiredTasks() {
        int before = taskManager.activeCount() + ipTaskManager.activeCount();
        taskManager.removeFinishedBefore(Duration.ofMinutes(30));
        ipTaskManager.removeFinishedBefore(Duration.ofMinutes(30));
        int after = taskManager.activeCount() + ipTaskManager.activeCount();
        if (before > after) {
            log.debug("清理过期任务: {} → {}", before, after);
        }
    }
}

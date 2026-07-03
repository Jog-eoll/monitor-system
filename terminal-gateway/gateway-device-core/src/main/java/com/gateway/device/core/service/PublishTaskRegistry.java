package com.gateway.device.core.service;

import com.gateway.device.core.controller.dto.SecureCommandResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 发布编排任务登记表 —— 记录每次发布编排的最终响应，供
 * {@code GET /api/secure-command/publish-tasks/{orchestrationTaskId}} 查询。
 * <p>
 * 与控制指令的 {@code InMemoryBatchTaskManager} 不同，发布包是多步骤同步编排，
 * 没有单一 BatchTask，因此单独维护一份以 orchestrationTaskId 为键的快照。
 * </p>
 */
@Slf4j
@Component
public class PublishTaskRegistry {

    private static final int MAX_ENTRIES = 5_000;

    @Value("${secure-command.publish-task.ttl-minutes:60}")
    private int ttlMinutes;

    private final ConcurrentMap<String, Entry> store = new ConcurrentHashMap<>();

    public void record(SecureCommandResponse response) {
        if (response == null || response.getOrchestrationTaskId() == null) {
            return;
        }
        if (store.size() >= MAX_ENTRIES) {
            evictExpired();
        }
        Instant expireAt = Instant.now().plus(Duration.ofMinutes(Math.max(1, ttlMinutes)));
        store.put(response.getOrchestrationTaskId(), new Entry(response, expireAt));
    }

    public Optional<SecureCommandResponse> find(String orchestrationTaskId) {
        if (orchestrationTaskId == null || orchestrationTaskId.isEmpty()) {
            return Optional.empty();
        }
        Entry entry = store.get(orchestrationTaskId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(Instant.now())) {
            store.remove(orchestrationTaskId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.response);
    }

    public int evictExpired() {
        Instant now = Instant.now();
        int removed = 0;
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> e = it.next();
            if (e.getValue().isExpired(now)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    public int size() {
        return store.size();
    }

    private static final class Entry {
        final SecureCommandResponse response;
        final Instant expireAt;

        Entry(SecureCommandResponse response, Instant expireAt) {
            this.response = response;
            this.expireAt = expireAt;
        }

        boolean isExpired(Instant now) {
            return now.isAfter(expireAt);
        }
    }
}

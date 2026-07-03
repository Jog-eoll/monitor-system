package com.gateway.device.core.service;

import com.gateway.device.core.controller.dto.SecureCommandResponse;
import com.gateway.device.core.controller.dto.SecureControlResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 密文指令短期幂等缓存。
 * <p>
 * 加密网关 HTTP 超时重试时会重复下发设备命令，本缓存以 {@code requestId}
 * （兜底 {@code deliveryTaskId} / {@code commandTaskId}）为键记录响应摘要，
 * TTL 内重复命中直接回放原结果，不再触发 BatchCommandService 下发。
 * </p>
 *
 * <p>实现为进程内 {@link ConcurrentHashMap}，配合定期清理；不引入新依赖。
 * 命中后回放的是上一次的响应快照（accepted/status/message/任务 ID），
 * 不保证子步骤实时状态，调用方如需最新状态应走任务查询接口。</p>
 */
@Slf4j
@Component
public class SecureCommandIdempotencyCache {

    /** 默认 TTL：15 分钟，落在文档建议的 10~30 分钟区间 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    /** 单缓存条目数上限，避免异常刷量导致内存膨胀 */
    private static final int MAX_ENTRIES = 10_000;

    @Value("${secure-command.idempotency.ttl-minutes:15}")
    private int ttlMinutes;

    private final ConcurrentMap<String, CachedResponse> store = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("[幂等缓存] 初始化: ttlMinutes={}, maxEntries={}", ttlMinutes, MAX_ENTRIES);
    }

    /**
     * 记录一次发布包响应，key 由调用方按 requestId/deliveryTaskId 优先级提供。
     */
    public void putPublish(String key, SecureCommandResponse response) {
        cache(key, response != null && response.isAccepted(), response);
    }

    /**
     * 记录一次控制指令响应。
     */
    public void putControl(String key, SecureControlResponse response) {
        cache(key, response != null && response.isAccepted(), response);
    }

    /**
     * 查询发布包响应快照。
     */
    public Optional<SecureCommandResponse> findPublish(String key) {
        return find(key).map(entry -> {
            // 仅在缓存且被接受时回放；被拒绝的请求允许重试，不缓存拒绝结果
            if (!entry.accepted) {
                return null;
            }
            SecureCommandResponse replay = clonePublish(entry);
            return replay;
        });
    }

    /**
     * 查询控制指令响应快照。
     */
    public Optional<SecureControlResponse> findControl(String key) {
        return find(key).map(entry -> {
            if (!entry.accepted) {
                return null;
            }
            return cloneControl(entry);
        });
    }

    /**
     * 清理过期条目。由调用方周期触发（例如定时任务）。
     */
    public int evictExpired() {
        Instant now = Instant.now();
        int removed = 0;
        Iterator<Map.Entry<String, CachedResponse>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CachedResponse> e = it.next();
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

    // ──────────────── 内部实现 ────────────────

    private void cache(String key, boolean accepted, Object response) {
        if (key == null || key.isEmpty() || response == null) {
            return;
        }
        // 仅缓存被接受的终态；拒绝/异常允许重试
        if (!accepted) {
            return;
        }
        if (store.size() >= MAX_ENTRIES) {
            evictExpired();
            if (store.size() >= MAX_ENTRIES) {
                log.warn("[幂等缓存] 已达上限 {}，跳过缓存: key={}", MAX_ENTRIES, key);
                return;
            }
        }
        Instant expireAt = Instant.now().plus(Duration.ofMinutes(Math.max(1, ttlMinutes)));
        store.put(key, new CachedResponse(response, accepted, expireAt));
    }

    private Optional<CachedResponse> find(String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        CachedResponse entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(Instant.now())) {
            store.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @SuppressWarnings("unchecked")
    private SecureCommandResponse clonePublish(CachedResponse entry) {
        Object raw = entry.response;
        if (raw instanceof SecureCommandResponse) {
            SecureCommandResponse origin = (SecureCommandResponse) raw;
            // 回放快照：保留任务 ID 与能力，message 标注为幂等回放
            SecureCommandResponse replay = new SecureCommandResponse();
            replay.setAccepted(true);
            replay.setDeliveryTaskId(origin.getDeliveryTaskId());
            replay.setOrchestrationTaskId(origin.getOrchestrationTaskId());
            replay.setRequestId(origin.getRequestId());
            replay.setStatus(origin.getStatus());
            replay.setCode(origin.getCode());
            replay.setMessage("幂等命中，回放上一次结果");
            return replay;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private SecureControlResponse cloneControl(CachedResponse entry) {
        Object raw = entry.response;
        if (raw instanceof SecureControlResponse) {
            SecureControlResponse origin = (SecureControlResponse) raw;
            SecureControlResponse replay = new SecureControlResponse();
            replay.setAccepted(true);
            replay.setCommandTaskId(origin.getCommandTaskId());
            replay.setBatchTaskId(origin.getBatchTaskId());
            replay.setRequestId(origin.getRequestId());
            replay.setMappedCapability(origin.getMappedCapability());
            replay.setStatus(origin.getStatus());
            replay.setCode(origin.getCode());
            replay.setMessage("幂等命中，回放上一次结果");
            return replay;
        }
        return null;
    }

    private static final class CachedResponse {
        final Object response;
        final boolean accepted;
        final Instant expireAt;

        CachedResponse(Object response, boolean accepted, Instant expireAt) {
            this.response = response;
            this.accepted = accepted;
            this.expireAt = expireAt;
        }

        boolean isExpired(Instant now) {
            return now.isAfter(expireAt);
        }
    }
}

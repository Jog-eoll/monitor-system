package com.publishgateway.udpproxy.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令幂等记录服务 —— 基于 messageId 的去重，避免重复执行同一条下行命令。
 * <p>
 * v1 使用进程内 ConcurrentHashMap，单实例部署足够。多实例横向扩展时
 * 可替换为 Redis SETNX 实现。
 * </p>
 */
@Slf4j
@Component
public class MqttCommandRecordService {

    @Resource
    private MqttAgentProperties properties;

    /** 已处理 messageId 集合（value 为首次处理时间戳） */
    private final ConcurrentHashMap<String, Long> processedMessageIds = new ConcurrentHashMap<>();

    /**
     * 判断该 messageId 是否已处理过。
     *
     * @param messageId 消息 ID
     * @return true 表示已处理
     */
    public boolean isProcessed(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return false;
        }
        Long firstSeenAt = processedMessageIds.get(messageId);
        if (firstSeenAt == null) {
            return false;
        }
        if (isExpired(firstSeenAt, System.currentTimeMillis())) {
            processedMessageIds.remove(messageId, firstSeenAt);
            return false;
        }
        return true;
    }

    /**
     * 标记 messageId 为已处理。
     *
     * @param messageId 消息 ID
     * @return true 表示本次为首次标记（之前不存在）；false 表示已存在
     */
    public boolean markProcessed(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return false;
        }
        Long prev = processedMessageIds.putIfAbsent(messageId, System.currentTimeMillis());
        return prev == null;
    }

    /**
     * 当前已记录的消息数量。
     *
     * @return 已处理 messageId 数量
     */
    public int processedCount() {
        return processedMessageIds.size();
    }

    /**
     * 定期清理过期幂等记录，避免长期运行内存无限增长。
     */
    @Scheduled(fixedDelayString = "${mqtt-agent.dedup-cleanup-interval-ms:600000}")
    public void cleanupExpiredRecords() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Map.Entry<String, Long> entry : processedMessageIds.entrySet()) {
            Long firstSeenAt = entry.getValue();
            if (firstSeenAt != null && isExpired(firstSeenAt, now)
                    && processedMessageIds.remove(entry.getKey(), firstSeenAt)) {
                removed++;
            }
        }
        if (removed > 0) {
            log.info("[CMD] 已清理过期幂等记录: removed={}, remaining={}",
                    removed, processedMessageIds.size());
        }
    }

    private boolean isExpired(long firstSeenAt, long now) {
        long ttlMs = Math.max(60000L, properties.getCommandDedupTtlMs());
        return now - firstSeenAt > ttlMs;
    }
}

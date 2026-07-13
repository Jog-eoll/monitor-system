package com.publishgateway.udpproxy.mqtt;

import com.publishgateway.udpproxy.entity.MqttCommandRecord;
import com.publishgateway.udpproxy.mapper.MqttCommandRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关命令幂等服务 —— 内存缓存加速 + DB 唯一键持久化双层防重。
 * <p>
 * 容器重启后内存缓存丢失，但 DB 唯一键仍能防止重复 messageId 重复执行副作用。
 * </p>
 */
@Slf4j
@Component
public class MqttCommandRecordService {

    @Resource
    private MqttAgentProperties properties;

    @Resource
    private MqttCommandRecordMapper mqttCommandRecordMapper;

    private final ConcurrentHashMap<String, Long> processedMessageIds = new ConcurrentHashMap<>();

    public boolean isProcessed(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        // 先查内存缓存（加速层）
        Long firstSeenAt = processedMessageIds.get(messageId);
        if (firstSeenAt != null) {
            if (isExpired(firstSeenAt, now)) {
                processedMessageIds.remove(messageId, firstSeenAt);
            } else {
                return true;
            }
        }
        // 查 DB（持久层）
        try {
            MqttCommandRecord record = mqttCommandRecordMapper.selectByMessageId(messageId);
            if (record == null) {
                return false;
            }
            if (isDbExpired(record)) {
                mqttCommandRecordMapper.deleteById(record.getId());
                return false;
            }
            // 回填内存缓存
            processedMessageIds.putIfAbsent(messageId, toEpochMilli(record.getFirstSeenAt()));
            return true;
        } catch (Exception e) {
            log.warn("[CMD] query command record from DB failed, fallback to memory only: messageId={}, error={}",
                    messageId, e.getMessage());
            return false;
        }
    }

    public boolean markProcessed(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return false;
        }
        // 内存先标记
        Long prev = processedMessageIds.putIfAbsent(messageId, System.currentTimeMillis());
        if (prev != null) {
            return false;
        }
        // DB 唯一键插入
        try {
            LocalDateTime now = LocalDateTime.now();
            long ttlMs = Math.max(60000L, properties.getCommandDedupTtlMs());
            MqttCommandRecord record = new MqttCommandRecord();
            record.setMessageId(messageId);
            record.setFirstSeenAt(now);
            record.setExpireAt(now.plus(ttlMs, ChronoUnit.MILLIS));
            record.setStatus("PROCESSING");
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            mqttCommandRecordMapper.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            // 唯一键冲突，说明重复消息
            return false;
        } catch (Exception e) {
            log.warn("[CMD] insert command record to DB failed, keep memory-only dedup: messageId={}, error={}",
                    messageId, e.getMessage());
            // DB 失败但内存已标记，仍允许处理
            return true;
        }
    }

    public int processedCount() {
        return processedMessageIds.size();
    }

    @Scheduled(fixedDelayString = "${mqtt-agent.dedup-cleanup-interval-ms:600000}")
    public void cleanupExpiredRecords() {
        long now = System.currentTimeMillis();
        // 清理内存
        int memoryRemoved = 0;
        for (Map.Entry<String, Long> entry : processedMessageIds.entrySet()) {
            Long firstSeenAt = entry.getValue();
            if (firstSeenAt != null && isExpired(firstSeenAt, now)
                    && processedMessageIds.remove(entry.getKey(), firstSeenAt)) {
                memoryRemoved++;
            }
        }
        // 清理 DB
        int dbRemoved = 0;
        try {
            dbRemoved = mqttCommandRecordMapper.deleteExpiredRecords(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[CMD] cleanup expired DB command records failed: error={}", e.getMessage());
        }
        if (memoryRemoved > 0 || dbRemoved > 0) {
            log.info("[CMD] cleaned expired MQTT command records: memoryRemoved={}, dbRemoved={}, remaining={}",
                    memoryRemoved, dbRemoved, processedMessageIds.size());
        }
    }

    private boolean isExpired(long firstSeenAt, long now) {
        long ttlMs = Math.max(60000L, properties.getCommandDedupTtlMs());
        return now - firstSeenAt > ttlMs;
    }

    private boolean isDbExpired(MqttCommandRecord record) {
        if (record.getExpireAt() == null) {
            return false;
        }
        return record.getExpireAt().isBefore(LocalDateTime.now());
    }

    private long toEpochMilli(LocalDateTime ldt) {
        if (ldt == null) {
            return System.currentTimeMillis();
        }
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

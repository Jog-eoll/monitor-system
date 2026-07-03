package com.infopublish.client.service.impl;

import com.infopublish.client.entity.dto.EtwTrafficEventDTO;
import com.infopublish.client.service.EtwTrafficAccountingService;
import com.infopublish.client.service.ProcessBindService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory accounting for outbound UDP traffic reported by a local ETW collector.
 */
@Slf4j
@Service
public class EtwTrafficAccountingServiceImpl implements EtwTrafficAccountingService {

    private static final String PROTOCOL_UDP = "UDP";
    private static final String DIRECTION_OUTBOUND = "OUTBOUND";

    @Value("${traffic.etw-accounting.enabled:true}")
    private boolean enabled;

    @Value("${traffic.etw-accounting.window-ms:10000}")
    private long windowMs;

    @Value("${traffic.etw-accounting.retention-ms:120000}")
    private long retentionMs;

    @Value("${traffic.etw-accounting.max-records:20000}")
    private int maxRecords;

    @Resource
    private ProcessBindService processBindService;

    private final ConcurrentMap<TrafficKey, TrafficCounter> counters = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> recordOutboundEvent(EtwTrafficEventDTO event) {
        Map<String, Object> result = new HashMap<>();
        result.put("available", enabled);
        result.put("collectorType", "ETW_AGENT_HTTP");

        if (!enabled) {
            return reject(result, "ETW_ACCOUNTING_DISABLED");
        }
        if (event == null) {
            return reject(result, "EMPTY_EVENT");
        }

        String protocol = normalize(event.getProtocol());
        if (protocol != null && !PROTOCOL_UDP.equalsIgnoreCase(protocol)) {
            return reject(result, "NOT_UDP");
        }

        String direction = normalize(event.getDirection());
        if (direction != null && !DIRECTION_OUTBOUND.equalsIgnoreCase(direction)) {
            return reject(result, "NOT_OUTBOUND");
        }

        String sourceIp = normalize(event.getSourceIp());
        String targetIp = normalize(event.getTargetIp());
        Integer sourcePort = event.getSourcePort();
        Integer targetPort = event.getTargetPort();
        if (sourceIp == null || targetIp == null || !isValidPort(sourcePort) || !isValidPort(targetPort)) {
            return reject(result, "INVALID_ENDPOINT");
        }

        Long byteCount = event.getByteCount();
        if (byteCount == null || byteCount <= 0) {
            return reject(result, "INVALID_BYTE_COUNT");
        }

        long authorizedPid = processBindService.getAuthorizedPid();
        result.put("authorizedPid", authorizedPid);
        if (authorizedPid <= 0) {
            return reject(result, "PID_UNBOUND");
        }

        Long processId = event.getProcessId();
        if (processId == null || processId != authorizedPid) {
            result.put("processId", processId);
            return reject(result, "PID_NOT_AUTHORIZED");
        }

        long now = System.currentTimeMillis();
        long eventTime = event.getTimestampMillis() != null ? event.getTimestampMillis() : now;
        long safeWindowMs = safeWindowMs();
        long windowStart = alignWindow(eventTime, safeWindowMs);
        long packetCount = event.getPacketCount() != null && event.getPacketCount() > 0 ? event.getPacketCount() : 1L;

        TrafficKey key = new TrafficKey(sourceIp, sourcePort, targetIp, targetPort, windowStart);
        TrafficCounter counter = counters.computeIfAbsent(key, TrafficCounter::new);
        counter.packetCount.addAndGet(packetCount);
        counter.byteCount.addAndGet(byteCount);
        counter.lastUpdateMillis = now;

        cleanup(now);

        result.put("accepted", true);
        result.put("reason", "RECORDED");
        result.put("windowStart", windowStart);
        result.put("windowEnd", windowStart + safeWindowMs);
        result.put("sourceIp", sourceIp);
        result.put("sourcePort", sourcePort);
        result.put("targetIp", targetIp);
        result.put("targetPort", targetPort);
        result.put("packetCount", packetCount);
        result.put("byteCount", byteCount);
        return result;
    }

    @Override
    public Map<String, Object> queryStats(long windowStart, long windowEnd, int limit) {
        Map<String, Object> result = new HashMap<>();
        result.put("available", enabled);
        result.put("collectorType", "ETW_AGENT_HTTP");
        result.put("authorizedPid", processBindService.getAuthorizedPid());
        result.put("windowStart", windowStart);
        result.put("windowEnd", windowEnd);
        result.put("windowMs", safeWindowMs());

        if (!enabled) {
            result.put("reason", "ETW_ACCOUNTING_DISABLED");
            result.put("records", Collections.emptyList());
            result.put("total", 0);
            return result;
        }
        if (windowStart < 0 || windowEnd <= windowStart) {
            result.put("reason", "INVALID_WINDOW");
            result.put("records", Collections.emptyList());
            result.put("total", 0);
            return result;
        }

        cleanup(System.currentTimeMillis());

        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        List<Map<String, Object>> records = new ArrayList<>();
        for (TrafficCounter counter : counters.values()) {
            TrafficKey key = counter.key;
            if (key.windowStart >= windowStart && key.windowStart < windowEnd) {
                records.add(counter.toMap(safeWindowMs()));
            }
        }

        records.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                long leftBytes = asLong(left.get("byteCount"));
                long rightBytes = asLong(right.get("byteCount"));
                return Long.compare(rightBytes, leftBytes);
            }
        });

        int total = records.size();
        if (records.size() > safeLimit) {
            records = new ArrayList<>(records.subList(0, safeLimit));
        }

        result.put("reason", "OK");
        result.put("records", records);
        result.put("total", total);
        result.put("returned", records.size());
        return result;
    }

    @Override
    public void clear() {
        counters.clear();
        log.info("[ETW流量统计] 已清空客户端内存统计");
    }

    private Map<String, Object> reject(Map<String, Object> result, String reason) {
        result.put("accepted", false);
        result.put("reason", reason);
        return result;
    }

    private void cleanup(long now) {
        long safeRetentionMs = retentionMs > 0 ? retentionMs : 120000L;
        long expiredBefore = now - safeRetentionMs;
        Iterator<Map.Entry<TrafficKey, TrafficCounter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TrafficKey, TrafficCounter> entry = iterator.next();
            if (entry.getValue().lastUpdateMillis < expiredBefore) {
                iterator.remove();
            }
        }

        int safeMaxRecords = maxRecords > 0 ? maxRecords : 20000;
        if (counters.size() <= safeMaxRecords) {
            return;
        }

        List<TrafficCounter> list = new ArrayList<>(counters.values());
        list.sort(new Comparator<TrafficCounter>() {
            @Override
            public int compare(TrafficCounter left, TrafficCounter right) {
                return Long.compare(left.lastUpdateMillis, right.lastUpdateMillis);
            }
        });
        int removeCount = counters.size() - safeMaxRecords;
        for (int i = 0; i < removeCount && i < list.size(); i++) {
            counters.remove(list.get(i).key);
        }
    }

    private long safeWindowMs() {
        return windowMs > 0 ? windowMs : 10000L;
    }

    private long alignWindow(long timestampMillis, long safeWindowMs) {
        return timestampMillis - (timestampMillis % safeWindowMs);
    }

    private boolean isValidPort(Integer port) {
        return port != null && port > 0 && port <= 65535;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static class TrafficKey {
        private final String sourceIp;
        private final int sourcePort;
        private final String targetIp;
        private final int targetPort;
        private final long windowStart;

        private TrafficKey(String sourceIp, int sourcePort, String targetIp, int targetPort, long windowStart) {
            this.sourceIp = sourceIp;
            this.sourcePort = sourcePort;
            this.targetIp = targetIp;
            this.targetPort = targetPort;
            this.windowStart = windowStart;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TrafficKey)) {
                return false;
            }
            TrafficKey that = (TrafficKey) o;
            return sourcePort == that.sourcePort
                    && targetPort == that.targetPort
                    && windowStart == that.windowStart
                    && Objects.equals(sourceIp, that.sourceIp)
                    && Objects.equals(targetIp, that.targetIp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceIp, sourcePort, targetIp, targetPort, windowStart);
        }
    }

    private static class TrafficCounter {
        private final TrafficKey key;
        private final AtomicLong packetCount = new AtomicLong();
        private final AtomicLong byteCount = new AtomicLong();
        private volatile long lastUpdateMillis;

        private TrafficCounter(TrafficKey key) {
            this.key = key;
            this.lastUpdateMillis = System.currentTimeMillis();
        }

        private Map<String, Object> toMap(long windowMs) {
            Map<String, Object> data = new HashMap<>();
            data.put("sourceIp", key.sourceIp);
            data.put("sourcePort", key.sourcePort);
            data.put("targetIp", key.targetIp);
            data.put("targetPort", key.targetPort);
            data.put("windowStart", key.windowStart);
            data.put("windowEnd", key.windowStart + windowMs);
            data.put("packetCount", packetCount.get());
            data.put("byteCount", byteCount.get());
            data.put("lastUpdateMillis", lastUpdateMillis);
            return data;
        }
    }
}

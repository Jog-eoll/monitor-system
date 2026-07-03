package com.publishgateway.udpproxy.service;

import com.publishgateway.udpproxy.entity.UdpProxyRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
 * Reconciles gateway-received UDP traffic with client ETW outbound traffic.
 */
@Slf4j
@Service
public class TrafficReconciliationService {

    @Value("${security.traffic-reconcile.enabled:true}")
    private boolean enabled;

    @Value("${security.traffic-reconcile.window-ms:10000}")
    private long windowMs;

    @Value("${security.traffic-reconcile.delay-ms:2000}")
    private long delayMs;

    @Value("${security.traffic-reconcile.retention-ms:120000}")
    private long retentionMs;

    @Value("${security.traffic-reconcile.max-endpoints:10000}")
    private int maxEndpoints;

    @Value("${security.traffic-reconcile.alert-cooldown-ms:30000}")
    private long alertCooldownMs;

    @Value("${security.traffic-reconcile.tolerance-bytes:512}")
    private long toleranceBytes;

    @Value("${security.client-port:7080}")
    private int clientPort;

    @Value("${security.client-validate-timeout-ms:2000}")
    private int clientTimeoutMs;

    private final ConcurrentMap<TrafficKey, TrafficCounter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> alertTimes = new ConcurrentHashMap<>();

    /**
     * Records bytes received by the gateway from a UDP sender.
     */
    public void recordGatewayReceive(UdpProxyRule rule, String sourceIp, int sourcePort, int byteCount) {
        if (!enabled || rule == null || isBlank(sourceIp) || sourcePort <= 0 || byteCount <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long safeWindowMs = safeWindowMs();
        long windowStart = alignWindow(now, safeWindowMs);
        TrafficKey key = new TrafficKey(
                safe(rule.getRuleId()),
                rule.getChainId() == null ? "" : String.valueOf(rule.getChainId()),
                sourceIp.trim(),
                sourcePort,
                resolveTargetIp(rule),
                resolveTargetPort(rule),
                windowStart);

        TrafficCounter counter = counters.computeIfAbsent(key, TrafficCounter::new);
        counter.gatewayReceivedPackets.incrementAndGet();
        counter.gatewayReceivedBytes.addAndGet(byteCount);
        counter.lastUpdateMillis = now;
        counter.reconciled = false;

        cleanup(now);
    }

    /**
     * Reconciles completed windows in the background.
     */
    @Scheduled(fixedDelayString = "${security.traffic-reconcile.reconcile-interval-ms:10000}")
    public void reconcileCompletedWindows() {
        if (!enabled) {
            return;
        }

        long now = System.currentTimeMillis();
        long completedBefore = now - safeDelayMs();
        for (TrafficCounter counter : counters.values()) {
            TrafficKey key = counter.key;
            long windowEnd = key.windowStart + safeWindowMs();
            if (windowEnd > completedBefore || counter.reconciled) {
                continue;
            }

            synchronized (counter) {
                if (counter.reconciled) {
                    continue;
                }
                reconcile(counter, now);
            }
        }
        cleanup(now);
    }

    /**
     * Queries current reconciliation snapshots.
     */
    public Map<String, Object> querySnapshots(int limit) {
        cleanup(System.currentTimeMillis());

        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        List<Map<String, Object>> records = new ArrayList<>();
        for (TrafficCounter counter : counters.values()) {
            records.add(counter.toMap(safeWindowMs()));
        }
        records.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                long leftWindow = asLong(left.get("windowStart"));
                long rightWindow = asLong(right.get("windowStart"));
                if (leftWindow != rightWindow) {
                    return Long.compare(rightWindow, leftWindow);
                }
                return Long.compare(asLong(right.get("gatewayReceivedBytes")), asLong(left.get("gatewayReceivedBytes")));
            }
        });

        int total = records.size();
        if (records.size() > safeLimit) {
            records = new ArrayList<>(records.subList(0, safeLimit));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("enabled", enabled);
        data.put("windowMs", safeWindowMs());
        data.put("delayMs", safeDelayMs());
        data.put("toleranceBytes", Math.max(toleranceBytes, 0L));
        data.put("total", total);
        data.put("returned", records.size());
        data.put("records", records);
        return data;
    }

    /**
     * Clears in-memory reconciliation counters.
     */
    public void clear() {
        counters.clear();
        alertTimes.clear();
        log.info("[流量对账] 已清空网关内存统计");
    }

    private void reconcile(TrafficCounter counter, long now) {
        TrafficKey key = counter.key;
        ClientStats clientStats = queryClientStats(key);
        counter.clientEtwAvailable = clientStats.available;
        counter.clientEtwPackets = clientStats.packetCount;
        counter.clientEtwBytes = clientStats.byteCount;
        counter.lastReconcileMillis = now;

        long gatewayBytes = counter.gatewayReceivedBytes.get();
        long diffBytes = gatewayBytes - clientStats.byteCount;
        counter.diffBytes = diffBytes;

        if (!clientStats.available) {
            counter.mismatch = false;
            counter.lastReason = clientStats.reason;
            warnWithCooldown(key, "[流量对账] 客户端ETW统计不可用: source={}:{}, target={}:{}, ruleId={}, chainId={}, reason={}",
                    key.sourceIp, key.sourcePort, key.targetIp, key.targetPort, key.ruleId, key.chainId, clientStats.reason);
        } else if (diffBytes > Math.max(toleranceBytes, 0L)) {
            counter.mismatch = true;
            counter.lastReason = "GATEWAY_BYTES_EXCEED_CLIENT_ETW";
            warnWithCooldown(key, "[流量对账] 发现网关收包大于合法进程ETW出站量: source={}:{}, target={}:{}, ruleId={}, chainId={}, gatewayBytes={}, clientEtwBytes={}, diffBytes={}",
                    key.sourceIp, key.sourcePort, key.targetIp, key.targetPort, key.ruleId, key.chainId,
                    gatewayBytes, clientStats.byteCount, diffBytes);
        } else if (-diffBytes > Math.max(toleranceBytes, 0L)) {
            counter.mismatch = true;
            counter.lastReason = "CLIENT_ETW_BYTES_EXCEED_GATEWAY";
            warnWithCooldown(key, "[流量对账] 客户端ETW出站量大于网关收包量: source={}:{}, target={}:{}, ruleId={}, chainId={}, gatewayBytes={}, clientEtwBytes={}, diffBytes={}",
                    key.sourceIp, key.sourcePort, key.targetIp, key.targetPort, key.ruleId, key.chainId,
                    gatewayBytes, clientStats.byteCount, diffBytes);
        } else {
            counter.mismatch = false;
            counter.lastReason = "MATCHED";
        }

        counter.reconciled = true;
    }

    private ClientStats queryClientStats(TrafficKey key) {
        String url = "http://" + key.sourceIp + ":" + clientPort
                + "/security/traffic/stats?windowStart=" + key.windowStart
                + "&windowEnd=" + (key.windowStart + safeWindowMs())
                + "&limit=1000";
        try {
            ResponseEntity<Map> response = buildRestTemplate().getForEntity(url, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return ClientStats.unavailable("CLIENT_HTTP_STATUS_" + response.getStatusCodeValue());
            }

            Map body = response.getBody();
            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map)) {
                return ClientStats.unavailable("CLIENT_RESPONSE_DATA_MISSING");
            }

            Map data = (Map) dataObj;
            if (!asBoolean(data.get("available"))) {
                return ClientStats.unavailable(String.valueOf(data.get("reason")));
            }

            Object recordsObj = data.get("records");
            if (!(recordsObj instanceof List)) {
                return new ClientStats(true, 0L, 0L, "OK");
            }

            long packetCount = 0L;
            long byteCount = 0L;
            for (Object recordObj : (List) recordsObj) {
                if (!(recordObj instanceof Map)) {
                    continue;
                }
                Map record = (Map) recordObj;
                if (matches(key, record)) {
                    packetCount += asLong(record.get("packetCount"));
                    byteCount += asLong(record.get("byteCount"));
                }
            }
            return new ClientStats(true, packetCount, byteCount, "OK");
        } catch (Exception e) {
            log.debug("[流量对账] 拉取客户端ETW统计失败: url={}, reason={}", url, e.getMessage());
            return ClientStats.unavailable("CLIENT_STATS_UNREACHABLE");
        }
    }

    private boolean matches(TrafficKey key, Map record) {
        return key.sourceIp.equals(String.valueOf(record.get("sourceIp")))
                && key.sourcePort == asInt(record.get("sourcePort"))
                && key.targetIp.equals(String.valueOf(record.get("targetIp")))
                && key.targetPort == asInt(record.get("targetPort"));
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = clientTimeoutMs > 0 ? clientTimeoutMs : 2000;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private void warnWithCooldown(TrafficKey key, String message, Object... args) {
        long now = System.currentTimeMillis();
        String alertKey = key.sourceIp + ":" + key.sourcePort + "|" + key.targetIp + ":" + key.targetPort;
        Long lastAlert = alertTimes.get(alertKey);
        if (lastAlert != null && now - lastAlert < safeAlertCooldownMs()) {
            return;
        }
        alertTimes.put(alertKey, now);
        log.warn(message, args);
    }

    private void cleanup(long now) {
        long safeRetentionMs = retentionMs > 0 ? retentionMs : 120000L;
        long expiredBefore = now - safeRetentionMs;
        Iterator<Map.Entry<TrafficKey, TrafficCounter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TrafficKey, TrafficCounter> entry = iterator.next();
            TrafficKey key = entry.getKey();
            TrafficCounter value = entry.getValue();
            if (key.windowStart + safeWindowMs() < expiredBefore && value.lastUpdateMillis < expiredBefore) {
                iterator.remove();
            }
        }

        int safeMaxEndpoints = maxEndpoints > 0 ? maxEndpoints : 10000;
        if (counters.size() <= safeMaxEndpoints) {
            return;
        }

        List<TrafficCounter> list = new ArrayList<>(counters.values());
        list.sort(new Comparator<TrafficCounter>() {
            @Override
            public int compare(TrafficCounter left, TrafficCounter right) {
                return Long.compare(left.lastUpdateMillis, right.lastUpdateMillis);
            }
        });
        int removeCount = counters.size() - safeMaxEndpoints;
        for (int i = 0; i < removeCount && i < list.size(); i++) {
            counters.remove(list.get(i).key);
        }
    }

    private String resolveTargetIp(UdpProxyRule rule) {
        if (!isBlank(rule.getTargetIp())) {
            return rule.getTargetIp().trim();
        }
        if (!isBlank(rule.getListenIp())) {
            return rule.getListenIp().trim();
        }
        return "";
    }

    private int resolveTargetPort(UdpProxyRule rule) {
        if (rule.getTargetPort() != null && rule.getTargetPort() > 0) {
            return rule.getTargetPort();
        }
        return rule.getListenPort() != null ? rule.getListenPort() : 0;
    }

    private long safeWindowMs() {
        return windowMs > 0 ? windowMs : 10000L;
    }

    private long safeDelayMs() {
        return delayMs >= 0 ? delayMs : 2000L;
    }

    private long safeAlertCooldownMs() {
        return alertCooldownMs > 0 ? alertCooldownMs : 30000L;
    }

    private long alignWindow(long timestampMillis, long safeWindowMs) {
        return timestampMillis - (timestampMillis % safeWindowMs);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
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
        private final String ruleId;
        private final String chainId;
        private final String sourceIp;
        private final int sourcePort;
        private final String targetIp;
        private final int targetPort;
        private final long windowStart;

        private TrafficKey(String ruleId, String chainId, String sourceIp, int sourcePort,
                           String targetIp, int targetPort, long windowStart) {
            this.ruleId = ruleId;
            this.chainId = chainId;
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
                    && Objects.equals(ruleId, that.ruleId)
                    && Objects.equals(chainId, that.chainId)
                    && Objects.equals(sourceIp, that.sourceIp)
                    && Objects.equals(targetIp, that.targetIp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ruleId, chainId, sourceIp, sourcePort, targetIp, targetPort, windowStart);
        }
    }

    private static class TrafficCounter {
        private final TrafficKey key;
        private final AtomicLong gatewayReceivedPackets = new AtomicLong();
        private final AtomicLong gatewayReceivedBytes = new AtomicLong();
        private volatile boolean reconciled;
        private volatile boolean clientEtwAvailable;
        private volatile boolean mismatch;
        private volatile long clientEtwPackets;
        private volatile long clientEtwBytes;
        private volatile long diffBytes;
        private volatile long lastUpdateMillis;
        private volatile long lastReconcileMillis;
        private volatile String lastReason = "PENDING";

        private TrafficCounter(TrafficKey key) {
            this.key = key;
            this.lastUpdateMillis = System.currentTimeMillis();
        }

        private Map<String, Object> toMap(long windowMs) {
            Map<String, Object> data = new HashMap<>();
            data.put("ruleId", key.ruleId);
            data.put("chainId", key.chainId);
            data.put("sourceIp", key.sourceIp);
            data.put("sourcePort", key.sourcePort);
            data.put("targetIp", key.targetIp);
            data.put("targetPort", key.targetPort);
            data.put("windowStart", key.windowStart);
            data.put("windowEnd", key.windowStart + windowMs);
            data.put("gatewayReceivedPackets", gatewayReceivedPackets.get());
            data.put("gatewayReceivedBytes", gatewayReceivedBytes.get());
            data.put("clientEtwAvailable", clientEtwAvailable);
            data.put("clientEtwPackets", clientEtwPackets);
            data.put("clientEtwBytes", clientEtwBytes);
            data.put("diffBytes", diffBytes);
            data.put("mismatch", mismatch);
            data.put("reconciled", reconciled);
            data.put("lastReason", lastReason);
            data.put("lastUpdateMillis", lastUpdateMillis);
            data.put("lastReconcileMillis", lastReconcileMillis);
            return data;
        }
    }

    private static class ClientStats {
        private final boolean available;
        private final long packetCount;
        private final long byteCount;
        private final String reason;

        private ClientStats(boolean available, long packetCount, long byteCount, String reason) {
            this.available = available;
            this.packetCount = packetCount;
            this.byteCount = byteCount;
            this.reason = isEmpty(reason) ? "UNKNOWN" : reason;
        }

        private static ClientStats unavailable(String reason) {
            return new ClientStats(false, 0L, 0L, isEmpty(reason) ? "CLIENT_STATS_UNAVAILABLE" : reason);
        }

        private static boolean isEmpty(String value) {
            return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim());
        }
    }
}

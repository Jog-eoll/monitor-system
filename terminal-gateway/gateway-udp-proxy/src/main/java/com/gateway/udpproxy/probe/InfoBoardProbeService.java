package com.gateway.udpproxy.probe;

import com.alibaba.fastjson2.JSON;
import com.gateway.udpproxy.entity.UdpProxyRule;
import com.gateway.udpproxy.manager.UdpProxyRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 情报板连通性探测服务
 *
 * 职责：
 *   1. 定时遍历所有 ENABLED 规则，用 TCP Socket 探测情报板 IP:Port 是否可达
 *   2. 将探测结果批量 POST 上报给 monitor-platform device 服务
 *
 * 上报接口：POST {probe.report-url}
 * 上报格式：
 * [
 *   { "ip": "192.168.113.239", "port": 9520, "chainId": 1, "reachable": true },
 *   { "ip": "192.168.113.240", "port": 9520, "chainId": 2, "reachable": false }
 * ]
 */
@Slf4j
@Service
public class InfoBoardProbeService {

    @Resource
    private UdpProxyRuleManager ruleManager;

    @Resource
    private RestTemplate restTemplate;

    @Value("${probe.report-url:}")
    private String reportUrl;

    @Value("${probe.timeout-ms:3000}")
    private int timeoutMs;

    private final Map<String, Map<String, Object>> latestResults = new ConcurrentHashMap<>();

    /**
     * 定时探测，默认每30秒执行一次
     * fixedDelayString 支持从配置读取，单位毫秒（30s = 30000ms）
     */
    @Scheduled(fixedDelayString = "${probe.interval-seconds:30}000")
    public void probeAndReport() {
        List<UdpProxyRule> rules = ruleManager.getAllEnabledRules();
        if (rules == null || rules.isEmpty()) {
            log.debug("[Probe] 无启用规则，跳过探测");
            return;
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (UdpProxyRule rule : rules) {
            String ip = rule.getTargetIp();
            Integer port = rule.getTargetPort();

            if (ip == null || ip.isEmpty() || port == null) {
                log.warn("[Probe] 规则 {} 情报板IP/Port 为空，跳过", rule.getRuleId());
                continue;
            }

            boolean reachable = probe(ip, port);
            log.info("[Probe] 情报板 {}:{} chainId={} -> {}",
                    ip, port, rule.getChainId(), reachable ? "可达" : "不可达");

            Map<String, Object> item = new HashMap<>();
            item.put("ip", ip);
            item.put("port", port);
            item.put("chainId", rule.getChainId());
            item.put("reachable", reachable);
            item.put("probeTime", System.currentTimeMillis());
            results.add(item);
            latestResults.put(buildKey(ip, port), new LinkedHashMap<>(item));
        }

        if (!results.isEmpty()) {
            report(results);
        }
    }

    /**
     * 返回最新一次各情报板探测结果（供 /udp-proxy/probe/report 接口直接返回）
     */
    public List<Map<String, Object>> getLatestProbeResult() {
        if (!latestResults.isEmpty()) {
            return new ArrayList<>(latestResults.values());
        }

        List<UdpProxyRule> rules = ruleManager.getAllEnabledRules();
        List<Map<String, Object>> results = new ArrayList<>();
        if (rules == null) {
            return results;
        }
        for (UdpProxyRule rule : rules) {
            String ip = rule.getTargetIp();
            Integer port = rule.getTargetPort();
            if (ip == null || ip.isEmpty() || port == null) continue;

            Map<String, Object> item = probeRule(rule);
            if (!item.isEmpty()) {
                results.add(item);
            }
        }
        return results;
    }

    public Map<String, Object> getLatestProbeStatus(String ip, Integer port) {
        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null) {
            return Collections.emptyMap();
        }
        Integer normalizedPort = port != null && port > 0 ? port : null;
        Map<String, Object> cached = findCached(normalizedIp, normalizedPort);
        if (cached != null) {
            return cached;
        }
        UdpProxyRule rule = findRule(normalizedIp, normalizedPort);
        if (rule == null) {
            log.debug("[Probe] no enabled rule found for info board {}:{}", normalizedIp, normalizedPort);
            return Collections.emptyMap();
        }
        return probeRule(rule);
    }

    /**
     * TCP Socket 探测情报板是否可达
     * 情报板通信为 UDP，但大多数情报板同时开放 TCP 管理端口（如 9520）
     * 若 TCP 也不通，可改为 ICMP（InetAddress.isReachable）
     */
    private boolean probe(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (Exception e) {
            log.debug("[Probe] TCP探测失败 {}:{} -> {}", ip, port, e.getMessage());
            // TCP不通时降级用 ICMP ping
            return pingFallback(ip);
        }
    }

    /**
     * ICMP ping 降级探测（TCP 不通时兜底）
     */
    private boolean pingFallback(String ip) {
        try {
            boolean reachable = java.net.InetAddress.getByName(ip).isReachable(timeoutMs);
            log.debug("[Probe] ICMP ping {} -> {}", ip, reachable ? "可达" : "不可达");
            return reachable;
        } catch (Exception e) {
            log.debug("[Probe] ICMP ping 异常 {}: {}", ip, e.getMessage());
            return false;
        }
    }

    /**
     * 将探测结果批量上报给 monitor-platform
     */
    private void report(List<Map<String, Object>> results) {
        if (reportUrl == null || reportUrl.isEmpty()) {
            log.debug("[Probe] report-url 未配置，跳过上报");
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = JSON.toJSONString(results);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(reportUrl, entity, Map.class);
            log.info("[Probe] 上报探测结果成功，共 {} 条", results.size());
        } catch (Exception e) {
            log.warn("[Probe] 上报探测结果失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private Map<String, Object> probeRule(UdpProxyRule rule) {
        String ip = normalizeIp(rule.getTargetIp());
        Integer port = rule.getTargetPort();
        if (ip == null || port == null) {
            return Collections.emptyMap();
        }
        boolean reachable = probe(ip, port);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ip", ip);
        item.put("port", port);
        item.put("chainId", rule.getChainId());
        item.put("reachable", reachable);
        item.put("probeTime", System.currentTimeMillis());
        latestResults.put(buildKey(ip, port), new LinkedHashMap<>(item));
        return item;
    }

    private Map<String, Object> findCached(String ip, Integer port) {
        if (port != null) {
            Map<String, Object> cached = latestResults.get(buildKey(ip, port));
            return cached == null ? null : new LinkedHashMap<>(cached);
        }
        for (Map<String, Object> item : latestResults.values()) {
            if (ip.equals(item.get("ip"))) {
                return new LinkedHashMap<>(item);
            }
        }
        return null;
    }

    private UdpProxyRule findRule(String ip, Integer port) {
        List<UdpProxyRule> rules = ruleManager.getAllEnabledRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (UdpProxyRule rule : rules) {
            String targetIp = normalizeIp(rule.getTargetIp());
            Integer targetPort = rule.getTargetPort();
            if (!ip.equals(targetIp)) {
                continue;
            }
            if (port == null || port.equals(targetPort)) {
                return rule;
            }
        }
        return null;
    }

    private String buildKey(String ip, Integer port) {
        return ip + ":" + port;
    }

    private String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ip.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

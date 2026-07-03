package com.infopublish.client.service.impl;

import com.infopublish.client.entity.UdpProxyRule;
import com.infopublish.client.entity.dto.sigma.InfoBoardStatusResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.manager.UdpProxyRuleManager;
import com.infopublish.client.service.InfoBoardStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class InfoBoardStatusServiceImpl implements InfoBoardStatusService {

    @Resource
    private UdpProxyRuleManager proxyRuleManager;

    @Resource
    private RestTemplate restTemplate;

    @Value("${info-board.status.default-port:9520}")
    private int defaultPort;

    @Value("${info-board.status.probe-enabled:true}")
    private boolean probeEnabled;

    @Value("${info-board.status.probe-timeout-ms:1000}")
    private int probeTimeoutMs;

    @Value("${info-board.status.source:auto}")
    private String statusSource;

    @Value("${info-board.status.platform-url:${monitor-platform.url:}}")
    private String platformUrl;

    @Value("${info-board.status.gateway-url:}")
    private String gatewayUrl;

    @Value("${info-board.status.fallback-direct-probe-enabled:true}")
    private boolean fallbackDirectProbeEnabled;

    @Override
    public InfoBoardStatusResponse getStatus(String ip, Integer port) {
        BoardEndpoint endpoint = resolveEndpoint(ip, port);
        String responseIp = endpoint != null ? endpoint.getIp() : trimToNull(ip);
        return InfoBoardStatusResponse.of(responseIp, isOnline(endpoint));
    }

    @Override
    public BoardEndpoint resolveEndpoint(SigmaVerifyRequest.TargetRef target) {
        if (target != null && trimToNull(target.getIp()) != null) {
            return resolveEndpoint(target.getIp(), target.getPort());
        }
        return findFirstConfiguredEndpoint();
    }

    private BoardEndpoint resolveEndpoint(String ip, Integer port) {
        String requestedIp = trimToNull(ip);
        if (requestedIp != null) {
            UdpProxyRule matchedRule = findRuleByIp(requestedIp);
            if (matchedRule != null) {
                return toEndpoint(matchedRule, true);
            }
            return new BoardEndpoint(requestedIp, normalizePort(port), true);
        }
        return findFirstConfiguredEndpoint();
    }

    private boolean isOnline(BoardEndpoint endpoint) {
        if (endpoint == null || trimToNull(endpoint.getIp()) == null) {
            return false;
        }
        if (!endpoint.isEnabled()) {
            log.debug("[InfoBoardStatus] endpoint disabled, ip={}, port={}", endpoint.getIp(), endpoint.getPort());
            return false;
        }
        StatusLookupResult remoteResult = lookupRemoteStatus(endpoint);
        if (remoteResult != null) {
            log.debug("[InfoBoardStatus] remote status source={}, ip={}, online={}",
                    remoteResult.getSource(), endpoint.getIp(), remoteResult.isOnline());
            return remoteResult.isOnline();
        }
        if (!fallbackDirectProbeEnabled && !"direct".equalsIgnoreCase(trimToEmpty(statusSource))) {
            return false;
        }
        return probeLocal(endpoint);
    }

    private boolean probeLocal(BoardEndpoint endpoint) {
        if (!probeEnabled) {
            return true;
        }
        Integer port = normalizePort(endpoint.getPort());
        if (probeTcp(endpoint.getIp(), port)) {
            return true;
        }
        return probeIcmp(endpoint.getIp());
    }

    private UdpProxyRule findRuleByIp(String ip) {
        List<UdpProxyRule> rules = proxyRuleManager.listAllRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        UdpProxyRule fallback = null;
        for (UdpProxyRule rule : rules) {
            if (ip.equals(trimToNull(rule.getTargetIp())) || ip.equals(trimToNull(rule.getListenIp()))) {
                if ("ENABLED".equalsIgnoreCase(rule.getStatus())) {
                    return rule;
                }
                if (fallback == null) {
                    fallback = rule;
                }
            }
        }
        return fallback;
    }

    private BoardEndpoint findFirstConfiguredEndpoint() {
        List<UdpProxyRule> rules = proxyRuleManager.listAllRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        UdpProxyRule fallback = null;
        for (UdpProxyRule rule : rules) {
            if (trimToNull(rule.getTargetIp()) == null && trimToNull(rule.getListenIp()) == null) {
                continue;
            }
            if ("ENABLED".equalsIgnoreCase(rule.getStatus())) {
                return toEndpoint(rule);
            }
            if (fallback == null) {
                fallback = rule;
            }
        }
        return fallback != null ? toEndpoint(fallback) : null;
    }

    private BoardEndpoint toEndpoint(UdpProxyRule rule) {
        return toEndpoint(rule, "ENABLED".equalsIgnoreCase(rule.getStatus()));
    }

    private BoardEndpoint toEndpoint(UdpProxyRule rule, boolean enabled) {
        String ip = firstNonBlank(rule.getTargetIp(), rule.getListenIp());
        Integer port = normalizePort(rule.getTargetPort() != null ? rule.getTargetPort() : rule.getListenPort());
        return new BoardEndpoint(ip, port, enabled);
    }

    private StatusLookupResult lookupRemoteStatus(BoardEndpoint endpoint) {
        String source = trimToEmpty(statusSource).toLowerCase(Locale.ROOT);
        if (source.isEmpty() || "auto".equals(source)) {
            StatusLookupResult platform = lookupPlatformStatus(endpoint);
            return platform != null ? platform : lookupGatewayStatus(endpoint);
        }
        if ("platform".equals(source)) {
            return lookupPlatformStatus(endpoint);
        }
        if ("gateway".equals(source) || "terminal-gateway".equals(source)) {
            return lookupGatewayStatus(endpoint);
        }
        return null;
    }

    private StatusLookupResult lookupPlatformStatus(BoardEndpoint endpoint) {
        String baseUrl = trimToNull(platformUrl);
        if (baseUrl == null) {
            return null;
        }
        String url = UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(baseUrl))
                .path("/device/unified/status-by-ip")
                .queryParam("ip", endpoint.getIp())
                .build()
                .toUriString();
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            Map<String, Object> data = extractData(response);
            if (data == null || data.isEmpty()) {
                return null;
            }
            Boolean online = extractOnline(data);
            return online == null ? null : new StatusLookupResult(online, "platform");
        } catch (Exception e) {
            log.debug("[InfoBoardStatus] platform status query failed: url={}, error={}", url, e.getMessage());
            return null;
        }
    }

    private StatusLookupResult lookupGatewayStatus(BoardEndpoint endpoint) {
        String baseUrl = trimToNull(gatewayUrl);
        if (baseUrl == null) {
            return null;
        }
        String url = UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(baseUrl))
                .path("/udp-proxy/probe/status")
                .queryParam("ip", endpoint.getIp())
                .queryParam("port", normalizePort(endpoint.getPort()))
                .build()
                .toUriString();
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            Map<String, Object> data = extractData(response);
            if (data == null || data.isEmpty()) {
                return null;
            }
            Boolean online = extractOnline(data);
            return online == null ? null : new StatusLookupResult(online, "terminal-gateway");
        } catch (Exception e) {
            log.debug("[InfoBoardStatus] gateway status query failed: url={}, error={}", url, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object code = response.get("code");
        if (code instanceof Number && ((Number) code).intValue() != 200) {
            return null;
        }
        Object data = response.get("data");
        return data instanceof Map ? (Map<String, Object>) data : null;
    }

    private Boolean extractOnline(Map<String, Object> data) {
        Boolean online = toBoolean(data.get("online"));
        if (online != null) {
            return online;
        }
        online = toBoolean(data.get("reachable"));
        if (online != null) {
            return online;
        }
        Object status = data.get("status");
        if (status == null) {
            return null;
        }
        String normalized = String.valueOf(status).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return "\u5728\u7ebf".equals(normalized)
                || "\u6b63\u5e38".equals(normalized)
                || "\u544a\u8b66".equals(normalized)
                || "online".equalsIgnoreCase(normalized)
                || "normal".equalsIgnoreCase(normalized)
                || "warning".equalsIgnoreCase(normalized);
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        return null;
    }

    private boolean probeTcp(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), probeTimeoutMs);
            return true;
        } catch (Exception e) {
            log.debug("[InfoBoardStatus] TCP probe failed {}:{} -> {}", ip, port, e.getMessage());
            return false;
        }
    }

    private boolean probeIcmp(String ip) {
        try {
            return InetAddress.getByName(ip).isReachable(probeTimeoutMs);
        } catch (Exception e) {
            log.debug("[InfoBoardStatus] ICMP probe failed {} -> {}", ip, e.getMessage());
            return false;
        }
    }

    private Integer normalizePort(Integer port) {
        return port != null && port > 0 ? port : defaultPort;
    }

    private String firstNonBlank(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(second);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static class StatusLookupResult {
        private final boolean online;
        private final String source;

        StatusLookupResult(boolean online, String source) {
            this.online = online;
            this.source = source;
        }

        boolean isOnline() {
            return online;
        }

        String getSource() {
            return source;
        }
    }
}

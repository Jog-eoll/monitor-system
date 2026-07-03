package com.infopublish.client.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.infopublish.client.config.AppConfig.ContentReleaseTokenProperties;
import com.infopublish.client.entity.UdpProxyRule;
import com.infopublish.client.entity.dto.ContentAuditItem;
import com.infopublish.client.entity.dto.ContentReleaseTokenIssueRequest;
import com.infopublish.client.entity.dto.ContentReleaseTokenIssueResponse;
import com.infopublish.client.entity.dto.ContentReleaseTokenMetadata;
import com.infopublish.client.mapper.UdpProxyRuleMapper;
import com.infopublish.client.service.ContentReleaseTokenClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentReleaseTokenClientImpl implements ContentReleaseTokenClient {

    private final ContentReleaseTokenProperties properties;
    private final RestTemplate restTemplate;
    private final UdpProxyRuleMapper ruleMapper;

    @Override
    public ContentReleaseTokenIssueResponse issue(ContentAuditItem item, String scanResult, String riskLevel) {
        ContentReleaseTokenIssueResponse failure = new ContentReleaseTokenIssueResponse();
        if (!isEnabled()) {
            failure.setIssued(false);
            failure.setError("CONTENT_RELEASE_TOKEN_DISABLED");
            return failure;
        }
        String issueUrl = trimToNull(properties.getGatewayIssueUrl());
        if (issueUrl == null) {
            failure.setIssued(false);
            failure.setError("CONTENT_RELEASE_TOKEN_ISSUE_URL_EMPTY");
            return failure;
        }
        if (item == null) {
            failure.setIssued(false);
            failure.setError("CONTENT_AUDIT_ITEM_EMPTY");
            return failure;
        }

        try {
            ContentReleaseTokenIssueRequest request = buildRequest(item, scanResult, riskLevel);
            ResponseEntity<Map> response = restTemplate.postForEntity(issueUrl, request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                failure.setIssued(false);
                failure.setError("HTTP_" + response.getStatusCodeValue());
                return failure;
            }
            ContentReleaseTokenIssueResponse issued = parseResponse(response.getBody());
            if (issued == null) {
                failure.setIssued(false);
                failure.setError("TOKEN_RESPONSE_EMPTY");
                return failure;
            }
            String consistencyError = validateConsistency(item, issued);
            if (consistencyError != null) {
                issued.setIssued(false);
                issued.setError(consistencyError);
                return issued;
            }
            return issued;
        } catch (RestClientException e) {
            failure.setIssued(false);
            failure.setError("TOKEN_ISSUE_HTTP_ERROR: " + e.getMessage());
            log.warn("[ContentReleaseToken] issue request failed: {}", e.getMessage());
            return failure;
        } catch (Exception e) {
            failure.setIssued(false);
            failure.setError("TOKEN_ISSUE_ERROR: " + e.getMessage());
            log.warn("[ContentReleaseToken] issue failed: {}", e.getMessage(), e);
            return failure;
        }
    }

    @Override
    public boolean isEnabled() {
        return properties != null && properties.isEnabled();
    }

    private ContentReleaseTokenIssueRequest buildRequest(ContentAuditItem item, String scanResult, String riskLevel) {
        ContentReleaseTokenIssueRequest request = new ContentReleaseTokenIssueRequest();
        UdpProxyRule rule = findRule(item);
        request.setClientId(defaultIfBlank(properties.getClientId(), item.getSourceIp()));
        if (rule != null) {
            request.setRuleId(rule.getRuleId());
            request.setChainId(rule.getChainId());
        }
        request.setSourceIp(item.getSourceIp());
        request.setSourcePort(item.getSourcePort());
        request.setTargetIp(item.getTargetIp());
        request.setTargetPort(item.getTargetPort());
        request.setFileName(item.getFileName());
        request.setFilePath(item.getFilePath());
        request.setFileHash(normalizeHash(item.getFileHash()));
        request.setFileSize(item.getFileSize());
        request.setContentType(item.getContentType());
        request.setScanResult(defaultIfBlank(scanResult, "PASS"));
        request.setRiskLevel(defaultIfBlank(riskLevel, "LOW"));
        request.setPolicyVersion(defaultIfBlank(properties.getPolicyVersion(), "v1"));
        request.setAllowForward(Boolean.TRUE);
        request.setAllowDisplay(Boolean.TRUE);
        request.setTokenTtlMs(Math.max(60_000L, properties.getTokenTtlMs()));
        return request;
    }

    private UdpProxyRule findRule(ContentAuditItem item) {
        if (item == null || ruleMapper == null) {
            return null;
        }
        try {
            LambdaQueryWrapper<UdpProxyRule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UdpProxyRule::getStatus, "ENABLED")
                    .eq(UdpProxyRule::getDeleted, 0)
                    .eq(UdpProxyRule::getListenPort, item.getTargetPort());
            List<UdpProxyRule> rules = ruleMapper.selectList(wrapper);
            if (rules == null || rules.isEmpty()) {
                return null;
            }
            for (UdpProxyRule rule : rules) {
                if (rule == null) {
                    continue;
                }
                if (!matchesListenIp(rule, item.getTargetIp())) {
                    continue;
                }
                if (!matchesSourceIp(rule, item.getSourceIp())) {
                    continue;
                }
                return rule;
            }
        } catch (Exception e) {
            log.debug("[ContentReleaseToken] rule lookup skipped: {}", e.getMessage());
        }
        return null;
    }

    private boolean matchesListenIp(UdpProxyRule rule, String targetIp) {
        String listenIp = trimToNull(rule.getListenIp());
        return listenIp == null || "0.0.0.0".equals(listenIp) || listenIp.equals(targetIp);
    }

    private boolean matchesSourceIp(UdpProxyRule rule, String sourceIp) {
        String configured = trimToNull(rule.getSourceIp());
        if (configured == null) {
            return true;
        }
        for (String item : configured.split(",")) {
            if (sourceIp != null && sourceIp.equals(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private ContentReleaseTokenIssueResponse parseResponse(Map<?, ?> body) {
        if (body == null) {
            return null;
        }
        int code = parseInt(body.get("code"), -1);
        Object dataObj = body.get("data");
        if (!(dataObj instanceof Map)) {
            return null;
        }
        Map<?, ?> data = (Map<?, ?>) dataObj;
        ContentReleaseTokenIssueResponse response = new ContentReleaseTokenIssueResponse();
        response.setIssued(code == 200 && parseBoolean(data.get("issued")));
        response.setTokenId(safeString(data.get("tokenId")));
        response.setFileId(safeString(data.get("fileId")));
        response.setSignedEnvelopeBase64(safeString(data.get("signedEnvelopeBase64")));
        response.setError(safeString(data.get("error")));
        Object metadataObj = data.get("metadata");
        if (metadataObj instanceof Map) {
            response.setMetadata(parseMetadata((Map<?, ?>) metadataObj));
        }
        if (code != 200 && response.getError() == null) {
            response.setError(safeString(body.get("message")));
        }
        return response;
    }

    private ContentReleaseTokenMetadata parseMetadata(Map<?, ?> data) {
        ContentReleaseTokenMetadata metadata = new ContentReleaseTokenMetadata();
        metadata.setVersion(parseIntegerObject(data.get("version")));
        metadata.setTokenId(safeString(data.get("tokenId")));
        metadata.setFileId(safeString(data.get("fileId")));
        metadata.setFileName(safeString(data.get("fileName")));
        metadata.setFilePath(safeString(data.get("filePath")));
        metadata.setFileHash(safeString(data.get("fileHash")));
        metadata.setFileSize(parseLongObject(data.get("fileSize")));
        metadata.setContentType(safeString(data.get("contentType")));
        metadata.setScanResult(safeString(data.get("scanResult")));
        metadata.setRiskLevel(safeString(data.get("riskLevel")));
        metadata.setAllowForward(parseBooleanObject(data.get("allowForward")));
        metadata.setAllowDisplay(parseBooleanObject(data.get("allowDisplay")));
        metadata.setPolicyVersion(safeString(data.get("policyVersion")));
        metadata.setClientId(safeString(data.get("clientId")));
        metadata.setSourceIp(safeString(data.get("sourceIp")));
        metadata.setSourcePort(parseIntegerObject(data.get("sourcePort")));
        metadata.setTargetIp(safeString(data.get("targetIp")));
        metadata.setTargetPort(parseIntegerObject(data.get("targetPort")));
        metadata.setRuleId(safeString(data.get("ruleId")));
        metadata.setChainId(parseLongObject(data.get("chainId")));
        metadata.setIssuedAt(parseLongObject(data.get("issuedAt")));
        metadata.setExpireAt(parseLongObject(data.get("expireAt")));
        metadata.setIssuer(safeString(data.get("issuer")));
        return metadata;
    }

    private String validateConsistency(ContentAuditItem item, ContentReleaseTokenIssueResponse response) {
        if (response == null || !response.isIssued()) {
            return response == null ? "TOKEN_NOT_ISSUED" : defaultIfBlank(response.getError(), "TOKEN_NOT_ISSUED");
        }
        ContentReleaseTokenMetadata metadata = response.getMetadata();
        if (metadata == null) {
            return "TOKEN_METADATA_EMPTY";
        }
        if (!safeEquals(normalizeHash(item.getFileHash()), normalizeHash(metadata.getFileHash()))) {
            return "TOKEN_FILE_HASH_MISMATCH";
        }
        if (!safeEquals(item.getSourceIp(), metadata.getSourceIp())) {
            return "TOKEN_SOURCE_IP_MISMATCH";
        }
        if (!safeEquals(item.getTargetIp(), metadata.getTargetIp())) {
            return "TOKEN_TARGET_IP_MISMATCH";
        }
        if (metadata.getTargetPort() == null || metadata.getTargetPort() != item.getTargetPort()) {
            return "TOKEN_TARGET_PORT_MISMATCH";
        }
        if (!"PASS".equalsIgnoreCase(metadata.getScanResult())) {
            return "TOKEN_SCAN_RESULT_NOT_PASS";
        }
        if (!Boolean.TRUE.equals(metadata.getAllowForward()) || !Boolean.TRUE.equals(metadata.getAllowDisplay())) {
            return "TOKEN_POLICY_DENIED";
        }
        if (metadata.getExpireAt() == null || metadata.getExpireAt() <= System.currentTimeMillis()) {
            return "TOKEN_EXPIRED";
        }
        if (trimToNull(response.getTokenId()) == null || trimToNull(response.getFileId()) == null) {
            return "TOKEN_ID_EMPTY";
        }
        return null;
    }

    private String normalizeHash(String hash) {
        String value = defaultIfBlank(hash, "");
        if (value.regionMatches(true, 0, "SHA256:", 0, 7)) {
            return "SHA256:" + value.substring(7).toLowerCase(Locale.ROOT);
        }
        return "SHA256:" + value.toLowerCase(Locale.ROOT);
    }

    private boolean safeEquals(String left, String right) {
        return defaultIfBlank(left, "").equals(defaultIfBlank(right, ""));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeString(Object value) {
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Long parseLongObject(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseIntegerObject(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean parseBooleanObject(Object value) {
        if (value == null) {
            return null;
        }
        return parseBoolean(value);
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}

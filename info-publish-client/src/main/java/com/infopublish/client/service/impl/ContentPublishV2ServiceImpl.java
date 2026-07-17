package com.infopublish.client.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;
import com.infopublish.client.entity.dto.publish.ContentPublishResponse;
import com.infopublish.client.entity.dto.publish.ContentPublishV2BaseRequest;
import com.infopublish.client.entity.dto.publish.ContentPublishV2PlaylistRequest;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.service.ContentPublishV2Service;
import com.infopublish.client.service.PublishPrecheckV2Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ContentPublishV2ServiceImpl implements ContentPublishV2Service {

    private static final long DELIVERY_STATUS_POLL_INTERVAL_MS = 200L;

    @Resource
    private PublishPrecheckV2Service publishPrecheckV2Service;

    @Resource
    private PublishTempFileService tempFileService;

    @Resource
    private RestTemplate restTemplate;

    @Value("${content-publish.gateway-url:${control-command.gateway-url:http://127.0.0.1:8092}}")
    private String gatewayUrl;

    @Value("${content-publish.v2.file-download-base-url:http://127.0.0.1:7081}")
    private String fileDownloadBaseUrl;

    @Value("${content-publish.v2.max-file-size-mb:50}")
    private long maxFileSizeMb;

    @Value("${content-publish.v2.temp-file-ttl-ms:900000}")
    private long tempFileTtlMs;

    @Value("${content-publish.v2.delivery-timeout-ms:600000}")
    private int defaultDeliveryTimeoutMs;

    private final ConcurrentHashMap<String, ContentPublishResponse> idempotentCache = new ConcurrentHashMap<>();

    @Override
    public ContentPublishResponse publish(String baseJson, String playlistJson, MultipartFile[] files) {
        String requestId = null;
        try {
            ContentPublishV2BaseRequest base = parseBase(baseJson);
            requestId = trimToNull(base.getRequestId());
            if (!hasText(requestId)) {
                return ContentPublishResponse.rejected(null, "INVALID_REQUEST", "requestId 不能为空");
            }
            ContentPublishResponse cached = idempotentCache.get(requestId);
            if (cached != null) {
                log.info("[内容发布V2] 命中幂等缓存: requestId={}", requestId);
                return cached;
            }
            if (!"MEDIA_MULTI_UPLOAD".equalsIgnoreCase(trimToNull(base.getCommand()))) {
                return ContentPublishResponse.rejected(requestId, "INVALID_COMMAND",
                        "V2 内容发布仅支持 MEDIA_MULTI_UPLOAD");
            }
            ContentPublishV2PlaylistRequest playlist = parsePlaylist(playlistJson);
            String playlistId = firstNonBlank(playlist.getPlaylistId(), "V2-" + requestId);
            SigmaVerifyRequest.TargetRef target = convertTarget(base.getTarget());
            String targetError = validateTarget(target);
            if (targetError != null) {
                return ContentPublishResponse.rejected(requestId, "INVALID_TARGET", targetError);
            }

            List<SigmaVerifyRequest.PlaylistItem> items = buildItems(requestId, playlist, files);
            PrecheckRequest precheckRequest = new PrecheckRequest();
            precheckRequest.setRequestId(requestId);
            precheckRequest.setPlaylistId(playlistId);
            precheckRequest.setTarget(target);
            precheckRequest.setItems(items);
            precheckRequest.setOperatorId(base.getOperatorId());
            precheckRequest.setTimeoutMs(resolveTimeoutMs(base));

            PrecheckResponse precheck = publishPrecheckV2Service.precheckV2(precheckRequest);
            if (precheck == null || !precheck.isPublishAllowed() || !hasText(precheck.getPublishPermit())) {
                String message = precheck != null ? precheck.getMessage() : "precheck 无响应";
                ContentPublishResponse response = ContentPublishResponse.rejected(requestId,
                        "PRECHECK_FAILED", message);
                response.attachProgram(precheck, target, playlistId,
                        precheck != null ? precheck.getPlaylistDigest() : null, items, true);
                return response;
            }

            Map<String, Object> deliveryRaw;
            try {
                deliveryRaw = callSecureDelivery(requestId, target, playlistId, items, precheck, base);
            } catch (Exception e) {
                log.warn("[内容发布V2] 加密网关投递失败: requestId={}, error={}",
                        requestId, e.getMessage(), e);
                ContentPublishResponse response = ContentPublishResponse.error(requestId,
                        "DELIVERY_FAILED", "secure delivery submit failed: " + e.getMessage());
                response.attachProgram(precheck, target, playlistId, precheck.getPlaylistDigest(), items, true);
                return response;
            }
            String deliveryTaskId = extractDeliveryTaskId(deliveryRaw);
            if (!hasText(deliveryTaskId)) {
                ContentPublishResponse response = ContentPublishResponse.error(requestId,
                        "DELIVERY_FAILED", "加密网关未返回 deliveryTaskId");
                response.attachProgram(precheck, target, playlistId, precheck.getPlaylistDigest(), items, true);
                response.attachDelivery(null, deliveryRaw);
                return response;
            }

            ContentPublishResponse response = buildDeliveryResponse(requestId, precheck, deliveryTaskId,
                    deliveryRaw, precheckRequest.getEffectiveTimeoutMs());
            response.attachProgram(precheck, target, playlistId, precheck.getPlaylistDigest(), items, true);
            if (response.isSuccess()) {
                idempotentCache.putIfAbsent(requestId, response);
            }
            return response;
        } catch (PublishV2RejectException e) {
            return ContentPublishResponse.rejected(firstNonBlank(requestId, e.getRequestId()),
                    e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[内容发布V2] 执行异常: requestId={}, error={}", requestId, e.getMessage(), e);
            return ContentPublishResponse.error(requestId, "PUBLISH_EXECUTE_V2_FAILED",
                    "内容发布V2执行异常: " + e.getMessage());
        }
    }

    private ContentPublishV2BaseRequest parseBase(String baseJson) {
        if (!hasText(baseJson)) {
            throw new PublishV2RejectException(null, "INVALID_REQUEST", "base 不能为空");
        }
        ContentPublishV2BaseRequest base;
        try {
            base = JSON.parseObject(baseJson, ContentPublishV2BaseRequest.class);
        } catch (Exception e) {
            throw new PublishV2RejectException(null, "INVALID_REQUEST",
                    "base parse failed: " + e.getMessage());
        }
        if (base == null) {
            throw new PublishV2RejectException(null, "INVALID_REQUEST", "base 解析失败");
        }
        return base;
    }

    private ContentPublishV2PlaylistRequest parsePlaylist(String playlistJson) {
        if (!hasText(playlistJson)) {
            throw new PublishV2RejectException(null, "INVALID_PLAYLIST", "playlist 不能为空");
        }
        ContentPublishV2PlaylistRequest playlist;
        try {
            playlist = JSON.parseObject(playlistJson, ContentPublishV2PlaylistRequest.class);
        } catch (Exception e) {
            throw new PublishV2RejectException(null, "INVALID_PLAYLIST",
                    "playlist parse failed: " + e.getMessage());
        }
        if (playlist == null || playlist.getItems() == null || playlist.getItems().isEmpty()) {
            throw new PublishV2RejectException(null, "INVALID_PLAYLIST", "playlist.items 不能为空");
        }
        return playlist;
    }

    private List<SigmaVerifyRequest.PlaylistItem> buildItems(String requestId,
                                                             ContentPublishV2PlaylistRequest playlist,
                                                             MultipartFile[] files) throws Exception {
        Map<String, MultipartFile> byOriginalName = new LinkedHashMap<>();
        Map<String, MultipartFile> byFieldName = new LinkedHashMap<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                if (hasText(file.getOriginalFilename())) {
                    byOriginalName.put(normalizeFileName(file.getOriginalFilename()), file);
                }
                byFieldName.put(file.getName(), file);
            }
        }

        List<SigmaVerifyRequest.PlaylistItem> result = new ArrayList<>();
        Set<MultipartFile> used = Collections.newSetFromMap(new java.util.IdentityHashMap<MultipartFile, Boolean>());
        for (int i = 0; i < playlist.getItems().size(); i++) {
            ContentPublishV2PlaylistRequest.Item src = playlist.getItems().get(i);
            validatePlaylistItem(src, i);
            MultipartFile file = matchFile(src, byOriginalName, byFieldName);
            if (file == null) {
                throw new PublishV2RejectException(requestId, "FILE_NOT_FOUND",
                        "未找到节目文件: " + src.getFileName());
            }
            if (used.contains(file)) {
                throw new PublishV2RejectException(requestId, "FILE_DUPLICATED",
                        "节目文件重复绑定: " + src.getFileName());
            }
            used.add(file);
            PublishTempFileService.StoredFile stored = tempFileService.store(requestId, file,
                    Math.max(1L, maxFileSizeMb) * 1024L * 1024L, tempFileTtlMs);
            if (!stored.getSha256().equalsIgnoreCase(src.getFileHash().trim())) {
                tempFileService.remove(stored.getToken());
                throw new PublishV2RejectException(requestId, "FILE_HASH_MISMATCH",
                        "节目文件 Hash 不匹配: " + src.getFileName());
            }

            SigmaVerifyRequest.PlaylistItem item = new SigmaVerifyRequest.PlaylistItem();
            item.setOrderNo(src.getOrderNo());
            item.setFileName(src.getFileName());
            item.setFileType(src.getFileType());
            item.setDurationSeconds(src.getDurationSeconds());
            item.setFileHash(src.getFileHash());
            item.setFileUrl(buildFileUrl(stored.getToken()));
            result.add(item);
        }
        return result;
    }

    private void validatePlaylistItem(ContentPublishV2PlaylistRequest.Item item, int index) {
        if (item == null) {
            throw new PublishV2RejectException(null, "INVALID_PLAYLIST",
                    "playlist.items[" + index + "] 为空");
        }
        if (item.getOrderNo() == null) {
            throw new PublishV2RejectException(null, "INVALID_PLAYLIST",
                    "playlist.items[" + index + "].orderNo 不能为空");
        }
        if (!hasText(item.getFileName())) {
            throw new PublishV2RejectException(null, "INVALID_PLAYLIST",
                    "playlist.items[" + index + "].fileName 不能为空");
        }
        if (!hasText(item.getFileType())) {
            throw new PublishV2RejectException(null, "INVALID_PLAYLIST",
                    "playlist.items[" + index + "].fileType 不能为空");
        }
        if (item.getDurationSeconds() == null || item.getDurationSeconds() <= 0) {
            throw new PublishV2RejectException(null, "INVALID_PLAYLIST",
                    "playlist.items[" + index + "].durationSeconds 无效");
        }
        if (!hasText(item.getFileHash())) {
            throw new PublishV2RejectException(null, "INVALID_PLAYLIST",
                    "playlist.items[" + index + "].fileHash 不能为空");
        }
    }

    private MultipartFile matchFile(ContentPublishV2PlaylistRequest.Item item,
                                    Map<String, MultipartFile> byOriginalName,
                                    Map<String, MultipartFile> byFieldName) {
        MultipartFile byName = byOriginalName.get(normalizeFileName(item.getFileName()));
        if (byName != null) {
            return byName;
        }
        if (item.getOrderNo() != null) {
            return byFieldName.get("f" + item.getOrderNo());
        }
        return null;
    }

    private Map<String, Object> callSecureDelivery(String requestId,
                                                   SigmaVerifyRequest.TargetRef target,
                                                   String playlistId,
                                                   List<SigmaVerifyRequest.PlaylistItem> items,
                                                   PrecheckResponse precheck,
                                                   ContentPublishV2BaseRequest base) {
        String url = normalizeBaseUrl(gatewayUrl) + "/api/secure-delivery/tasks";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("sigmaPublishId", requestId);
        body.put("publishPermit", precheck.getPublishPermit());
        body.put("target", target);

        Map<String, Object> playlist = new LinkedHashMap<>();
        playlist.put("playlistId", playlistId);
        playlist.put("digest", precheck.getPlaylistDigest());
        body.put("playlist", playlist);
        body.put("files", items);
        body.put("options", buildDeliveryOptions(base.getParams()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("加密网关 HTTP 状态异常: " + response.getStatusCodeValue());
        }
        Map<String, Object> parsed = parseJsonMap(response.getBody());
        if (parsed == null) {
            throw new RuntimeException("加密网关返回空响应");
        }
        String status = parsed.get("status") != null ? String.valueOf(parsed.get("status")) : null;
        if ("FAILED".equalsIgnoreCase(status)) {
            throw new RuntimeException("加密网关投递失败: " + parsed.get("message"));
        }
        return parsed;
    }

    private Map<String, Object> buildDeliveryOptions(Map<String, Object> params) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("clearBeforePublish", booleanParam(params, "clearBeforePublish", true));
        options.put("checkExistence", booleanParam(params, "checkExistence", true));
        Object waitForDelivery = params != null ? params.get("waitForDelivery") : null;
        if (waitForDelivery != null) {
            options.put("waitForDelivery", waitForDelivery);
        }
        return options;
    }

    private ContentPublishResponse buildDeliveryResponse(String requestId,
                                                         PrecheckResponse precheck,
                                                         String deliveryTaskId,
                                                         Map<String, Object> deliveryRaw,
                                                         int timeoutMs) {
        Map<String, Object> deliveryStatus = awaitDeliveryTerminalStatus(deliveryTaskId, timeoutMs);
        Map<String, Object> mergedDelivery = mergeDeliveryStatus(deliveryRaw, deliveryStatus);
        if (isDeliveryFailure(deliveryStatus)) {
            ContentPublishResponse response = ContentPublishResponse.error(requestId,
                    "DELIVERY_FAILED", deliveryFailureMessage(deliveryStatus));
            response.attachDelivery(deliveryTaskId, mergedDelivery);
            return response;
        }
        return ContentPublishResponse.accepted(requestId, precheck, deliveryTaskId, mergedDelivery);
    }

    private Map<String, Object> awaitDeliveryTerminalStatus(String deliveryTaskId, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMs);
        Map<String, Object> lastStatus = null;
        while (System.currentTimeMillis() <= deadline) {
            Map<String, Object> status;
            try {
                status = queryDeliveryTaskStatus(deliveryTaskId);
            } catch (Exception e) {
                log.warn("[内容发布V2] 加密网关状态查询失败: deliveryTaskId={}, error={}",
                        deliveryTaskId, e.getMessage(), e);
                return deliveryStatus("FAILED", deliveryTaskId,
                        "secure delivery status query failed: " + e.getMessage());
            }
            lastStatus = status;
            String state = stringValue(status.get("status"));
            if (isTerminalDeliveryStatus(state) || Boolean.FALSE.equals(status.get("found"))) {
                return status;
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            sleep(Math.min(DELIVERY_STATUS_POLL_INTERVAL_MS, remaining));
        }
        if (lastStatus != null) {
            return mergeDeliveryStatus(lastStatus,
                    deliveryStatus("TIMEOUT", deliveryTaskId, "secure delivery status wait timeout"));
        }
        return deliveryStatus("TIMEOUT", deliveryTaskId, "secure delivery status unavailable");
    }

    private Map<String, Object> queryDeliveryTaskStatus(String deliveryTaskId) {
        String url = normalizeBaseUrl(gatewayUrl) + "/api/secure-delivery/tasks/" + encodePath(deliveryTaskId);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("HTTP " + response.getStatusCodeValue());
        }
        Map<String, Object> parsed = parseJsonMap(response.getBody());
        if (parsed == null) {
            throw new RuntimeException("empty response");
        }
        return parsed;
    }

    private Map<String, Object> parseJsonMap(String body) {
        if (!hasText(body)) {
            return null;
        }
        JSONObject object = JSON.parseObject(body);
        if (object == null) {
            return null;
        }
        return new LinkedHashMap<String, Object>(object);
    }

    private Map<String, Object> mergeDeliveryStatus(Map<String, Object> first,
                                                    Map<String, Object> second) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (first != null) {
            merged.putAll(first);
        }
        if (second != null) {
            merged.putAll(second);
        }
        return merged;
    }

    private Map<String, Object> deliveryStatus(String status, String deliveryTaskId, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("status", status);
        result.put("deliveryTaskId", deliveryTaskId);
        result.put("message", message);
        return result;
    }

    private boolean isDeliveryFailure(Map<String, Object> deliveryStatus) {
        if (deliveryStatus == null) {
            return false;
        }
        if (Boolean.FALSE.equals(deliveryStatus.get("found"))) {
            return true;
        }
        String status = stringValue(deliveryStatus.get("status"));
        return "FAILED".equalsIgnoreCase(status)
                || "TIMEOUT".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status);
    }

    private boolean isTerminalDeliveryStatus(String status) {
        return "SUCCESS".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "TIMEOUT".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status);
    }

    private String deliveryFailureMessage(Map<String, Object> deliveryStatus) {
        String status = deliveryStatus != null ? stringValue(deliveryStatus.get("status")) : null;
        String message = deliveryStatus != null ? stringValue(deliveryStatus.get("message")) : null;
        if (hasText(message)) {
            return "secure delivery failed: " + message;
        }
        if (hasText(status)) {
            return "secure delivery failed: status=" + status;
        }
        return "secure delivery failed";
    }

    private String extractDeliveryTaskId(Map<String, Object> deliveryRaw) {
        if (deliveryRaw == null) {
            return null;
        }
        Object value = deliveryRaw.get("deliveryTaskId");
        if (value == null) {
            value = deliveryRaw.get("taskId");
        }
        Object data = deliveryRaw.get("data");
        if (value == null && data instanceof Map) {
            Map<?, ?> dataMap = (Map<?, ?>) data;
            value = dataMap.get("deliveryTaskId");
            if (value == null) {
                value = dataMap.get("taskId");
            }
        }
        return value != null ? String.valueOf(value) : null;
    }

    private SigmaVerifyRequest.TargetRef convertTarget(ContentPublishV2BaseRequest.TargetRef source) {
        if (source == null) {
            return null;
        }
        SigmaVerifyRequest.TargetRef target = new SigmaVerifyRequest.TargetRef();
        target.setDeviceId(source.getDeviceId());
        target.setIp(source.getIp());
        target.setPort(parsePort(source.getPort()));
        target.setVendorHint(source.getVendorHint());
        return target;
    }

    private String validateTarget(SigmaVerifyRequest.TargetRef target) {
        if (target == null) {
            return "target 不能为空";
        }
        if (!hasText(target.getIp())) {
            return "target.ip 不能为空";
        }
        if (target.getPort() == null || target.getPort() <= 0 || target.getPort() > 65535) {
            return "target.port 无效";
        }
        return null;
    }

    private Integer parsePort(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer resolveTimeoutMs(ContentPublishV2BaseRequest base) {
        if (base.getTimeoutMs() != null && base.getTimeoutMs() > 0) {
            return base.getTimeoutMs();
        }
        Object timeout = base.getParams() != null ? base.getParams().get("timeoutMs") : null;
        if (timeout instanceof Number) {
            return ((Number) timeout).intValue();
        }
        if (timeout != null && hasText(String.valueOf(timeout))) {
            return Integer.valueOf(String.valueOf(timeout));
        }
        return defaultDeliveryTimeoutMs > 0 ? defaultDeliveryTimeoutMs : null;
    }

    private boolean booleanParam(Map<String, Object> params, String key, boolean defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String buildFileUrl(String token) {
        return normalizeBaseUrl(fileDownloadBaseUrl) + "/api/client/publish/v2/files/" + encodePath(token);
    }

    private String encodePath(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            throw new RuntimeException("encode path failed: " + value, e);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeFileName(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first : second;
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("wait delivery status interrupted", e);
        }
    }

    private static class PublishV2RejectException extends RuntimeException {
        private final String requestId;
        private final String code;

        PublishV2RejectException(String requestId, String code, String message) {
            super(message);
            this.requestId = requestId;
            this.code = code;
        }

        String getRequestId() {
            return requestId;
        }

        String getCode() {
            return code;
        }
    }
}

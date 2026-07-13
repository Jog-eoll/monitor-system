package com.infopublish.client.service.impl;

import com.alibaba.fastjson2.JSON;
import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;
import com.infopublish.client.entity.dto.publish.ContentPublishRequest;
import com.infopublish.client.entity.dto.publish.ContentPublishResponse;
import com.infopublish.client.entity.dto.sigma.QingsongProgramResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.service.ContentPublishService;
import com.infopublish.client.service.PublishPrecheckService;
import com.infopublish.client.service.SigmaApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Content publish orchestration for the Qingsong single-callback integration.
 */
@Slf4j
@Service
public class ContentPublishServiceImpl implements ContentPublishService {

    private static final long DELIVERY_STATUS_POLL_INTERVAL_MS = 200L;

    @Resource
    private SigmaApiClient sigmaApiClient;

    @Resource
    private PublishPrecheckService publishPrecheckService;

    @Resource
    private RestTemplate restTemplate;

    @Value("${content-publish.gateway-url:${control-command.gateway-url:http://127.0.0.1:8092}}")
    private String gatewayUrl;

    @Value("${content-publish.download-timeout-ms:120000}")
    private int downloadTimeoutMs;

    @Value("${content-publish.max-file-size-mb:50}")
    private int maxFileSizeMb;

    private final ConcurrentHashMap<String, ContentPublishResponse> idempotentCache = new ConcurrentHashMap<>();

    @Override
    public ContentPublishResponse publish(ContentPublishRequest request) {
        String requestId = request != null ? request.getRequestId() : null;
        try {
            if (request == null) {
                return ContentPublishResponse.error(null, "INVALID_REQUEST", "请求体不能为空");
            }
            ContentPublishResponse cached = hasText(requestId) ? idempotentCache.get(requestId) : null;
            if (cached != null) {
                log.info("[内容发布] 命中幂等缓存: requestId={}, deliveryTaskId={}",
                        requestId, cached.getDelivery() != null ? cached.getDelivery().getDeliveryTaskId() : null);
                return cached;
            }

            String ip = resolveTargetIp(request);
            if (!hasText(ip)) {
                return ContentPublishResponse.rejected(requestId, "INVALID_REQUEST", "target.ip 不能为空");
            }

            log.info("[内容发布] 开始执行: requestId={}, sigmaBaseUrl={}, targetIp={}",
                    requestId, request.getSigmaBaseUrl(), ip);

            QingsongProgramResponse.ProgramData program =
                    sigmaApiClient.getProgramByIp(request.getSigmaBaseUrl(), ip);
            QingsongPlaylistDurationNormalizer.normalizeVideoDuration(program);
            String programError = validateProgram(program, ip);
            if (programError != null) {
                log.warn("[内容发布] 节目单校验失败: requestId={}, reason={}", requestId, programError);
                return ContentPublishResponse.rejected(requestId, "PROGRAM_INVALID", programError);
            }

            verifyProgramFiles(program.getItems());

            PrecheckRequest precheckRequest = buildPrecheckRequest(request, program);
            PrecheckResponse precheck = publishPrecheckService.precheck(precheckRequest);
            if (precheck == null || !precheck.isPublishAllowed() || !hasText(precheck.getPublishPermit())) {
                String message = precheck != null ? precheck.getMessage() : "precheck 无响应";
                log.warn("[内容发布] precheck 未通过: requestId={}, message={}", requestId, message);
                ContentPublishResponse response =
                        ContentPublishResponse.rejected(requestId, "PRECHECK_FAILED", message);
                attachProgram(response, precheck, program, true);
                return response;
            }

            Map<String, Object> deliveryRaw = callSecureDelivery(request, precheck, program);
            String deliveryTaskId = extractDeliveryTaskId(deliveryRaw);
            if (!hasText(deliveryTaskId)) {
                ContentPublishResponse response = ContentPublishResponse.error(requestId,
                        "DELIVERY_FAILED", "加密网关未返回 deliveryTaskId");
                attachProgram(response, precheck, program, true);
                response.attachDelivery(null, deliveryRaw);
                return response;
            }

            ContentPublishResponse response =
                    buildDeliveryResponse(requestId, precheck, deliveryTaskId, deliveryRaw, request);
            attachProgram(response, precheck, program, true);
            if (hasText(requestId)) {
                if (response.isSuccess()) {
                    idempotentCache.putIfAbsent(requestId, response);
                }
            }
            log.info("[内容发布] 投递完成: requestId={}, playlistId={}, deliveryTaskId={}, success={}, status={}",
                    requestId, program.getPlaylistId(), deliveryTaskId, response.isSuccess(),
                    response.getDelivery() != null ? response.getDelivery().getStatus() : null);
            return response;
        } catch (Exception e) {
            log.error("[内容发布] 执行异常: requestId={}, error={}", requestId, e.getMessage(), e);
            return ContentPublishResponse.error(requestId, "PUBLISH_EXECUTE_FAILED",
                    "内容发布执行异常: " + e.getMessage());
        }
    }

    private ContentPublishResponse buildDeliveryResponse(String requestId,
                                                         PrecheckResponse precheck,
                                                         String deliveryTaskId,
                                                         Map<String, Object> deliveryRaw,
                                                         ContentPublishRequest request) {
        Map<String, Object> deliveryStatus = awaitDeliveryTerminalStatus(deliveryTaskId,
                request != null ? request.getEffectiveTimeoutMs() : 600000);
        Map<String, Object> mergedDelivery = mergeDeliveryStatus(deliveryRaw, deliveryStatus);
        if (isDeliveryFailure(deliveryStatus)) {
            String message = deliveryFailureMessage(deliveryStatus);
            log.warn("[content-publish] secure delivery failed: requestId={}, deliveryTaskId={}, status={}, message={}",
                    requestId, deliveryTaskId, stringValue(deliveryStatus.get("status")), message);
            ContentPublishResponse response = ContentPublishResponse.error(requestId,
                    "DELIVERY_FAILED", message);
            response.attachDelivery(deliveryTaskId, mergedDelivery);
            return response;
        }
        return ContentPublishResponse.accepted(requestId, precheck, deliveryTaskId, mergedDelivery);
    }

    private Map<String, Object> awaitDeliveryTerminalStatus(String deliveryTaskId, int timeoutMs) {
        long waitMs = Math.max(1000L, timeoutMs);
        long deadline = System.currentTimeMillis() + waitMs;
        Map<String, Object> lastStatus = null;
        RuntimeException lastError = null;
        while (System.currentTimeMillis() <= deadline) {
            try {
                Map<String, Object> status = queryDeliveryTaskStatus(deliveryTaskId);
                lastStatus = status;
                String state = stringValue(status.get("status"));
                if (isTerminalDeliveryStatus(state) || Boolean.FALSE.equals(status.get("found"))) {
                    return status;
                }
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("[content-publish] query secure delivery status failed: deliveryTaskId={}, error={}",
                        deliveryTaskId, e.getMessage());
                return deliveryStatus("FAILED", deliveryTaskId,
                        "secure delivery status query failed: " + e.getMessage());
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
        String message = lastError != null ? lastError.getMessage() : "secure delivery status unavailable";
        return deliveryStatus("TIMEOUT", deliveryTaskId, message);
    }

    private Map<String, Object> queryDeliveryTaskStatus(String deliveryTaskId) {
        String url = normalizeBaseUrl(gatewayUrl) + "/api/secure-delivery/tasks/" + encodePath(deliveryTaskId);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("HTTP " + response.getStatusCodeValue());
        }
        Map<String, Object> parsed = JSON.parseObject(response.getBody(), Map.class);
        if (parsed == null) {
            throw new RuntimeException("empty response");
        }
        return parsed;
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

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String encodePath(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            throw new RuntimeException("encode path failed: " + value, e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("wait delivery status interrupted", e);
        }
    }

    private PrecheckRequest buildPrecheckRequest(ContentPublishRequest request,
                                                 QingsongProgramResponse.ProgramData program) {
        PrecheckRequest precheckRequest = new PrecheckRequest();
        precheckRequest.setRequestId(request.getRequestId());
        precheckRequest.setSigmaBaseUrl(request.getSigmaBaseUrl());
        precheckRequest.setPlaylistId(program.getPlaylistId());
        precheckRequest.setTarget(program.getTarget());
        precheckRequest.setItems(program.getItems());
        precheckRequest.setOperatorId(request.getOperatorId());
        precheckRequest.setTimeoutMs(request.getTimeoutMs());
        return precheckRequest;
    }

    private Map<String, Object> callSecureDelivery(ContentPublishRequest request,
                                                   PrecheckResponse precheck,
                                                   QingsongProgramResponse.ProgramData program) {
        String url = normalizeBaseUrl(gatewayUrl) + "/api/secure-delivery/tasks";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", request.getRequestId());
        body.put("sigmaPublishId", request.getRequestId());
        body.put("publishPermit", precheck.getPublishPermit());
        body.put("target", program.getTarget());

        Map<String, Object> playlist = new LinkedHashMap<>();
        playlist.put("playlistId", program.getPlaylistId());
        playlist.put("digest", precheck.getPlaylistDigest());
        body.put("playlist", playlist);
        body.put("files", program.getItems());
        body.put("options", buildDeliveryOptions(request.getOptions()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), headers);

        log.info("[内容发布] 调用加密网关投递: requestId={}, url={}, playlistId={}, files={}",
                request.getRequestId(), url, program.getPlaylistId(), program.getItems().size());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        String responseBody = response.getBody();
        Map<String, Object> parsed = JSON.parseObject(responseBody, Map.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("加密网关 HTTP 状态异常: " + response.getStatusCodeValue());
        }
        if (parsed == null) {
            throw new RuntimeException("加密网关返回空响应");
        }
        String status = parsed.get("status") != null ? String.valueOf(parsed.get("status")) : null;
        if ("FAILED".equalsIgnoreCase(status)) {
            throw new RuntimeException("加密网关投递失败: " + parsed.get("message"));
        }
        return parsed;
    }

    private Map<String, Object> buildDeliveryOptions(ContentPublishRequest.Options options) {
        Map<String, Object> deliveryOptions = new LinkedHashMap<>();
        deliveryOptions.put("clearBeforePublish",
                options == null || options.getClearBeforePublish() == null || options.getClearBeforePublish());
        deliveryOptions.put("checkExistence",
                options == null || options.getCheckExistence() == null || options.getCheckExistence());
        if (options != null && options.getWaitForDelivery() != null) {
            deliveryOptions.put("waitForDelivery", options.getWaitForDelivery());
        }
        return deliveryOptions;
    }

    private void verifyProgramFiles(List<SigmaVerifyRequest.PlaylistItem> items) throws Exception {
        for (SigmaVerifyRequest.PlaylistItem item : items) {
            if (!hasText(item.getFileHash())) {
                throw new IllegalStateException("节目文件缺少 fileHash: " + item.getFileName());
            }
            FileVerification verification = verifyProgramFile(item.getFileUrl());
            String actualHash = verification.getSha256();
            if (!actualHash.equalsIgnoreCase(item.getFileHash().trim())) {
                throw new IllegalStateException("节目文件 Hash 不匹配: " + item.getFileName()
                        + ", expected=" + item.getFileHash() + ", actual=" + actualHash);
            }
            log.info("[内容发布] 节目文件校验通过: orderNo={}, fileName={}, size={}",
                    item.getOrderNo(), item.getFileName(), verification.getSizeBytes());
        }
    }

    private FileVerification verifyProgramFile(String fileUrl) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(fileUrl).openConnection();
            connection.setConnectTimeout(downloadTimeoutMs);
            connection.setReadTimeout(downloadTimeoutMs);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("下载节目文件失败: url=" + fileUrl + ", status=" + status);
            }
            long maxBytes = maxFileSizeBytes();
            long declaredLength = connection.getContentLengthLong();
            if (declaredLength > maxBytes) {
                throw new IllegalStateException("节目文件超过大小限制: " + fileUrl);
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0L;
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw new IllegalStateException("节目文件超过大小限制: " + fileUrl);
                    }
                    digest.update(buffer, 0, read);
                }
            }
            return new FileVerification(hex(digest.digest()), total);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private long maxFileSizeBytes() {
        return Math.max(1L, (long) maxFileSizeMb) * 1024L * 1024L;
    }

    private String hex(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static final class FileVerification {
        private final String sha256;
        private final long sizeBytes;

        private FileVerification(String sha256, long sizeBytes) {
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
        }

        private String getSha256() {
            return sha256;
        }

        private long getSizeBytes() {
            return sizeBytes;
        }
    }

    private String validateProgram(QingsongProgramResponse.ProgramData program, String requestedIp) {
        if (program == null) {
            return "获取青松节目单失败";
        }
        if (!Boolean.TRUE.equals(program.getSuccess())) {
            return "青松节目单 success 不为 true";
        }
        if (!hasText(program.getPlaylistId())) {
            return "青松节目单 playlistId 为空";
        }
        if (program.getTarget() == null) {
            return "青松节目单 target 为空";
        }
        if (!hasText(program.getTarget().getIp())) {
            return "青松节目单 target.ip 为空";
        }
        if (!program.getTarget().getIp().trim().equals(requestedIp)) {
            return "青松节目单 target.ip 与请求 IP 不一致";
        }
        if (program.getItems() == null || program.getItems().isEmpty()) {
            return "青松节目单 items 为空";
        }
        for (int i = 0; i < program.getItems().size(); i++) {
            SigmaVerifyRequest.PlaylistItem item = program.getItems().get(i);
            if (item == null) {
                return "青松节目单 items[" + i + "] 为空";
            }
            if (item.getOrderNo() == null) {
                return "青松节目单 items[" + i + "].orderNo 为空";
            }
            if (!hasText(item.getFileName())) {
                return "青松节目单 items[" + i + "].fileName 为空";
            }
            if (!hasText(item.getFileType())) {
                return "青松节目单 items[" + i + "].fileType 为空";
            }
            if (!hasText(item.getFileUrl())) {
                return "青松节目单 items[" + i + "].fileUrl 为空";
            }
            if (item.getDurationSeconds() == null || item.getDurationSeconds() <= 0) {
                return "青松节目单 items[" + i + "].durationSeconds 无效";
            }
        }
        return null;
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

    private void attachProgram(ContentPublishResponse response,
                               PrecheckResponse precheck,
                               QingsongProgramResponse.ProgramData program,
                               boolean filesVerified) {
        if (program != null) {
            response.attachProgram(precheck,
                    program.getTarget(),
                    program.getPlaylistId(),
                    precheck != null ? precheck.getPlaylistDigest() : null,
                    program.getItems(),
                    filesVerified);
            return;
        }
        response.attachProgram(precheck, null, null, null, null, filesVerified);
    }

    private String resolveTargetIp(ContentPublishRequest request) {
        if (request == null || request.getTarget() == null) {
            return null;
        }
        return trimToNull(request.getTarget().getIp());
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
}

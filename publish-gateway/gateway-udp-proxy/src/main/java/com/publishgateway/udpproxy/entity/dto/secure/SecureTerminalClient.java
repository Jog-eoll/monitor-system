package com.publishgateway.udpproxy.entity.dto.secure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.SocketTimeoutException;

/**
 * 加密网关到解密网关的统一 HTTP 客户端
 * <p>
 * 封装 RestTemplate 调用，保证 requestId、deliveryTaskId/commandTaskId
 * 同时写入 HTTP header 和 JSON 信封（由调用方写入信封，本类写入 header）。
 * </p>
 */
@Slf4j
@Component
public class SecureTerminalClient {

    private RestTemplate restTemplate;

    @Value("${secure-delivery.terminal-http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${secure-delivery.terminal-http.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        restTemplate = new RestTemplate(factory);
        log.info("[SecureTerminalClient] 初始化完成: connectTimeoutMs={}, readTimeoutMs={}", connectTimeoutMs, readTimeoutMs);
    }

    /**
     * 发布任务到解密网关
     */
    public ResponseEntity<String> postPublish(String baseUrl,
                                              byte[] encryptedBytes,
                                              String requestId,
                                              String deliveryTaskId) {
        String url = baseUrl + "/api/secure-command/publish";
        return doPost(url, encryptedBytes, requestId, deliveryTaskId, "X-Delivery-Task-Id");
    }

    /**
     * 控制指令到解密网关
     */
    public ResponseEntity<String> postControl(String baseUrl,
                                              byte[] encryptedBytes,
                                              String requestId,
                                              String commandTaskId) {
        String url = baseUrl + "/api/secure-command/control";
        return doPost(url, encryptedBytes, requestId, commandTaskId, "X-Command-Task-Id");
    }

    /**
     * 查询发布任务状态
     */
    public ResponseEntity<String> getPublishStatus(String baseUrl, String orchestrationTaskId) {
        String url = baseUrl + "/api/secure-command/publish-tasks/" + orchestrationTaskId;
        return restTemplate.getForEntity(url, String.class);
    }

    /**
     * 查询控制任务状态
     */
    public ResponseEntity<String> getControlStatus(String baseUrl, String batchTaskId) {
        String url = baseUrl + "/api/secure-command/control-tasks/" + batchTaskId;
        return restTemplate.getForEntity(url, String.class);
    }

    // ════════════════════════════════════════════════════

    private ResponseEntity<String> doPost(String url,
                                          byte[] encryptedBytes,
                                          String requestId,
                                          String taskId,
                                          String taskIdHeaderName) {
        log.info("[SecureTerminalClient] POST 开始: url={}, requestId={}, taskId={}", url, requestId, taskId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("X-Request-Id", requestId != null ? requestId : "");
        headers.set(taskIdHeaderName, taskId != null ? taskId : "");

        HttpEntity<byte[]> entity = new HttpEntity<>(encryptedBytes, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            if (containsCause(e, SocketTimeoutException.class)) {
                log.error("[SecureTerminalClient] 请求超时: url={}, requestId={}, taskId={}, msg={}",
                        url, requestId, taskId, e.getMessage());
                throw new RuntimeException("TIMEOUT: 解密网关请求超时, url=" + url, e);
            }
            log.error("[SecureTerminalClient] 请求连接失败: url={}, requestId={}, taskId={}, msg={}",
                    url, requestId, taskId, e.getMessage());
            throw new RuntimeException("解密网关不可达: " + url, e);
        }

        int httpStatus = response.getStatusCodeValue();
        String body = response.getBody();

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("[SecureTerminalClient] 非 2xx 响应: url={}, requestId={}, taskId={}, httpStatus={}, body={}",
                    url, requestId, taskId, httpStatus, body != null ? body.substring(0, Math.min(body.length(), 500)) : "null");
        } else if (body == null || body.trim().isEmpty()) {
            log.warn("[SecureTerminalClient] 空响应: url={}, requestId={}, taskId={}, httpStatus={}",
                    url, requestId, taskId, httpStatus);
        } else {
            log.info("[SecureTerminalClient] POST 完成: url={}, requestId={}, taskId={}, httpStatus={}",
                    url, requestId, taskId, httpStatus);
        }

        return response;
    }

    private boolean containsCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

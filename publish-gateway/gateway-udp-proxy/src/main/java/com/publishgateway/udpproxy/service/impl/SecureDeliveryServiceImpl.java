package com.publishgateway.udpproxy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gateway.standardization.dto.StandardizedPublishPackage;
import com.publishgateway.udpproxy.config.SecureDeliveryEnvelopeProperties;
import com.publishgateway.udpproxy.entity.dto.delivery.DeliveryTaskStatus;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskRequest;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskResponse;
import com.publishgateway.udpproxy.entity.dto.secure.*;
import com.publishgateway.udpproxy.service.CryptoService;
import com.publishgateway.udpproxy.service.SecureDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * 安全投递服务实现
 * <p>
 * 流程：验证 JWT → 下载文件 → 校验 hash → 构建 StandardizedPublishPackage → 加密 → HTTP 投递到解密网关
 * <p>
 * v2 改造：统一出站信封（SecureGatewayEnvelopeFactory）、统一 HTTP 客户端（SecureTerminalClient）、
 * 统一响应解析（SecureGatewayAckParser）、保存 orchestrationTaskId。
 * 支持配置开关 secure-delivery.envelope.enabled 做灰度和回滚。
 * </p>
 */
@Slf4j
@Service
public class SecureDeliveryServiceImpl implements SecureDeliveryService {

    @Resource
    private CryptoService cryptoService;

    @Resource
    private SecureTerminalClient secureTerminalClient;

    @Resource
    private SecureDeliveryEnvelopeProperties envelopeProperties;

    @Value("${secure-delivery.permit.hmac-secret:change-me-in-production}")
    private String hmacSecret;

    @Value("${secure-delivery.terminal-gateway-url:http://127.0.0.1:8093}")
    private String terminalGatewayUrl;

    @Value("${secure-delivery.download.timeout-ms:30000}")
    private int downloadTimeoutMs;

    @Value("${secure-delivery.download.max-file-size-mb:50}")
    private int maxFileSizeMb;

    /** v1 进程内任务状态存储 */
    private final ConcurrentHashMap<String, DeliveryTaskStatus> taskStore = new ConcurrentHashMap<>();

    private ExecutorService executor;

    @PostConstruct
    public void init() {
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "delivery-worker");
            t.setDaemon(true);
            return t;
        });
        log.info("[安全投递] 初始化完成: terminalUrl={}, envelope.enabled={}, envelope.schemaVersion={}",
                terminalGatewayUrl, envelopeProperties.isEnabled(), envelopeProperties.getSchemaVersion());
    }

    @Override
    public SecureDeliveryTaskResponse createTask(SecureDeliveryTaskRequest request) {
        SecureDeliveryTaskRequest plainRequest;
        try {
            plainRequest = resolvePlainRequest(request);
        } catch (Exception e) {
            log.warn("[安全投递] 任务包解密失败: {}", e.getMessage());
            return SecureDeliveryTaskResponse.error("任务包解密失败: " + e.getMessage());
        }

        String permitPayload = verifyJwt(plainRequest.getPublishPermit());
        if (permitPayload == null) {
            return SecureDeliveryTaskResponse.error("publishPermit 验签失败");
        }
        String permitError = validatePermitClaims(plainRequest, permitPayload);
        if (permitError != null) {
            return SecureDeliveryTaskResponse.error(permitError);
        }

        String taskId = "DLV-" + UUID.randomUUID().toString().substring(0, 8);
        DeliveryTaskStatus status = new DeliveryTaskStatus();
        status.setDeliveryTaskId(taskId);
        status.setSigmaPublishId(plainRequest.getSigmaPublishId());
        status.setStatus("ACCEPTED");
        status.setCreatedAt(System.currentTimeMillis());
        status.setUpdatedAt(System.currentTimeMillis());
        status.setTotalFiles(plainRequest.getFiles() != null ? plainRequest.getFiles().size() : 0);
        taskStore.put(taskId, status);

        // 异步执行投递
        executor.submit(() -> executeDelivery(taskId, plainRequest));

        return SecureDeliveryTaskResponse.created(taskId);
    }

    @Override
    public DeliveryTaskStatus getTaskStatus(String deliveryTaskId) {
        return taskStore.get(deliveryTaskId);
    }

    @Override
    public boolean isHealthy() {
        return taskStore.size() < 10000;
    }

    // ════════════════════════════════════════════════════
    // 核心投递流程
    // ════════════════════════════════════════════════════

    private void executeDelivery(String taskId, SecureDeliveryTaskRequest request) {
        DeliveryTaskStatus status = taskStore.get(taskId);
        status.setStatus("RUNNING");
        status.setUpdatedAt(System.currentTimeMillis());

        try {
            // 2. 下载文件
            List<StandardizedPublishPackage.FileEntry> fileEntries = new ArrayList<>();
            if (request.getFiles() != null) {
                for (SecureDeliveryTaskRequest.FileRef ref : request.getFiles()) {
                    byte[] data = downloadFile(ref.getFileUrl());
                    if (data == null) {
                        throw new RuntimeException("文件下载失败: " + ref.getFileUrl());
                    }
                    // 校验 hash（可选）
                    if (ref.getFileHash() != null && !ref.getFileHash().isEmpty()) {
                        String actualHash = sha256Hex(data);
                        if (!ref.getFileHash().equalsIgnoreCase(actualHash)) {
                            throw new RuntimeException("文件 hash 不匹配: " + ref.getFileName()
                                    + " expected=" + ref.getFileHash() + " actual=" + actualHash);
                        }
                    }
                    StandardizedPublishPackage.FileEntry entry = new StandardizedPublishPackage.FileEntry();
                    entry.setOrderNo(ref.getOrderNo());
                    entry.setFileName(ref.getFileName());
                    entry.setFileType(ref.getFileType());
                    entry.setContentBase64(Base64.getEncoder().encodeToString(data));
                    entry.setDurationSeconds(ref.getDurationSeconds());
                    entry.setFileHash(ref.getFileHash());
                    fileEntries.add(entry);
                    status.setDownloadedFiles(status.getDownloadedFiles() + 1);
                }
            }

            // 3. 构建 StandardizedPublishPackage
            StandardizedPublishPackage pkg = new StandardizedPublishPackage();
            pkg.setDeliveryTaskId(taskId);
            pkg.setSigmaPublishId(request.getSigmaPublishId());
            pkg.setAction("PUBLISH_PLAYLIST");
            pkg.setPublishPermit(request.getPublishPermit());

            StandardizedPublishPackage.TargetRef target = new StandardizedPublishPackage.TargetRef();
            if (request.getTarget() != null) {
                target.setDeviceId(request.getTarget().getDeviceId());
                target.setIp(request.getTarget().getIp());
                target.setPort(request.getTarget().getPort());
                target.setVendorHint(request.getTarget().getVendorHint());
            }
            pkg.setTarget(target);

            StandardizedPublishPackage.PlaylistRef playlist = new StandardizedPublishPackage.PlaylistRef();
            if (request.getPlaylist() != null) {
                playlist.setPlaylistId(request.getPlaylist().getPlaylistId());
                playlist.setDigest(request.getPlaylist().getDigest());
            }
            pkg.setPlaylist(playlist);
            pkg.setFiles(fileEntries);

            StandardizedPublishPackage.PublishOptions opts = new StandardizedPublishPackage.PublishOptions();
            if (request.getOptions() != null) {
                opts.setClearBeforePublish(request.getOptions().isClearBeforePublish());
                opts.setCheckExistence(request.getOptions().isCheckExistence());
            }
            pkg.setOptions(opts);

            // 4. 构建信封并加密
            byte[] envelopeBytes;
            if (envelopeProperties.isEnabled()) {
                // v2 信封模式
                SecureGatewayEnvelope.TargetRef envelopeTarget = new SecureGatewayEnvelope.TargetRef();
                envelopeTarget.setDeviceId(target.getDeviceId());
                envelopeTarget.setIp(target.getIp());
                envelopeTarget.setPort(target.getPort());
                envelopeTarget.setVendorHint(target.getVendorHint());

                SecureGatewayEnvelope envelope = SecureGatewayEnvelopeFactory.publish(
                        envelopeProperties.getSchemaVersion(),
                        request.getRequestId(),
                        taskId,
                        null,
                        envelopeTarget,
                        pkg
                );
                envelopeBytes = SecureGatewayEnvelopeFactory.toBytes(envelope);
            } else {
                // 回退：旧扁平 JSON（真正的不带 schemaVersion/messageType/publish 包裹层）
                JSONObject payload = buildLegacyPublishPayload(taskId, request, pkg, target, playlist, fileEntries, opts);
                envelopeBytes = JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8);
            }

            // 5. 加密并投递
            byte[] encrypted = cryptoService.encrypt(envelopeBytes);
            if (encrypted == null) {
                status.setStatus("FAILED");
                status.setMessage("encrypt failed: " + cryptoService.getSvacModuleStatus());
                log.warn("[安全投递] 加密失败，停止投递: taskId={}, requestId={}, cryptoStatus={}",
                        taskId, request.getRequestId(), cryptoService.getSvacModuleStatus());
                return;
            }
            deliverToTerminal(taskId, request.getRequestId(), encrypted, status);

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            boolean isTimeout = errorMsg != null && errorMsg.startsWith("TIMEOUT:");
            status.setStatus(isTimeout ? "TIMEOUT" : "FAILED");
            status.setMessage(isTimeout ? ("解密网关请求超时: " + errorMsg) : errorMsg);
            log.error("[安全投递] 任务失败: taskId={}, status={}, error={}",
                    taskId, status.getStatus(), errorMsg, e);
        } finally {
            status.setUpdatedAt(System.currentTimeMillis());
        }
    }

    /**
     * 投递到解密网关，使用 SecureTerminalClient + SecureGatewayAckParser
     */
    private void deliverToTerminal(String taskId, String requestId, byte[] encryptedPayload, DeliveryTaskStatus status) {
        ResponseEntity<String> response = secureTerminalClient.postPublish(
                terminalGatewayUrl, encryptedPayload, requestId, taskId);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("解密网关投递失败: HTTP " + response.getStatusCodeValue()
                    + " body=" + response.getBody());
        }

        // 用 SecureGatewayAckParser 统一解析响应
        SecureGatewayAck ack = SecureGatewayAckParser.parse(response.getBody());

        // 保存 orchestrationTaskId，即使失败也保存以便排障
        if (ack.getOrchestrationTaskId() != null) {
            status.setOrchestrationTaskId(ack.getOrchestrationTaskId());
        }

        if (ack.isTerminalFailure()) {
            throw new RuntimeException("解密网关执行失败: " + ack.getErrorMessage());
        }

        status.setDeliveredFiles(status.getDownloadedFiles());
        status.setStatus("SUCCESS");
        log.info("[安全投递] 解密网关已接收: taskId={}, requestId={}, orchestrationTaskId={}, accepted={}",
                taskId, requestId, ack.getOrchestrationTaskId(), ack.getAccepted());
    }

    // ════════════════════════════════════════════════════
    // 回退：旧扁平 JSON 构建（envelope.enabled=false 时使用）
    // ════════════════════════════════════════════════════

    /**
     * 回退：旧扁平 JSON（envelope.enabled=false 时使用）
     * 必须完全去掉 schemaVersion、messageType、publish 包裹层，让解密网关 SecureEnvelopeParser 进入旧 DTO 回退路径。
     * 根节点直接放：deliveryTaskId、requestId、target、playlist、files、options、publishPermit
     */
    private JSONObject buildLegacyPublishPayload(String taskId, SecureDeliveryTaskRequest request,
                                                  StandardizedPublishPackage pkg,
                                                  StandardizedPublishPackage.TargetRef target,
                                                  StandardizedPublishPackage.PlaylistRef playlist,
                                                  List<StandardizedPublishPackage.FileEntry> fileEntries,
                                                  StandardizedPublishPackage.PublishOptions opts) {
        JSONObject payload = new JSONObject();
        payload.put("deliveryTaskId", taskId);
        payload.put("requestId", request.getRequestId());
        payload.put("publishPermit", pkg.getPublishPermit());

        JSONObject targetRef = new JSONObject();
        targetRef.put("deviceId", target.getDeviceId());
        targetRef.put("ip", target.getIp());
        targetRef.put("port", target.getPort());
        targetRef.put("vendorHint", target.getVendorHint());
        payload.put("target", targetRef);

        payload.put("playlist", playlist);
        payload.put("files", fileEntries);
        payload.put("options", opts);

        return payload;
    }

    // ════════════════════════════════════════════════════
    // 工具方法
    // ════════════════════════════════════════════════════

    private SecureDeliveryTaskRequest resolvePlainRequest(SecureDeliveryTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (isBlank(request.getEncryptedPackage())) {
            return request;
        }
        byte[] encrypted = Base64.getDecoder().decode(request.getEncryptedPackage());
        byte[] plain = cryptoService.decrypt(encrypted);
        if (plain == null) {
            throw new IllegalArgumentException("任务包解密失败: " + cryptoService.getSvacModuleStatus());
        }
        SecureDeliveryTaskRequest plainRequest = JSON.parseObject(
                new String(plain, StandardCharsets.UTF_8), SecureDeliveryTaskRequest.class);
        if (plainRequest == null) {
            throw new IllegalArgumentException("加密任务包解析为空");
        }
        if (isBlank(plainRequest.getEncryptedPackageId())) {
            plainRequest.setEncryptedPackageId(request.getEncryptedPackageId());
        }
        return plainRequest;
    }

    private String verifyJwt(String token) {
        if (isBlank(token)) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String expectedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            if (!MessageDigest.isEqual(
                    expectedSig.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[安全投递] JWT 验签异常: {}", e.getMessage());
            return null;
        }
    }

    private String validatePermitClaims(SecureDeliveryTaskRequest request, String payloadJson) {
        try {
            JSONObject claims = JSON.parseObject(payloadJson);
            Long exp = claims.getLong("exp");
            long now = System.currentTimeMillis() / 1000;
            if (exp == null || exp <= now) {
                return "publishPermit 已过期";
            }

            String claimDigest = claims.getString("playlistDigest");
            if (isBlank(claimDigest)) {
                return "publishPermit 缺少 playlistDigest";
            }
            if (request.getPlaylist() == null) {
                return "请求缺少 playlist";
            }
            String requestDigest = request.getPlaylist().getDigest();
            if (isBlank(requestDigest)) {
                request.getPlaylist().setDigest(claimDigest);
            } else if (!claimDigest.equalsIgnoreCase(requestDigest)) {
                return "playlist.digest 与 publishPermit 不一致";
            }
            return null;
        } catch (Exception e) {
            log.warn("[安全投递] JWT payload 解析失败: {}", e.getMessage());
            return "publishPermit payload 解析失败";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private byte[] downloadFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) return null;
        try {
            URL url = new URL(fileUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(downloadTimeoutMs);
            conn.setReadTimeout(downloadTimeoutMs);
            conn.setRequestMethod("GET");

            int maxBytes = maxFileSizeMb * 1024 * 1024;
            try (InputStream is = conn.getInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int total = 0;
                int n;
                while ((n = is.read(buf)) > 0) {
                    total += n;
                    if (total > maxBytes) {
                        throw new RuntimeException("文件超过最大限制: " + maxFileSizeMb + "MB");
                    }
                    bos.write(buf, 0, n);
                }
                return bos.toByteArray();
            }
        } catch (Exception e) {
            log.error("[安全投递] 文件下载失败: url={}, error={}", fileUrl, e.getMessage());
            return null;
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }
}

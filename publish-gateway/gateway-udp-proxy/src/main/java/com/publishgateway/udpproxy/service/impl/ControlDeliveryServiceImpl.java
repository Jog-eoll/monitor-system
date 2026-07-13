package com.publishgateway.udpproxy.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.publishgateway.udpproxy.config.SecureDeliveryEnvelopeProperties;
import com.publishgateway.udpproxy.entity.dto.control.*;
import com.publishgateway.udpproxy.entity.dto.secure.*;
import com.publishgateway.udpproxy.service.ControlDeliveryService;
import com.publishgateway.udpproxy.service.CryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * 控制指令投递服务实现 —— 加密 + 异步投递到解密网关。
 * <p>
 * v2 改造：
 * - 白名单与解密网关完全一致（POWER 暂不放行）
 * - 用 SecureGatewayEnvelopeFactory 构建信封
 * - 用 SecureTerminalClient 统一 HTTP 投递
 * - 用 SecureGatewayAckParser 统一响应解析
 * - 支持配置开关 secure-delivery.envelope.enabled 做灰度和回滚
 * </p>
 */
@Slf4j
@Service
public class ControlDeliveryServiceImpl implements ControlDeliveryService {

    @Resource
    private CryptoService cryptoService;

    @Resource
    private SecureTerminalClient secureTerminalClient;

    @Resource
    private SecureDeliveryEnvelopeProperties envelopeProperties;

    @Value("${secure-delivery.terminal-gateway-url:http://127.0.0.1:8093}")
    private String terminalGatewayUrl;

    @Value("${control-delivery.http-timeout-ms:10000}")
    private int httpTimeoutMs;

    /**
     * 控制指令白名单 —— 与解密网关完全一致。
     * POWER 暂不放行，除非解密网关补齐真实映射。
     */
    private static final Set<String> COMMAND_WHITELIST = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "BRIGHTNESS", "BLACKOUT", "TIME_SYNC", "QUERY_STATUS", "REBOOT",
                    "NTP_SET", "DEVICE_IP_SET", "SCREEN_ATTRIBUTE_SET",
                    "FONTS_GET", "FONTS_SYNC", "AP_NETWORK_SWITCH"
            ))
    );

    private final ConcurrentHashMap<String, ControlTaskStatus> taskStore = new ConcurrentHashMap<>();
    private ExecutorService deliveryExecutor;

    @PostConstruct
    public void init() {
        deliveryExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "control-delivery-worker");
            t.setDaemon(true);
            return t;
        });
        log.info("[控制投递] 服务初始化完成: terminalGatewayUrl={}, envelope.enabled={}, whitelist={}",
                terminalGatewayUrl, envelopeProperties.isEnabled(), COMMAND_WHITELIST);
    }

    @PreDestroy
    public void destroy() {
        if (deliveryExecutor != null) {
            deliveryExecutor.shutdownNow();
        }
    }

    @Override
    public ControlTaskEncryptResponse encryptControlTask(ControlTaskEncryptRequest request) {
        log.info("[控制投递] 加密控制任务: commandTaskId={}", request.getCommandTaskId());

        byte[] plaintext = request.getPlaintextControlTask().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cryptoService.encrypt(plaintext);

        String packageId = "ECP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        log.info("[控制投递] 加密完成: commandTaskId={}, packageId={}, plainSize={}, encryptedSize={}",
                request.getCommandTaskId(), packageId, plaintext.length, encrypted.length);

        return ControlTaskEncryptResponse.success(packageId, encrypted);
    }

    @Override
    public ControlDeliveryTaskResponse createControlTask(ControlDeliveryTaskRequest request) {
        String commandTaskId = request.getCommandTaskId();
        JSONObject plainTask = decryptAndValidate(request);
        String command = plainTask.getString("command");
        String deliveryTaskId = "CDLV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // 记录任务状态
        ControlTaskStatus status = new ControlTaskStatus();
        status.setCommandTaskId(commandTaskId);
        status.setDeliveryTaskId(deliveryTaskId);
        status.setStatus("ACCEPTED");
        status.setCommand(command);
        status.setCreatedAt(System.currentTimeMillis());
        status.setUpdatedAt(System.currentTimeMillis());
        taskStore.put(deliveryTaskId, status);

        // 异步投递
        deliveryExecutor.submit(() -> executeDelivery(deliveryTaskId, plainTask));

        log.info("[控制投递] 任务已创建: deliveryTaskId={}, commandTaskId={}, command={}",
                deliveryTaskId, commandTaskId, command);

        return ControlDeliveryTaskResponse.created(deliveryTaskId);
    }

    @Override
    public Map<String, Object> getControlTaskStatus(String commandTaskId) {
        // 先按 commandTaskId 查找，再按 deliveryTaskId 查找
        ControlTaskStatus found = null;
        for (ControlTaskStatus s : taskStore.values()) {
            if (commandTaskId.equals(s.getCommandTaskId()) || commandTaskId.equals(s.getDeliveryTaskId())) {
                found = s;
                break;
            }
        }

        Map<String, Object> result = new HashMap<>();
        if (found == null) {
            result.put("found", false);
            result.put("commandTaskId", commandTaskId);
            return result;
        }

        if ("RUNNING".equals(found.getStatus()) && found.getBatchTaskId() != null) {
            refreshTerminalStatus(found);
        }

        result.put("found", true);
        result.put("commandTaskId", found.getCommandTaskId());
        result.put("deliveryTaskId", found.getDeliveryTaskId());
        result.put("status", found.getStatus());
        result.put("command", found.getCommand());
        result.put("batchTaskId", found.getBatchTaskId());
        result.put("mappedCapability", found.getMappedCapability());
        result.put("terminalStatus", found.getTerminalStatus());
        result.put("message", found.getMessage());
        result.put("createdAt", found.getCreatedAt());
        result.put("updatedAt", found.getUpdatedAt());
        if (found.getTerminalTask() != null) {
            result.put("terminalTask", found.getTerminalTask());
        }
        if (found.getTerminalResults() != null) {
            result.put("terminalResults", found.getTerminalResults());
        }
        return result;
    }

    // ═══════════════════════════ 异步投递 ═══════════════════════════

    private void executeDelivery(String deliveryTaskId, JSONObject plainTask) {
        ControlTaskStatus status = taskStore.get(deliveryTaskId);
        if (status == null) return;

        status.setStatus("RUNNING");
        status.setUpdatedAt(System.currentTimeMillis());

        try {
            byte[] envelopeBytes;
            String commandTaskId = plainTask.getString("commandTaskId");

            if (envelopeProperties.isEnabled()) {
                // v2 信封模式：用 SecureGatewayEnvelopeFactory 构建
                SecureGatewayEnvelope.TargetRef targetRef = buildEnvelopeTarget(plainTask.getJSONObject("target"));
                String sourceClientId = null;
                JSONObject sourceObj = plainTask.getJSONObject("source");
                if (sourceObj != null) {
                    sourceClientId = sourceObj.getString("clientId");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> params = plainTask.getJSONObject("params") != null
                        ? new LinkedHashMap<>(plainTask.getJSONObject("params"))
                        : new LinkedHashMap<>();

                SecureGatewayEnvelope envelope = SecureGatewayEnvelopeFactory.control(
                        envelopeProperties.getSchemaVersion(),
                        commandTaskId,
                        commandTaskId,
                        sourceClientId,
                        targetRef,
                        plainTask.getString("command"),
                        params
                );
                envelopeBytes = SecureGatewayEnvelopeFactory.toBytes(envelope);
            } else {
                // 回退：旧扁平 JSON（真正的不带 schemaVersion/messageType/control 包裹层）
                JSONObject payload = buildLegacyControlPayload(plainTask);
                envelopeBytes = JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8);
            }

            byte[] encryptedBytes = cryptoService.encrypt(envelopeBytes);

            // 用 SecureTerminalClient 统一投递
            ResponseEntity<String> response = secureTerminalClient.postControl(
                    terminalGatewayUrl, encryptedBytes, commandTaskId, commandTaskId);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("解密网关投递失败: HTTP " + response.getStatusCodeValue()
                        + " body=" + response.getBody());
            }

            // 用 SecureGatewayAckParser 统一解析
            SecureGatewayAck ack = SecureGatewayAckParser.parse(response.getBody());

            if (!ack.isTerminalFailure()) {
                if (ack.isDuplicateAccepted()) {
                    status.setStatus("SUCCESS");
                    status.setTerminalStatus(ack.getStatus());
                    status.setBatchTaskId(ack.getBatchTaskId());
                    status.setMappedCapability(ack.getMappedCapability());
                    status.setMessage("幂等回放: 控制指令已处理");
                    log.info("[控制投递] 幂等回放成功: deliveryTaskId={}, batchTaskId={}",
                            deliveryTaskId, ack.getBatchTaskId());
                } else {
                    status.setStatus("RUNNING");
                    status.setBatchTaskId(ack.getBatchTaskId());
                    status.setMappedCapability(ack.getMappedCapability());
                    status.setTerminalStatus(ack.getStatus());
                    status.setMessage("解密网关已接受控制指令");
                    log.info("[控制投递] 解密网关已接受: deliveryTaskId={}, batchTaskId={}, capability={}",
                            deliveryTaskId, ack.getBatchTaskId(), ack.getMappedCapability());
                }
            } else {
                status.setStatus("FAILED");
                status.setTerminalStatus(ack.getStatus());
                status.setMessage(ack.getErrorMessage());
                log.warn("[控制投递] 解密网关拒绝: deliveryTaskId={}, message={}", deliveryTaskId, ack.getErrorMessage());
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            boolean isTimeout = errorMsg != null && errorMsg.startsWith("TIMEOUT:");
            status.setStatus(isTimeout ? "TIMEOUT" : "FAILED");
            status.setMessage(isTimeout ? ("解密网关请求超时: " + errorMsg) : ("投递异常: " + errorMsg));
            log.error("[控制投递] 投递异常: deliveryTaskId={}, status={}, error={}",
                    deliveryTaskId, status.getStatus(), errorMsg, e);
        } finally {
            status.setUpdatedAt(System.currentTimeMillis());
        }
    }

    // ═══════════════════════════ 验证与构建 ═══════════════════════════

    private JSONObject decryptAndValidate(ControlDeliveryTaskRequest request) {
        try {
            if (request == null || request.getEncryptedCommandPackage() == null
                    || request.getEncryptedCommandPackage().trim().isEmpty()) {
                throw new IllegalArgumentException("encryptedCommandPackage 不能为空");
            }
            byte[] encryptedBytes = Base64.getDecoder().decode(request.getEncryptedCommandPackage());
            byte[] plainBytes;
            try {
                plainBytes = cryptoService.decrypt(encryptedBytes);
                if (plainBytes == null || plainBytes.length == 0) {
                    throw new IllegalStateException("empty decrypt result");
                }
            } catch (Exception decryptEx) {
                // 明文兼容：平台侧无 CryptoService，尝试直接将 Base64 解码后的字节作为明文 JSON 降级处理
                String plainText = new String(encryptedBytes, StandardCharsets.UTF_8);
                JSONObject plainFallback = null;
                try {
                    plainFallback = JSON.parseObject(plainText);
                } catch (Exception parseEx) {
                    plainFallback = null;
                }
                if (plainFallback != null && plainFallback.getString("command") != null) {
                    log.warn("[控制投递] 控制任务包为明文 JSON，降级处理: commandTaskId={}, command={}",
                            request.getCommandTaskId(), plainFallback.getString("command"));
                    plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
                } else {
                    throw new IllegalArgumentException(
                            "控制任务包解密失败: " + decryptEx.getMessage(), decryptEx);
                }
            }
            JSONObject plain = JSON.parseObject(new String(plainBytes, StandardCharsets.UTF_8));
            if (plain == null) {
                throw new IllegalArgumentException("控制任务包为空");
            }
            String commandTaskId = trimToNull(request.getCommandTaskId());
            if (commandTaskId == null) {
                commandTaskId = trimToNull(plain.getString("commandTaskId"));
            }
            if (commandTaskId == null) {
                commandTaskId = trimToNull(plain.getString("taskId"));
            }
            if (commandTaskId == null) {
                throw new IllegalArgumentException("commandTaskId 不能为空");
            }
            if (plain.getString("commandTaskId") != null && !commandTaskId.equals(plain.getString("commandTaskId"))) {
                throw new IllegalArgumentException("commandTaskId 不一致");
            }
            if (plain.getString("taskId") != null && !commandTaskId.equals(plain.getString("taskId"))) {
                throw new IllegalArgumentException("taskId 不一致");
            }
            plain.put("commandTaskId", commandTaskId);
            plain.put("taskId", commandTaskId);
            plain.put("encryptedCommandPackageId", request.getEncryptedCommandPackageId());

            JSONObject source = plain.getJSONObject("source");
            JSONObject target = plain.getJSONObject("target");
            if (source == null || trimToNull(source.getString("clientId")) == null) {
                throw new IllegalArgumentException("source.clientId 不能为空");
            }
            if (target == null) {
                throw new IllegalArgumentException("target 不能为空");
            }
            if (trimToNull(target.getString("ip")) == null && trimToNull(target.getString("deviceId")) == null) {
                throw new IllegalArgumentException("target.ip 或 target.deviceId 不能为空");
            }
            if (target.getInteger("port") == null || target.getInteger("port") <= 0) {
                throw new IllegalArgumentException("target.port 不能为空");
            }
            String command = trimToNull(plain.getString("command"));
            if (command == null || !COMMAND_WHITELIST.contains(command.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("不支持的控制指令: " + command
                        + "，当前白名单: " + COMMAND_WHITELIST);
            }
            plain.put("command", command.toUpperCase(Locale.ROOT));
            if (trimToNull(request.getCommand()) != null
                    && !request.getCommand().trim().equalsIgnoreCase(plain.getString("command"))) {
                throw new IllegalArgumentException("外层 command 与控制任务包不一致");
            }
            String paramsError = validateCommandParams(plain.getString("command"), plain.getJSONObject("params"));
            if (paramsError != null) {
                throw new IllegalArgumentException(paramsError);
            }
            return plain;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("控制任务包解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 参数校验与解密侧别名保持一致
     */
    private String validateCommandParams(String command, JSONObject params) {
        if ("BRIGHTNESS".equals(command)) {
            if (params == null || params.get("brightness") == null) {
                return "BRIGHTNESS 缺少 brightness 参数";
            }
            Integer brightness = params.getInteger("brightness");
            if (brightness == null || brightness < 0 || brightness > 100) {
                return "brightness 必须在 0-100 之间";
            }
        } else if ("BLACKOUT".equals(command)) {
            if (params == null || params.get("enabled") == null) {
                return "BLACKOUT 缺少 enabled 参数";
            }
            Object enabled = params.get("enabled");
            if (!(enabled instanceof Boolean)
                    && !"true".equalsIgnoreCase(String.valueOf(enabled))
                    && !"false".equalsIgnoreCase(String.valueOf(enabled))) {
                return "enabled 必须为布尔值";
            }
        } else if ("NTP_SET".equals(command)) {
            // 别名兼容：ntpServer/server
            if (params == null || (trimToNull(params.getString("ntpServer")) == null
                    && trimToNull(params.getString("server")) == null)) {
                return "NTP_SET 缺少 ntpServer/server 参数";
            }
        } else if ("DEVICE_IP_SET".equals(command)) {
            // 别名兼容：ip/mask/netmask/subnetMask/gateway/dns
            if (params == null || trimToNull(params.getString("ip")) == null) {
                return "DEVICE_IP_SET 缺少 ip 参数";
            }
            // mask/subnetMask 别名统一
            if (trimToNull(params.getString("mask")) != null && trimToNull(params.getString("subnetMask")) == null) {
                params.put("subnetMask", params.getString("mask"));
            }
        } else if ("SCREEN_ATTRIBUTE_SET".equals(command)) {
            if (params == null || params.get("width") == null || params.get("height") == null) {
                return "SCREEN_ATTRIBUTE_SET 缺少 width/height 参数";
            }
        } else if ("FONTS_SYNC".equals(command)) {
            // 别名兼容：jetFileIIFonts/novaStarFonts
            if (params == null || (params.get("jetFileIIFonts") == null && params.get("novaStarFonts") == null)) {
                return "FONTS_SYNC 缺少字体列表参数";
            }
        } else if ("AP_NETWORK_SWITCH".equals(command)) {
            // 别名兼容：enable/enabled/on
            if (params == null || (params.get("enable") == null && params.get("enabled") == null && params.get("on") == null)) {
                return "AP_NETWORK_SWITCH 缺少 enable/enabled/on 参数";
            }
            // on → enable 别名统一
            if (params.get("on") != null && params.get("enable") == null && params.get("enabled") == null) {
                params.put("enable", params.get("on"));
            }
        }
        // FONTS_GET, QUERY_STATUS, REBOOT, TIME_SYNC 无强制参数要求
        return null;
    }

    // ═══════════════════════════ 信封构建 ═══════════════════════════

    private SecureGatewayEnvelope.TargetRef buildEnvelopeTarget(JSONObject target) {
        SecureGatewayEnvelope.TargetRef ref = new SecureGatewayEnvelope.TargetRef();
        if (target != null) {
            ref.setDeviceId(target.getString("deviceId"));
            ref.setIp(target.getString("ip"));
            ref.setPort(target.getInteger("port"));
            ref.setVendorHint(target.getString("vendorHint"));
        }
        return ref;
    }

    /**
     * 回退：旧扁平 JSON（envelope.enabled=false 时使用）
     * 必须完全去掉 schemaVersion、messageType、control 包裹层，让解密网关 SecureEnvelopeParser 进入旧 DTO 回退路径。
     * 根节点直接放：taskId、commandTaskId、requestId、command、action、target、params
     */
    private JSONObject buildLegacyControlPayload(JSONObject plainTask) {
        JSONObject payload = new JSONObject();
        String commandTaskId = plainTask.getString("commandTaskId");
        payload.put("taskId", commandTaskId);
        payload.put("commandTaskId", commandTaskId);
        payload.put("requestId", commandTaskId);
        payload.put("action", "CONTROL_SCREEN");
        payload.put("command", plainTask.getString("command"));

        JSONObject sourceRef = plainTask.getJSONObject("source");
        if (sourceRef == null) {
            sourceRef = new JSONObject();
        }
        sourceRef.put("gatewayId", "publish-gateway");
        payload.put("source", sourceRef);

        payload.put("target", plainTask.getJSONObject("target"));
        payload.put("params", plainTask.getJSONObject("params"));

        return payload;
    }

    // ═══════════════════════════ 状态刷新 ═══════════════════════════

    private void refreshTerminalStatus(ControlTaskStatus status) {
        try {
            ResponseEntity<String> response = secureTerminalClient.getControlStatus(
                    terminalGatewayUrl, status.getBatchTaskId());

            if (!response.getStatusCode().is2xxSuccessful()) {
                return;
            }
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.trim().isEmpty()) {
                return;
            }

            JSONObject json = JSON.parseObject(responseBody);
            if (json == null || json.getInteger("code") == null || json.getInteger("code") != 200) {
                return;
            }
            JSONObject data = json.getJSONObject("data");
            if (data == null || !Boolean.TRUE.equals(data.getBoolean("found"))) {
                return;
            }
            status.setTerminalTask(data);
            if (data.containsKey("results")) {
                status.setTerminalResults(data.get("results"));
            }
            String terminalStatus = data.getString("status");
            status.setTerminalStatus(terminalStatus);
            if ("SUCCESS".equals(terminalStatus)) {
                status.setStatus("SUCCESS");
                status.setMessage("控制指令执行成功");
            } else if ("FAILED".equals(terminalStatus) || "PARTIAL_SUCCESS".equals(terminalStatus)) {
                status.setStatus("FAILED");
                status.setMessage(data.getString("message"));
            } else if ("TIMEOUT".equals(terminalStatus)) {
                status.setStatus("TIMEOUT");
                status.setMessage("控制指令执行超时");
            }
            status.setUpdatedAt(System.currentTimeMillis());
        } catch (Exception e) {
            log.debug("[控制投递] 刷新解密网关状态失败: batchTaskId={}, error={}",
                    status.getBatchTaskId(), e.getMessage());
        }
    }

    // ═══════════════════════════ 工具方法 ═══════════════════════════

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

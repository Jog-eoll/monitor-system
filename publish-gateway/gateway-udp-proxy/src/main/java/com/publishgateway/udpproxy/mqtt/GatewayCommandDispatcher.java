package com.publishgateway.udpproxy.mqtt;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import com.monitorplatform.mqtt.core.dto.MqttEnvelope;
import com.monitorplatform.mqtt.core.dto.MqttReplyMessage;
import com.publishgateway.udpproxy.entity.dto.delivery.DeliveryTaskStatus;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskRequest;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskResponse;
import com.publishgateway.udpproxy.service.ControlDeliveryService;
import com.publishgateway.udpproxy.service.SecureDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关命令分发器 —— 处理 MQTT 下行 PROXY_COMMAND，按 actions 列表逐条执行，
 * 并上行 PROCESSING / SUCCESS / FAILED 回执。
 * <p>
 * v1 执行策略：
 * <ul>
 *   <li>SELF_APPLY：通过本地 HTTP 调用自身 /udp-proxy/config 等接口（复用现有 Controller 逻辑）</li>
 *   <li>HTTP：转发到局域网内目标设备的 REST 接口（如其它网关的 /udp-proxy/config）</li>
 * </ul>
 * 目标 IP/Port 在 v1 中从 action.body 字段解析，未提供时回退到本机。
 * </p>
 */
@Slf4j
@Component
public class GatewayCommandDispatcher {

    /** 本机回环地址，SELF_APPLY / HTTP 兜底使用 */
    private static final String LOCAL_HOST = "127.0.0.1";

    /** SELF_APPLY / HTTP 默认转发路径 */
    private static final String DEFAULT_PROXY_CONFIG_PATH = "/udp-proxy/config";
    private static final String COMMAND_QUERY_STATUS = "QUERY_STATUS";
    private static final String COMMAND_NOOP = "NOOP";
    private static final String COMMAND_ECHO = "ECHO";
    private static final String COMMAND_SECURE_DELIVERY = "SECURE_DELIVERY";
    private static final String COMMAND_CONTROL_DELIVERY = "CONTROL_DELIVERY";

    /** SECURE_DELIVERY 监控线程轮询间隔（毫秒） */
    private static final long SECURE_DELIVERY_POLL_INTERVAL_MS = 2_000L;
    /** SECURE_DELIVERY 监控线程超时时间（毫秒），2 分钟内未达终态视为超时 */
    private static final long SECURE_DELIVERY_MONITOR_TIMEOUT_MS = 120_000L;

    @Resource
    private MqttCommandRecordService commandRecordService;

    @Resource
    private LocalHttpForwardService localHttpForwardService;

    @Resource
    private SystemNetworkChangeService systemNetworkChangeService;

    @Resource
    private RemoteUpgradeService remoteUpgradeService;

    /**
     * 安全投递服务，使用 @Lazy 防御性注入避免潜在的循环依赖
     * （SecureDeliveryService 依赖链涉及 CryptoService 等，若后续出现反向引用可平滑处理）。
     */
    @Resource
    @Lazy
    private SecureDeliveryService secureDeliveryService;

    @Resource
    @Lazy
    private ControlDeliveryService controlDeliveryService;

    @Resource
    private ReplyPublisher replyPublisher;

    @Resource
    private MqttAgentProperties properties;

    /** 本机 HTTP 端口（与 server.port 一致），用于 SELF_APPLY 回环调用 */
    @Value("${server.port:8092}")
    private int localHttpPort;

    @Value("${control-delivery.mqtt-final-reply.poll-interval-ms:1000}")
    private long controlDeliveryPollIntervalMs;

    @Value("${control-delivery.mqtt-final-reply.timeout-ms:60000}")
    private long controlDeliveryMonitorTimeoutMs;

    /**
     * 处理下行命令信封。
     *
     * @param envelope 下行命令信封（payload 为 MqttCommandMessage 的 JSON）
     */
    public void onCommand(MqttEnvelope envelope) {
        if (envelope == null) {
            log.warn("[CMD] 收到空信封，忽略");
            return;
        }
        String commandMessageId = envelope.getMessageId();
        if (commandMessageId == null || commandMessageId.trim().isEmpty()) {
            commandMessageId = "unknown-" + System.nanoTime();
        }

        // 1. 幂等校验：已处理则直接跳过
        if (commandRecordService.isProcessed(commandMessageId)) {
            log.info("[CMD] 命令已处理过，跳过: messageId={}", commandMessageId);
            return;
        }
        // 2. 标记已处理（防御并发重复投递）
        if (!commandRecordService.markProcessed(commandMessageId)) {
            log.info("[CMD] 命令已被并发标记，跳过: messageId={}", commandMessageId);
            return;
        }

        // 3. 解析命令体
        MqttCommandMessage command;
        try {
            command = JSON.parseObject(envelope.getPayload(), MqttCommandMessage.class);
        } catch (Exception e) {
            log.error("[CMD] 命令体解析失败: messageId={}, {}", commandMessageId, e.getMessage(), e);
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(), "命令体解析失败: " + e.getMessage()));
            return;
        }
        if (command == null) {
            log.warn("[CMD] 命令体为空: messageId={}", commandMessageId);
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(), "命令体为空"));
            return;
        }

        log.info("[CMD] 收到下行命令: messageId={}, command={}, actions={}",
                commandMessageId, command.getCommand(),
                command.getActions() == null ? 0 : command.getActions().size());

        // 4. 立即上行 PROCESSING 回执
        replyPublisher.publishReply(MqttReplyMessage.processing(
                commandMessageId, properties.resolveDeviceId()));

        if (isNativeProbeCommand(command.getCommand())) {
            executeNativeProbeCommand(commandMessageId, command);
            return;
        }
        if ("CHANGE_SYSTEM_IP".equalsIgnoreCase(command.getCommand())) {
            executeNativeSystemCommand(commandMessageId, command, "CHANGE_SYSTEM_IP");
            return;
        }
        if ("CONFIRM_SYSTEM_IP".equalsIgnoreCase(command.getCommand())) {
            executeNativeSystemCommand(commandMessageId, command, "CONFIRM_SYSTEM_IP");
            return;
        }
        if ("REMOTE_UPGRADE".equalsIgnoreCase(command.getCommand())) {
            executeNativeSystemCommand(commandMessageId, command, "REMOTE_UPGRADE");
            return;
        }
        if (COMMAND_SECURE_DELIVERY.equalsIgnoreCase(command.getCommand())) {
            executeSecureDeliveryCommand(commandMessageId, command);
            return;
        }
        if (COMMAND_CONTROL_DELIVERY.equalsIgnoreCase(command.getCommand())) {
            executeControlDeliveryCommand(commandMessageId, command);
            return;
        }

        // 5. 逐条执行 actions
        List<Map<String, Object>> actionResults = new ArrayList<>();
        boolean allSuccess = true;
        String firstError = null;
        try {
            List<MqttCommandMessage.Action> actions = command.getActions();
            if (actions == null || actions.isEmpty()) {
                log.info("[CMD] 命令无 actions，按 payload 做默认 SELF_APPLY: messageId={}",
                        commandMessageId);
                actions = new ArrayList<>();
                MqttCommandMessage.Action selfAction = new MqttCommandMessage.Action();
                selfAction.setMode("SELF_APPLY");
                selfAction.setPath(DEFAULT_PROXY_CONFIG_PATH);
                selfAction.setHttpMethod("POST");
                selfAction.setBody(command.getPayload());
                actions.add(selfAction);
            }

            for (int i = 0; i < actions.size(); i++) {
                MqttCommandMessage.Action action = actions.get(i);
                Map<String, Object> result = executeAction(action, command, commandMessageId, i);
                actionResults.add(result);
                boolean ok = isActionSuccess(result);
                if (!ok && allSuccess) {
                    allSuccess = false;
                    firstError = resolveActionFailureMessage(result);
                }
            }

            // 6. 汇总上行最终回执
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("actions", actionResults);
            data.put("command", command.getCommand());
            if (allSuccess) {
                replyPublisher.publishReply(MqttReplyMessage.success(
                        commandMessageId, properties.resolveDeviceId(), data));
                log.info("[CMD] 命令执行完成(成功): messageId={}, actions={}",
                        commandMessageId, actionResults.size());
            } else {
                replyPublisher.publishReply(MqttReplyMessage.failed(
                        commandMessageId, properties.resolveDeviceId(), firstError));
                log.warn("[CMD] 命令执行完成(部分失败): messageId={}, firstError={}",
                        commandMessageId, firstError);
            }
        } catch (Exception e) {
            // 7. 异常兜底：上行 FAILED
            log.error("[CMD] 命令执行异常: messageId={}, {}", commandMessageId, e.getMessage(), e);
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "命令执行异常: " + e.getMessage()));
        }
    }

    /**
     * 执行单个 action。
     */
    private boolean isNativeProbeCommand(String commandName) {
        return COMMAND_QUERY_STATUS.equalsIgnoreCase(commandName)
                || COMMAND_NOOP.equalsIgnoreCase(commandName)
                || COMMAND_ECHO.equalsIgnoreCase(commandName);
    }

    private void executeNativeProbeCommand(String commandMessageId, MqttCommandMessage command) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("command", command.getCommand());
        data.put("status", "UP");
        data.put("deviceId", properties.resolveDeviceId());
        data.put("clientId", properties.getClientId());
        data.put("deviceType", properties.getDeviceType());
        data.put("serviceName", properties.getServiceName());
        data.put("localHttpPort", localHttpPort);
        data.put("version", properties.getVersion());
        data.put("mqttAgentEnabled", properties.isEnabled());
        data.put("timestamp", System.currentTimeMillis());
        if (COMMAND_ECHO.equalsIgnoreCase(command.getCommand())) {
            data.put("echo", command.getPayload());
        }
        replyPublisher.publishReply(MqttReplyMessage.success(
                commandMessageId, properties.resolveDeviceId(), data));
        log.info("[CMD] native probe command success: messageId={}, command={}",
                commandMessageId, command.getCommand());
    }

    private void executeNativeSystemCommand(String commandMessageId,
                                            MqttCommandMessage command,
                                            String commandName) {
        try {
            Map<String, Object> result;
            if ("CHANGE_SYSTEM_IP".equalsIgnoreCase(commandName)) {
                result = systemNetworkChangeService.changeIp(command.getPayload(), commandMessageId);
            } else if ("CONFIRM_SYSTEM_IP".equalsIgnoreCase(commandName)) {
                result = systemNetworkChangeService.confirm(command.getPayload());
            } else if ("REMOTE_UPGRADE".equalsIgnoreCase(commandName)) {
                result = remoteUpgradeService.execute(command.getPayload());
            } else {
                throw new IllegalArgumentException("unsupported native command: " + commandName);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("command", commandName);
            data.put("result", result);
            replyPublisher.publishReply(MqttReplyMessage.success(
                    commandMessageId, properties.resolveDeviceId(), data));
            log.info("[CMD] native command success: messageId={}, command={}", commandMessageId, commandName);
        } catch (Exception e) {
            log.error("[CMD] native command failed: messageId={}, command={}, {}",
                    commandMessageId, commandName, e.getMessage(), e);
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "native command failed: " + e.getMessage()));
        }
    }

    /**
     * 执行 SECURE_DELIVERY 命令：将 payload 转为 SecureDeliveryTaskRequest 调用 createTask，
     * 按 ACCEPTED / FAILED 分别回执 PROCESSING / FAILED；ACCEPTED 后启动监控线程轮询
     * 任务状态，到达终态或超时再上报最终的 SUCCESS / FAILED。
     * <p>
     * createTask 内部异步执行文件下载、SHA256 校验、加密、HTTP 投递；
     * 同步返回的 FAILED 通常为前置校验失败（如 publishPermit 验签失败、任务包解密失败），
     * 异步阶段的失败（如 MinIO 下载失败、SHA256 校验失败、UKey/证书不可用）会写入任务状态，
     * 通过 deliveryTaskId 后续查询 DeliveryTaskStatus 获取。
     * </p>
     */
    private void executeSecureDeliveryCommand(String commandMessageId, MqttCommandMessage command) {
        try {
            Map<String, Object> payload = command.getPayload();
            if (payload == null || payload.isEmpty()) {
                log.warn("[CMD] SECURE_DELIVERY 缺少 payload: messageId={}", commandMessageId);
                replyPublisher.publishReply(MqttReplyMessage.failed(
                        commandMessageId, properties.resolveDeviceId(),
                        "SECURE_DELIVERY 缺少 payload"));
                return;
            }
            SecureDeliveryTaskRequest request = JSON.parseObject(
                    JSON.toJSONString(payload), SecureDeliveryTaskRequest.class);
            if (request == null) {
                log.warn("[CMD] SECURE_DELIVERY payload 解析为空: messageId={}", commandMessageId);
                replyPublisher.publishReply(MqttReplyMessage.failed(
                        commandMessageId, properties.resolveDeviceId(),
                        "SECURE_DELIVERY payload 解析为空"));
                return;
            }

            SecureDeliveryTaskResponse response = secureDeliveryService.createTask(request);
            if (response == null) {
                log.warn("[CMD] SECURE_DELIVERY 服务返回空: messageId={}", commandMessageId);
                replyPublisher.publishReply(MqttReplyMessage.failed(
                        commandMessageId, properties.resolveDeviceId(),
                        "SECURE_DELIVERY 服务返回空"));
                return;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("command", COMMAND_SECURE_DELIVERY);
            data.put("deliveryTaskId", response.getDeliveryTaskId());
            data.put("status", response.getStatus());
            data.put("message", response.getMessage());
            data.put("createdAt", response.getCreatedAt());

            if ("ACCEPTED".equalsIgnoreCase(response.getStatus())) {
                // ACCEPTED 仅代表任务已被受理：后台线程异步执行文件下载、SHA256 校验、
                // 加密和投递。此处改为上报 PROCESSING（携带 deliveryTaskId 等元数据），
                // 最终结果由独立监控线程轮询 getTaskStatus 后上报，避免过早上报 SUCCESS。
                MqttReplyMessage processing = MqttReplyMessage.processing(
                        commandMessageId, properties.resolveDeviceId());
                processing.setData(data);
                replyPublisher.publishReply(processing);
                log.info("[CMD] SECURE_DELIVERY accepted(processing): messageId={}, deliveryTaskId={}",
                        commandMessageId, response.getDeliveryTaskId());

                startSecureDeliveryMonitor(commandMessageId, response.getDeliveryTaskId());
            } else {
                String errorMsg = response.getMessage() != null
                        ? response.getMessage() : "创建投递任务失败";
                replyPublisher.publishReply(MqttReplyMessage.failed(
                        commandMessageId, properties.resolveDeviceId(),
                        "SECURE_DELIVERY failed: " + errorMsg));
                log.warn("[CMD] SECURE_DELIVERY failed: messageId={}, message={}",
                        commandMessageId, response.getMessage());
            }
        } catch (Exception e) {
            log.error("[CMD] SECURE_DELIVERY 异常: messageId={}, {}",
                    commandMessageId, e.getMessage(), e);
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "SECURE_DELIVERY 异常: " + e.getMessage()));
        }
    }

    private void executeControlDeliveryCommand(String commandMessageId, MqttCommandMessage command) {
        try {
            List<MqttCommandMessage.Action> actions = command.getActions();
            if (actions == null || actions.isEmpty()) {
                replyPublisher.publishReply(MqttReplyMessage.failed(
                        commandMessageId, properties.resolveDeviceId(),
                        "CONTROL_DELIVERY missing action"));
                return;
            }

            List<Map<String, Object>> actionResults = new ArrayList<>();
            for (int i = 0; i < actions.size(); i++) {
                Map<String, Object> result = executeAction(actions.get(i), command, commandMessageId, i);
                actionResults.add(result);
                if (!isActionSuccess(result)) {
                    replyPublisher.publishReply(MqttReplyMessage.failed(
                            commandMessageId, properties.resolveDeviceId(),
                            resolveActionFailureMessage(result)));
                    return;
                }
            }

            Map<String, Object> acceptedResult = findControlDeliveryResult(actionResults);
            if (acceptedResult == null) {
                replyPublisher.publishReply(MqttReplyMessage.failed(
                        commandMessageId, properties.resolveDeviceId(),
                        "CONTROL_DELIVERY missing task response"));
                return;
            }

            String deliveryTaskId = extractControlDeliveryTaskId(acceptedResult);
            String createStatus = extractControlDeliveryStatus(acceptedResult);
            if (isTerminalDeliveryStatus(createStatus)) {
                reportControlDeliveryFinalStatus(commandMessageId, deliveryTaskId, acceptedResult);
                return;
            }
            if (createStatus != null && !"ACCEPTED".equalsIgnoreCase(createStatus)) {
                replyPublisher.publishReply(MqttReplyMessage.failed(
                        commandMessageId, properties.resolveDeviceId(),
                        resolveControlDeliveryFailureMessage(acceptedResult, createStatus)));
                return;
            }
            if (deliveryTaskId == null || deliveryTaskId.trim().isEmpty()) {
                replyPublisher.publishReply(MqttReplyMessage.failed(
                        commandMessageId, properties.resolveDeviceId(),
                        "CONTROL_DELIVERY missing deliveryTaskId"));
                return;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("command", COMMAND_CONTROL_DELIVERY);
            data.put("deliveryTaskId", deliveryTaskId);
            data.put("status", createStatus == null ? "ACCEPTED" : createStatus);
            data.put("actions", actionResults);
            MqttReplyMessage processing = MqttReplyMessage.processing(
                    commandMessageId, properties.resolveDeviceId());
            processing.setData(data);
            replyPublisher.publishReply(processing);
            log.info("[CMD] CONTROL_DELIVERY accepted: messageId={}, deliveryTaskId={}",
                    commandMessageId, deliveryTaskId);

            startControlDeliveryMonitor(commandMessageId, deliveryTaskId);
        } catch (Exception e) {
            log.error("[CMD] CONTROL_DELIVERY exception: messageId={}, {}",
                    commandMessageId, e.getMessage(), e);
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "CONTROL_DELIVERY exception: " + e.getMessage()));
        }
    }

    private void startControlDeliveryMonitor(final String commandMessageId, final String deliveryTaskId) {
        Thread monitor = new Thread(new Runnable() {
            @Override
            public void run() {
                monitorControlDeliveryTask(commandMessageId, deliveryTaskId);
            }
        }, "control-delivery-monitor-" + deliveryTaskId);
        monitor.setDaemon(true);
        monitor.start();
        log.info("[CMD] CONTROL_DELIVERY monitor started: messageId={}, deliveryTaskId={}",
                commandMessageId, deliveryTaskId);
    }

    private void monitorControlDeliveryTask(String commandMessageId, String deliveryTaskId) {
        long timeoutMs = controlDeliveryMonitorTimeoutMs <= 0 ? 60000L : controlDeliveryMonitorTimeoutMs;
        long pollIntervalMs = controlDeliveryPollIntervalMs <= 0 ? 1000L : controlDeliveryPollIntervalMs;
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            while (System.currentTimeMillis() < deadline) {
                Map<String, Object> taskStatus = controlDeliveryService.getControlTaskStatus(deliveryTaskId);
                if (taskStatus == null) {
                    replyPublisher.publishReply(MqttReplyMessage.failed(
                            commandMessageId, properties.resolveDeviceId(),
                            "CONTROL_DELIVERY monitor failed: empty task status"));
                    return;
                }
                if (Boolean.FALSE.equals(taskStatus.get("found"))) {
                    replyPublisher.publishReply(MqttReplyMessage.failed(
                            commandMessageId, properties.resolveDeviceId(),
                            "CONTROL_DELIVERY task not found: " + deliveryTaskId));
                    return;
                }
                String status = resolveString(taskStatus, "status");
                String terminalStatus = resolveString(taskStatus, "terminalStatus");
                String effectiveStatus = isTerminalDeliveryStatus(status) ? status : terminalStatus;
                log.info("[CONTROL_DELIVERY-MONITOR] status: messageId={}, deliveryTaskId={}, status={}, terminalStatus={}",
                        commandMessageId, deliveryTaskId, status, terminalStatus);
                if (isTerminalDeliveryStatus(effectiveStatus)) {
                    reportControlDeliveryFinalStatus(commandMessageId, deliveryTaskId, taskStatus);
                    return;
                }
                Thread.sleep(pollIntervalMs);
            }
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "CONTROL_DELIVERY monitor timeout"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "CONTROL_DELIVERY monitor interrupted"));
        } catch (Exception e) {
            log.error("[CONTROL_DELIVERY-MONITOR] exception: messageId={}, deliveryTaskId={}, {}",
                    commandMessageId, deliveryTaskId, e.getMessage(), e);
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "CONTROL_DELIVERY monitor exception: " + e.getMessage()));
        }
    }

    private void reportControlDeliveryFinalStatus(String commandMessageId,
                                                   String deliveryTaskId,
                                                   Map<String, Object> taskStatus) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (taskStatus != null) {
            data.putAll(taskStatus);
        }
        data.put("command", COMMAND_CONTROL_DELIVERY);
        if (deliveryTaskId != null && !deliveryTaskId.trim().isEmpty()) {
            data.put("deliveryTaskId", deliveryTaskId);
        }
        String status = resolveString(data, "status");
        String terminalStatus = resolveString(data, "terminalStatus");
        if ("SUCCESS".equalsIgnoreCase(status)
                || (!isTerminalDeliveryStatus(status) && "SUCCESS".equalsIgnoreCase(terminalStatus))) {
            replyPublisher.publishReply(MqttReplyMessage.success(
                    commandMessageId, properties.resolveDeviceId(), data));
            log.info("[CONTROL_DELIVERY-MONITOR] final success: messageId={}, deliveryTaskId={}",
                    commandMessageId, deliveryTaskId);
            return;
        }

        String errorMsg = resolveString(data, "message", "msg", "errorMessage", "error", "reason");
        if (errorMsg == null) {
            errorMsg = "CONTROL_DELIVERY failed: status=" + status + ", terminalStatus=" + terminalStatus;
        }
        MqttReplyMessage failed = MqttReplyMessage.failed(
                commandMessageId, properties.resolveDeviceId(), errorMsg);
        failed.setData(data);
        replyPublisher.publishReply(failed);
        log.warn("[CONTROL_DELIVERY-MONITOR] final failed: messageId={}, deliveryTaskId={}, status={}, terminalStatus={}, message={}",
                commandMessageId, deliveryTaskId, status, terminalStatus, errorMsg);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findControlDeliveryResult(List<Map<String, Object>> actionResults) {
        if (actionResults == null) {
            return null;
        }
        for (Map<String, Object> result : actionResults) {
            if (result == null) {
                continue;
            }
            String taskId = extractControlDeliveryTaskId(result);
            String status = extractControlDeliveryStatus(result);
            if (taskId != null || status != null) {
                return result;
            }
            Object data = result.get("data");
            if (data instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) data;
                taskId = extractControlDeliveryTaskId(dataMap);
                status = extractControlDeliveryStatus(dataMap);
                if (taskId != null || status != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractControlDeliveryTaskId(Map<String, Object> result) {
        if (result == null) {
            return null;
        }
        String direct = resolveString(result, "deliveryTaskId", "commandTaskId");
        if (direct != null) {
            return direct;
        }
        Object data = result.get("data");
        if (data instanceof Map) {
            return resolveString((Map<String, Object>) data, "deliveryTaskId", "commandTaskId");
        }
        if (data != null) {
            try {
                Map<String, Object> parsed = JSON.parseObject(JSON.toJSONString(data), Map.class);
                return resolveString(parsed, "deliveryTaskId", "commandTaskId");
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractControlDeliveryStatus(Map<String, Object> result) {
        if (result == null) {
            return null;
        }
        String direct = resolveString(result, "status", "terminalStatus");
        if (direct != null) {
            return direct;
        }
        Object data = result.get("data");
        if (data instanceof Map) {
            return resolveString((Map<String, Object>) data, "status", "terminalStatus");
        }
        if (data != null) {
            try {
                Map<String, Object> parsed = JSON.parseObject(JSON.toJSONString(data), Map.class);
                return resolveString(parsed, "status", "terminalStatus");
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String resolveControlDeliveryFailureMessage(Map<String, Object> result, String status) {
        if (result == null) {
            return "CONTROL_DELIVERY failed: status=" + status;
        }
        String message = resolveString(result, "message", "msg", "errorMessage", "error", "reason");
        if (message != null) {
            return message;
        }
        Object data = result.get("data");
        if (data instanceof Map) {
            message = resolveString((Map<String, Object>) data, "message", "msg", "errorMessage", "error", "reason");
            if (message != null) {
                return message;
            }
        }
        return "CONTROL_DELIVERY failed: status=" + status;
    }

    /**
     * 启动 SECURE_DELIVERY 任务状态监控线程（daemon）。
     * <p>
     * 轮询 {@link SecureDeliveryService#getTaskStatus(String)} 直到任务进入终态
     * （SUCCESS / FAILED / TIMEOUT / CANCELED）或达到超时时间，然后上报对应的 MQTT 最终回执。
     * 监控线程不阻塞 onCommand 回调线程。
     * </p>
     *
     * @param commandMessageId 原始命令 messageId（用于关联 MQTT 回执）
     * @param deliveryTaskId   投递任务 ID
     */
    private void startSecureDeliveryMonitor(final String commandMessageId, final String deliveryTaskId) {
        Thread monitor = new Thread(new Runnable() {
            @Override
            public void run() {
                monitorSecureDeliveryTask(commandMessageId, deliveryTaskId);
            }
        }, "secure-delivery-monitor-" + deliveryTaskId);
        monitor.setDaemon(true);
        monitor.start();
        log.info("[CMD] SECURE_DELIVERY 监控线程已启动: messageId={}, deliveryTaskId={}",
                commandMessageId, deliveryTaskId);
    }

    /**
     * 轮询投递任务状态直到终态或超时，上报 MQTT 最终回执。
     */
    private void monitorSecureDeliveryTask(String commandMessageId, String deliveryTaskId) {
        long deadline = System.currentTimeMillis() + SECURE_DELIVERY_MONITOR_TIMEOUT_MS;
        try {
            while (System.currentTimeMillis() < deadline) {
                DeliveryTaskStatus taskStatus = secureDeliveryService.getTaskStatus(deliveryTaskId);
                if (taskStatus == null) {
                    log.warn("[SECURE_DELIVERY-MONITOR] getTaskStatus 返回空: messageId={}, deliveryTaskId={}",
                            commandMessageId, deliveryTaskId);
                    replyPublisher.publishReply(MqttReplyMessage.failed(
                            commandMessageId, properties.resolveDeviceId(),
                            "SECURE_DELIVERY 监控异常: getTaskStatus 返回空"));
                    return;
                }
                String status = taskStatus.getStatus();
                log.info("[SECURE_DELIVERY-MONITOR] 轮询任务状态: messageId={}, deliveryTaskId={}, status={}",
                        commandMessageId, deliveryTaskId, status);
                if (isTerminalDeliveryStatus(status)) {
                    reportDeliveryFinalStatus(commandMessageId, deliveryTaskId, taskStatus);
                    return;
                }
                Thread.sleep(SECURE_DELIVERY_POLL_INTERVAL_MS);
            }
            // 超时未达终态
            log.warn("[SECURE_DELIVERY-MONITOR] 监控超时: messageId={}, deliveryTaskId={}",
                    commandMessageId, deliveryTaskId);
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "SECURE_DELIVERY 监控超时"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[SECURE_DELIVERY-MONITOR] 监控线程被中断: messageId={}, deliveryTaskId={}",
                    commandMessageId, deliveryTaskId);
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "SECURE_DELIVERY 监控被中断"));
        } catch (Exception e) {
            log.error("[SECURE_DELIVERY-MONITOR] 监控异常: messageId={}, deliveryTaskId={}, {}",
                    commandMessageId, deliveryTaskId, e.getMessage(), e);
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "SECURE_DELIVERY 监控异常: " + e.getMessage()));
        }
    }

    /**
     * 根据任务终态上报对应的 MQTT 回执。
     * SUCCESS → SUCCESS + data{deliveryTaskId, status}；其余终态 → FAILED + 错误信息。
     */
    private void reportDeliveryFinalStatus(String commandMessageId, String deliveryTaskId,
                                            DeliveryTaskStatus taskStatus) {
        String status = taskStatus.getStatus();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deliveryTaskId", deliveryTaskId);
        data.put("status", status);
        if ("SUCCESS".equalsIgnoreCase(status)) {
            replyPublisher.publishReply(MqttReplyMessage.success(
                    commandMessageId, properties.resolveDeviceId(), data));
            log.info("[SECURE_DELIVERY-MONITOR] 任务终态成功: messageId={}, deliveryTaskId={}",
                    commandMessageId, deliveryTaskId);
        } else {
            String errorMsg = taskStatus.getMessage() != null
                    ? taskStatus.getMessage() : "SECURE_DELIVERY 任务失败: " + status;
            replyPublisher.publishReply(MqttReplyMessage.failed(
                    commandMessageId, properties.resolveDeviceId(),
                    "SECURE_DELIVERY failed: " + errorMsg));
            log.warn("[SECURE_DELIVERY-MONITOR] 任务终态失败: messageId={}, deliveryTaskId={}, status={}, message={}",
                    commandMessageId, deliveryTaskId, status, taskStatus.getMessage());
        }
    }

    /**
     * 判断是否为 SECURE_DELIVERY 任务的终态。
     * 终态：SUCCESS / FAILED / TIMEOUT / CANCELED。
     */
    private boolean isTerminalDeliveryStatus(String status) {
        if (status == null) {
            return false;
        }
        return "SUCCESS".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "TIMEOUT".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status);
    }

    private Map<String, Object> executeAction(MqttCommandMessage.Action action,
                                               MqttCommandMessage command,
                                               String commandMessageId, int index) {
        String mode = action.getMode();
        // 优先取 action.body，其次回退到命令级 payload
        Map<String, Object> body = action.getBody() != null ? action.getBody() : command.getPayload();
        if (body == null) {
            body = new LinkedHashMap<>();
        }
        String path = (action.getPath() != null && !action.getPath().trim().isEmpty())
                ? action.getPath() : DEFAULT_PROXY_CONFIG_PATH;
        String httpMethod = (action.getHttpMethod() != null && !action.getHttpMethod().trim().isEmpty())
                ? action.getHttpMethod() : "POST";

        log.info("[CMD] 执行动作[{}]: messageId={}, mode={}, path={}, targetDeviceType={}, targetDeviceId={}",
                index, commandMessageId, mode, path, action.getTargetDeviceType(), action.getTargetDeviceId());

        try {
            if ("SELF_APPLY".equalsIgnoreCase(mode)) {
                // v1：SELF_APPLY 走本机 HTTP 回环，复用现有 UdpProxyConfigController 逻辑
                log.info("[CMD] self apply -> 调用本机 {}:{}{}（v1 通过 HTTP 回环触发已有 Controller）",
                        LOCAL_HOST, localHttpPort, path);
                return localHttpForwardService.forward(
                        LOCAL_HOST, localHttpPort, path, httpMethod, body);
            }

            if ("HTTP".equalsIgnoreCase(mode)) {
                // HTTP actions must carry an explicit target. SELF_APPLY is the only local fallback path.
                String targetIp = action.getTargetIp();
                if (targetIp == null || targetIp.trim().isEmpty()) {
                    targetIp = resolveString(body, "targetIp", "ip", "host");
                }
                int targetPort = action.getTargetPort() == null ? -1 : action.getTargetPort();
                if (targetPort <= 0) {
                    targetPort = resolveInt(body, "targetPort", "port");
                }
                if (targetIp == null || targetIp.isEmpty()) {
                    Map<String, Object> missingTarget = new LinkedHashMap<>();
                    missingTarget.put("success", false);
                    missingTarget.put("message", "HTTP 动作缺少目标 IP");
                    return missingTarget;
                }
                if (targetPort <= 0) {
                    Map<String, Object> missingTarget = new LinkedHashMap<>();
                    missingTarget.put("success", false);
                    missingTarget.put("message", "HTTP 动作缺少目标端口");
                    return missingTarget;
                }
                log.info("[CMD] http forward -> http://{}:{}{}", targetIp, targetPort, path);
                return localHttpForwardService.forward(targetIp, targetPort, path, httpMethod, body);
            }

            // 未知模式
            Map<String, Object> unknown = new LinkedHashMap<>();
            unknown.put("success", false);
            unknown.put("message", "未知执行模式: " + mode);
            return unknown;
        } catch (Exception e) {
            log.error("[CMD] 动作[{}]执行异常: mode={}, {}", index, mode, e.getMessage(), e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "动作执行异常: " + e.getMessage());
            return error;
        }
    }

    private boolean isActionSuccess(Map<String, Object> result) {
        if (result == null) {
            return false;
        }
        Object success = result.get("success");
        if (Boolean.FALSE.equals(success)) {
            return false;
        }
        if (success instanceof String && "false".equalsIgnoreCase(((String) success).trim())) {
            return false;
        }

        int httpStatus = toInt(result.get("httpStatus"), -1);
        if (httpStatus > 0 && (httpStatus < 200 || httpStatus >= 300)) {
            return false;
        }

        if (result.containsKey("code")) {
            return toInt(result.get("code"), Integer.MIN_VALUE) == 200;
        }

        // 缺少 success/code/httpStatus 时默认判定为失败，仅显式 success=true 才算成功
        return Boolean.TRUE.equals(success)
                || (success instanceof String && "true".equalsIgnoreCase(((String) success).trim()));
    }

    private String resolveActionFailureMessage(Map<String, Object> result) {
        if (result == null) {
            return "动作执行失败";
        }
        String message = resolveString(result, "message", "msg", "errorMessage", "error", "reason");
        if (message != null) {
            return message;
        }
        Object code = result.get("code");
        if (code != null) {
            return "动作执行失败: code=" + code;
        }
        Object httpStatus = result.get("httpStatus");
        if (httpStatus != null) {
            return "动作执行失败: httpStatus=" + httpStatus;
        }
        return "动作执行失败";
    }

    /**
     * 从 body Map 中按候选键顺序取首个非空字符串。
     */
    private String resolveString(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String key : keys) {
            Object val = body.get(key);
            if (val != null && !val.toString().trim().isEmpty()) {
                return val.toString().trim();
            }
        }
        return null;
    }

    /**
     * 从 body Map 中按候选键顺序取首个有效端口。
     */
    private int resolveInt(Map<String, Object> body, String... keys) {
        if (body == null) {
            return -1;
        }
        for (String key : keys) {
            Object val = body.get(key);
            int parsed = toInt(val, -1);
            if (parsed > 0) {
                return parsed;
            }
        }
        return -1;
    }

    /**
     * 将任意值转为 int，失败返回默认值。
     */
    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

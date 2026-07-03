package com.publishgateway.udpproxy.mqtt;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import com.monitorplatform.mqtt.core.dto.MqttEnvelope;
import com.monitorplatform.mqtt.core.dto.MqttReplyMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Resource
    private MqttCommandRecordService commandRecordService;

    @Resource
    private LocalHttpForwardService localHttpForwardService;

    @Resource
    private SystemNetworkChangeService systemNetworkChangeService;

    @Resource
    private RemoteUpgradeService remoteUpgradeService;

    @Resource
    private ReplyPublisher replyPublisher;

    @Resource
    private MqttAgentProperties properties;

    /** 本机 HTTP 端口（与 server.port 一致），用于 SELF_APPLY 回环调用 */
    @Value("${server.port:8092}")
    private int localHttpPort;

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
                boolean ok = Boolean.TRUE.equals(result.get("success"));
                if (!ok && allSuccess) {
                    allSuccess = false;
                    Object msg = result.get("message");
                    firstError = msg == null ? "动作执行失败" : msg.toString();
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

    private Map<String, Object> executeAction(MqttCommandMessage.Action action,
                                               MqttCommandMessage command,
                                               String commandMessageId, int index) {
        String mode = action.getMode();
        // 优先取 action.body，其次回退到命令级 payload
        Map<String, Object> body = action.getBody() != null ? action.getBody() : command.getPayload();
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

package com.infopublish.client.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.infopublish.client.config.AppConfig;
import com.infopublish.client.entity.dto.control.ControlCommandRequest;
import com.infopublish.client.entity.dto.control.ControlCommandResponse;
import com.infopublish.client.service.ClientAuthService;
import com.infopublish.client.service.ControlCommandService;
import com.infopublish.client.service.GatewayService;
import com.infopublish.client.service.ProcessBindService;
import com.infopublish.client.service.UkeyLifecycleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ControlCommandServiceImpl implements ControlCommandService {

    @Resource
    private UkeyLifecycleManager ukeyLifecycleManager;

    @Resource
    private ClientAuthService clientAuthService;

    @Resource
    private ProcessBindService processBindService;

    @Resource
    private AppConfig.ProcessBindProperties processBindProperties;

    @Resource
    private GatewayService gatewayService;

    @Value("${control-command.gateway-url:http://127.0.0.1:8092}")
    private String gatewayApiUrl;

    @Value("${control-command.http-timeout-ms:10000}")
    private int httpTimeoutMs;

    @Value("${control-command.result-wait-timeout-ms:30000}")
    private long resultWaitTimeoutMs;

    @Value("${control-command.result-poll-interval-ms:1000}")
    private long resultPollIntervalMs;

    private static final Set<String> COMMAND_WHITELIST = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "QUERY_STATUS", "BRIGHTNESS", "BLACKOUT", "REBOOT", "TIME_SYNC",
                    "NTP_SET", "DEVICE_IP_SET", "SCREEN_ATTRIBUTE_SET"
            ))
    );

    private static final Set<String> HIGH_RISK_COMMANDS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "DEVICE_IP_SET", "SCREEN_ATTRIBUTE_SET"
            ))
    );

    private final ConcurrentHashMap<String, Map<String, Object>> localTaskStore = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> requestTaskIndex = new ConcurrentHashMap<>();

    @Override
    public ControlCommandResponse execute(ControlCommandRequest request) {
        String requestId = request.getRequestId() != null ? request.getRequestId().trim() : null;
        String existingTaskId = requestId != null ? requestTaskIndex.get(requestId) : null;
        if (existingTaskId != null) {
            Map<String, Object> existing = localTaskStore.get(existingTaskId);
            if (existing != null) {
                log.info("[控制指令] 命中幂等请求: requestId={}, commandTaskId={}", requestId, existingTaskId);
                return waitForFinalResponse(existingTaskId);
            }
        }

        String commandTaskId = "CMD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        if (!ukeyLifecycleManager.isAuthenticated()) {
            log.warn("[控制指令] UKey 未认证: commandTaskId={}", commandTaskId);
            return ControlCommandResponse.rejected(commandTaskId, "UKEY_NOT_AUTHENTICATED", "UKey 未认证，无法执行控制指令");
        }
        if (!clientAuthService.isAuthenticated()) {
            log.warn("[控制指令] 客户端未认证: commandTaskId={}", commandTaskId);
            return ControlCommandResponse.rejected(commandTaskId, "CLIENT_NOT_READY", "客户端未通过管控平台认证");
        }
        if (processBindProperties.isEnabled() && processBindService.getAuthorizedPid() <= 0) {
            log.warn("[控制指令] 信发平台进程未绑定: commandTaskId={}", commandTaskId);
            return ControlCommandResponse.rejected(commandTaskId, "CLIENT_NOT_READY", "信发平台进程未绑定");
        }

        String targetError = validateTarget(request.getTarget());
        if (targetError != null) {
            log.warn("[控制指令] 目标设备校验失败: commandTaskId={}, error={}", commandTaskId, targetError);
            return ControlCommandResponse.rejected(commandTaskId, "INVALID_COMMAND_PARAM", targetError);
        }

        if (!isGatewayReachable()) {
            log.warn("[控制指令] 加密网关不可达: commandTaskId={}, gatewayUrl={}", commandTaskId, gatewayApiUrl);
            return ControlCommandResponse.rejected(commandTaskId, "GATEWAY_UNREACHABLE", "加密网关不可达");
        }

        String command = request.getCommand().trim().toUpperCase();
        if (!COMMAND_WHITELIST.contains(command)) {
            log.warn("[控制指令] 不支持的指令: command={}, commandTaskId={}", command, commandTaskId);
            return ControlCommandResponse.rejected(commandTaskId, "COMMAND_NOT_ALLOWED", "不支持的控制指令: " + command);
        }

        Map<String, Object> normalizedParams = normalizeParams(command, request.getParams());

        String validationError = validateParams(command, normalizedParams);
        if (validationError != null) {
            log.warn("[控制指令] 参数校验失败: command={}, error={}, commandTaskId={}",
                    command, validationError, commandTaskId);
            return ControlCommandResponse.rejected(commandTaskId, "INVALID_COMMAND_PARAM", validationError);
        }

        if (HIGH_RISK_COMMANDS.contains(command)) {
            log.warn("[控制指令] 高风险运维指令通过校验: commandTaskId={}, command={}, operatorId={}, targetIp={}, params={}",
                    commandTaskId, command, request.getOperatorId(), request.getTarget().getIp(), normalizedParams);
        }

        Map<String, Object> controlTask = buildControlTask(commandTaskId, request, command, normalizedParams);
        String plaintextJson = JSON.toJSONString(controlTask);
        log.info("[控制指令] 构建明文控制任务: commandTaskId={}, command={}, jsonLength={}",
                commandTaskId, command, plaintextJson.length());

        String encryptedPackageId;
        byte[] encryptedPackage;
        try {
            Map<String, Object> encryptResult = callEncryptEndpoint(commandTaskId, plaintextJson);
            encryptedPackageId = (String) encryptResult.get("encryptedCommandPackageId");
            String base64 = (String) encryptResult.get("encryptedCommandPackage");
            if (base64 == null || base64.isEmpty()) {
                return ControlCommandResponse.error(commandTaskId, "ENCRYPT_COMMAND_PACKAGE_FAILED", "加密网关返回空密文");
            }
            encryptedPackage = Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            log.error("[控制指令] 调用加密网关加密失败: commandTaskId={}, error={}", commandTaskId, e.getMessage(), e);
            return ControlCommandResponse.error(commandTaskId, "ENCRYPT_COMMAND_PACKAGE_FAILED", "加密网关加密失败: " + e.getMessage());
        }

        String deliveryTaskId;
        try {
            Map<String, Object> deliveryResult = callDeliveryEndpoint(commandTaskId, encryptedPackageId,
                    encryptedPackage, request, command);
            deliveryTaskId = (String) deliveryResult.get("deliveryTaskId");
            if (deliveryTaskId == null) {
                deliveryTaskId = encryptedPackageId;
            }
        } catch (Exception e) {
            log.error("[控制指令] 调用加密网关投递失败: commandTaskId={}, error={}", commandTaskId, e.getMessage(), e);
            return ControlCommandResponse.error(commandTaskId, "DELIVERY_FAILED", "加密网关投递失败: " + e.getMessage());
        }

        Map<String, Object> taskInfo = new HashMap<>();
        taskInfo.put("commandTaskId", commandTaskId);
        taskInfo.put("requestId", requestId);
        taskInfo.put("deliveryTaskId", deliveryTaskId);
        taskInfo.put("command", command);
        taskInfo.put("status", "ACCEPTED");
        taskInfo.put("createdAt", System.currentTimeMillis());
        taskInfo.put("operatorId", request.getOperatorId());
        taskInfo.put("target", buildTargetSnapshot(request.getTarget()));
        localTaskStore.put(commandTaskId, taskInfo);
        if (requestId != null && !requestId.isEmpty()) {
            requestTaskIndex.putIfAbsent(requestId, commandTaskId);
        }

        log.info("[控制指令] 控制指令已提交: commandTaskId={}, deliveryTaskId={}, command={}",
                commandTaskId, deliveryTaskId, command);
        return waitForFinalResponse(commandTaskId);
    }

    @Override
    public Map<String, Object> getTaskStatus(String commandTaskId) {
        Map<String, Object> localInfo = localTaskStore.get(commandTaskId);
        if (localInfo == null) {
            Map<String, Object> notFound = new HashMap<>();
            notFound.put("found", false);
            notFound.put("commandTaskId", commandTaskId);
            return notFound;
        }

        String deliveryTaskId = (String) localInfo.get("deliveryTaskId");
        Map<String, Object> gatewayStatus = null;
        if (deliveryTaskId != null) {
            try {
                gatewayStatus = queryGatewayTaskStatus(deliveryTaskId);
            } catch (Exception e) {
                log.debug("[控制指令] 查询加密网关状态失败: deliveryTaskId={}, error={}",
                        deliveryTaskId, e.getMessage());
            }
        }

        return buildQingsongResponse(localInfo, gatewayStatus);
    }

    private Map<String, Object> buildQingsongResponse(Map<String, Object> localInfo, Map<String, Object> gatewayStatus) {
        Map<String, Object> result = new LinkedHashMap<>();
        String commandTaskId = (String) localInfo.get("commandTaskId");
        String deliveryTaskId = (String) localInfo.get("deliveryTaskId");
        String command = (String) localInfo.get("command");
        String requestId = (String) localInfo.get("requestId");
        Map<String, Object> target = asMap(localInfo.get("target"));

        result.put("found", true);
        result.put("requestId", requestId);
        result.put("commandTaskId", commandTaskId);
        result.put("deliveryTaskId", deliveryTaskId);
        result.put("command", command);
        result.put("target", target);

        String overallStatus = "ACCEPTED";
        Map<String, Object> deviceResult = new LinkedHashMap<>();
        Map<String, Object> deviceInfo = null;

        if (gatewayStatus != null) {
            overallStatus = (String) gatewayStatus.getOrDefault("status", overallStatus);
            String terminalStatus = stringValue(gatewayStatus.get("terminalStatus"));
            if (terminalStatus != null && isFinalStatus(terminalStatus)) {
                overallStatus = terminalStatus;
            }
            String remoteMessage = stringValue(gatewayStatus.get("message"));
            if (remoteMessage != null) {
                result.put("message", remoteMessage);
            }

            Map<String, Object> terminalTask = gatewayStatus.get("terminalTask") instanceof Map
                    ? (Map<String, Object>) gatewayStatus.get("terminalTask") : null;
            List<?> terminalResults = gatewayStatus.get("terminalResults") instanceof List
                    ? (List<?>) gatewayStatus.get("terminalResults") : null;

            if (terminalTask != null) {
                if (terminalResults != null && !terminalResults.isEmpty()) {
                    Object firstResult = terminalResults.get(0);
                    if (firstResult instanceof Map) {
                        Map<String, Object> deviceResultData = (Map<String, Object>) firstResult;
                        deviceResult.put("deviceId", deviceResultData.get("deviceId"));
                        Object successObj = deviceResultData.get("success");
                        if (Boolean.TRUE.equals(successObj)) {
                            deviceResult.put("success", true);
                            deviceResult.put("code", "SUCCESS");
                            deviceResult.put("message", "success");
                        } else {
                            deviceResult.put("success", false);
                            deviceResult.put("code", deviceResultData.get("code"));
                            deviceResult.put("message", deviceResultData.get("message"));
                        }
                        if (deviceResultData.containsKey("costMillis")) {
                            deviceResult.put("costMillis", deviceResultData.get("costMillis"));
                        }
                        Object data = deviceResultData.get("data");
                        if (data instanceof Map && ((Map<?, ?>) data).containsKey("dataType")) {
                            deviceInfo = sanitizeDeviceInfo((Map<String, Object>) data);
                        }
                    }
                }
            }
        } else {
            overallStatus = (String) localInfo.getOrDefault("status", overallStatus);
        }

        result.put("status", overallStatus);
        if (!result.containsKey("message")) {
            result.put("message", stringValue(deviceResult.get("message")));
        }
        result.put("deviceResult", deviceResult);
        if (deviceInfo != null) {
            result.put("deviceInfo", deviceInfo);
        }
        return result;
    }

    private ControlCommandResponse waitForFinalResponse(String commandTaskId) {
        long timeoutMs = Math.max(resultWaitTimeoutMs, 1000L);
        long pollIntervalMs = Math.max(resultPollIntervalMs, 200L);
        long deadline = System.currentTimeMillis() + timeoutMs;
        Map<String, Object> lastStatus = null;

        while (true) {
            lastStatus = getTaskStatus(commandTaskId);
            String status = stringValue(lastStatus.get("status"));
            if (isFinalStatus(status)) {
                return buildFinalResponse(lastStatus, true, null);
            }
            if (System.currentTimeMillis() >= deadline) {
                return buildFinalResponse(lastStatus, false, "等待控制指令最终执行结果超时");
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return buildFinalResponse(lastStatus, false, "等待控制指令最终执行结果被中断");
            }
        }
    }

    private ControlCommandResponse buildFinalResponse(Map<String, Object> statusInfo,
                                                      boolean finalResult,
                                                      String fallbackMessage) {
        if (statusInfo == null) {
            statusInfo = new LinkedHashMap<>();
        }
        ControlCommandResponse response = new ControlCommandResponse();
        String status = stringValue(statusInfo.get("status"));
        if (!finalResult) {
            status = "TIMEOUT";
        }
        boolean success = "SUCCESS".equalsIgnoreCase(status);

        response.setAccepted(Boolean.TRUE.equals(statusInfo.get("found")));
        response.setSuccess(success);
        response.setAllowed(success);
        response.setFinalResult(finalResult);
        response.setRequestId(stringValue(statusInfo.get("requestId")));
        response.setCommandTaskId(stringValue(statusInfo.get("commandTaskId")));
        response.setDeliveryTaskId(stringValue(statusInfo.get("deliveryTaskId")));
        response.setCommand(stringValue(statusInfo.get("command")));
        response.setStatus(status);
        response.setState(status);
        response.setCode(success ? null : status);
        response.setMessage(firstNonBlank(
                fallbackMessage,
                stringValue(statusInfo.get("message")),
                messageFromDeviceResult(statusInfo.get("deviceResult")),
                success ? "success" : status
        ));
        response.setTarget(asMap(statusInfo.get("target")));
        response.setDeviceResult(asMap(statusInfo.get("deviceResult")));
        response.setDeviceInfo(asMap(statusInfo.get("deviceInfo")));
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    private Map<String, Object> sanitizeDeviceInfo(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        putIfPresent(sanitized, source, "dataType");

        putNestedAllowList(sanitized, source, "identity",
                "macAddr", "serialNo", "ipAddr", "groupAddr", "unitAddr");
        putNestedAllowList(sanitized, source, "display",
                "width", "height", "monitorStatus", "brightPercent1", "brightPercent2",
                "brightAdjust", "screenInput", "pixelBadPoints", "pixelSignalErrors");
        putNestedAllowList(sanitized, source, "runtime",
                "dateTime", "timezone", "runningTime", "runningDays", "door", "alarm");
        putNestedAllowList(sanitized, source, "communication",
                "csq", "temperatureInC", "temperatureOutC", "humidityOut", "humidityIn");
        return sanitized.isEmpty() ? null : sanitized;
    }

    @SuppressWarnings("unchecked")
    private void putNestedAllowList(Map<String, Object> target,
                                    Map<String, Object> source,
                                    String key,
                                    String... allowFields) {
        Object value = source.get(key);
        if (!(value instanceof Map)) {
            return;
        }
        Map<String, Object> original = (Map<String, Object>) value;
        Map<String, Object> allowed = new LinkedHashMap<>();
        for (String field : allowFields) {
            putIfPresent(allowed, original, field);
        }
        if (!allowed.isEmpty()) {
            target.put(key, allowed);
        }
    }

    private void putIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private boolean isFinalStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return !("ACCEPTED".equals(normalized)
                || "RUNNING".equals(normalized)
                || "PENDING".equals(normalized)
                || "PROCESSING".equals(normalized));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String messageFromDeviceResult(Object deviceResult) {
        Map<String, Object> result = asMap(deviceResult);
        return result == null ? null : stringValue(result.get("message"));
    }

    private Map<String, Object> buildTargetSnapshot(ControlCommandRequest.TargetRef target) {
        if (target == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("deviceId", target.getDeviceId());
        snapshot.put("ip", target.getIp());
        snapshot.put("port", target.getPort());
        snapshot.put("vendorHint", target.getVendorHint());
        return snapshot;
    }

    private String validateParams(String command, Map<String, Object> params) {
        switch (command) {
            case "BRIGHTNESS": {
                if (params == null || !params.containsKey("brightness")) {
                    return "BRIGHTNESS 指令必须提供 brightness 参数 (0-100)";
                }
                Object val = params.get("brightness");
                int brightness;
                try {
                    brightness = ((Number) val).intValue();
                } catch (Exception e) {
                    return "brightness 参数必须为数值";
                }
                if (brightness < 0 || brightness > 100) {
                    return "brightness 参数必须在 0-100 之间";
                }
                return null;
            }
            case "BLACKOUT": {
                if (params == null || !params.containsKey("enabled")) {
                    return "BLACKOUT 指令必须提供 enabled 参数 (true/false)";
                }
                Object val = params.get("enabled");
                if (!(val instanceof Boolean)) {
                    String s = String.valueOf(val).toLowerCase();
                    if (!"true".equals(s) && !"false".equals(s)) {
                        return "enabled 参数必须为布尔值";
                    }
                }
                return null;
            }
            case "TIME_SYNC":
            case "QUERY_STATUS":
            case "REBOOT":
                return null;
            case "NTP_SET": {
                String ntpServer = firstNonBlank(stringValue(params == null ? null : params.get("ntpServer")),
                        stringValue(params == null ? null : params.get("server")));
                if (ntpServer == null) {
                    return "NTP_SET 指令必须提供 ntpServer 参数";
                }
                if (ntpServer.length() > 255 || containsControlChar(ntpServer)) {
                    return "ntpServer 参数格式非法";
                }
                return null;
            }
            case "DEVICE_IP_SET": {
                String confirmError = validateConfirmed(command, params);
                if (confirmError != null) {
                    return confirmError;
                }
                if (params == null || !isIpv4(stringValue(params.get("ip")))) {
                    return "DEVICE_IP_SET 指令必须提供合法的 ip 参数";
                }
                String mask = firstNonBlank(stringValue(params.get("mask")),
                        stringValue(params.get("netmask")),
                        stringValue(params.get("subnetMask")));
                if (mask != null && !isIpv4(mask)) {
                    return "mask/netmask/subnetMask 参数必须是合法 IPv4 地址";
                }
                String gateway = stringValue(params.get("gateway"));
                if (gateway != null && !isIpv4(gateway)) {
                    return "gateway 参数必须是合法 IPv4 地址";
                }
                Object dns = params.get("dns");
                if (dns != null && !isValidDns(dns)) {
                    return "dns 参数必须是合法 IPv4 地址或 IPv4 地址数组";
                }
                return null;
            }
            case "SCREEN_ATTRIBUTE_SET": {
                String confirmError = validateConfirmed(command, params);
                if (confirmError != null) {
                    return confirmError;
                }
                if (!isPositiveInteger(params, "width")) {
                    return "SCREEN_ATTRIBUTE_SET 指令必须提供正整数 width 参数";
                }
                if (!isPositiveInteger(params, "height")) {
                    return "SCREEN_ATTRIBUTE_SET 指令必须提供正整数 height 参数";
                }
                if (!isPositiveInteger(params, "xCount")) {
                    return "SCREEN_ATTRIBUTE_SET 指令必须提供正整数 xCount 参数";
                }
                if (!isPositiveInteger(params, "yCount")) {
                    return "SCREEN_ATTRIBUTE_SET 指令必须提供正整数 yCount 参数";
                }
                if (!isPositiveInteger(params, "portNumber")) {
                    return "SCREEN_ATTRIBUTE_SET 指令必须提供正整数 portNumber 参数";
                }
                Object orders = params.get("orders");
                if (orders != null && !isIntegerList(orders)) {
                    return "orders 参数必须是整数数组";
                }
                return null;
            }
            default:
                return "未知的控制指令: " + command;
        }
    }

    private String validateTarget(ControlCommandRequest.TargetRef target) {
        if (target == null) {
            return "target 不能为空";
        }
        if (target.getIp() == null || target.getIp().trim().isEmpty()) {
            return "target.ip 不能为空";
        }
        if (target.getPort() == null || target.getPort() <= 0 || target.getPort() > 65535) {
            return "target.port 必须在 1-65535 之间";
        }
        return null;
    }

    private Map<String, Object> normalizeParams(String command, Map<String, Object> params) {
        Map<String, Object> normalized = params != null
                ? new LinkedHashMap<>(params)
                : new LinkedHashMap<>();
        if ("TIME_SYNC".equals(command)) {
            Object time = normalized.get("time");
            if (time == null || String.valueOf(time).trim().isEmpty()) {
                normalized.put("time", OffsetDateTime.now().toString());
            }
        } else if ("NTP_SET".equals(command)) {
            Object ntpServer = normalized.get("ntpServer");
            if (ntpServer == null) {
                Object server = normalized.get("server");
                if (server != null) {
                    normalized.put("ntpServer", server);
                }
            }
        } else if ("DEVICE_IP_SET".equals(command)) {
            if (!normalized.containsKey("mask")) {
                Object mask = normalized.get("netmask");
                if (mask == null) {
                    mask = normalized.get("subnetMask");
                }
                if (mask != null) {
                    normalized.put("mask", mask);
                }
            }
        } else if ("SCREEN_ATTRIBUTE_SET".equals(command)) {
            normalizeInteger(normalized, "width");
            normalizeInteger(normalized, "height");
            normalizeInteger(normalized, "xCount");
            normalizeInteger(normalized, "yCount");
            normalizeInteger(normalized, "xOffset");
            normalizeInteger(normalized, "yOffset");
            normalizeInteger(normalized, "portNumber");
            normalizeInteger(normalized, "screenSource");
        }
        return normalized;
    }

    private String validateConfirmed(String command, Map<String, Object> params) {
        if (params == null || !isTrue(params.get("confirm"))) {
            return command + " 是高风险运维指令，必须提供 confirm=true";
        }
        return null;
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private boolean containsControlChar(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean isIpv4(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String[] parts = value.trim().split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            int num;
            try {
                num = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return false;
            }
            if (num < 0 || num > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidDns(Object dns) {
        if (dns instanceof Collection) {
            Collection<?> values = (Collection<?>) dns;
            if (values.isEmpty()) {
                return false;
            }
            for (Object value : values) {
                if (!isIpv4(stringValue(value))) {
                    return false;
                }
            }
            return true;
        }
        return isIpv4(stringValue(dns));
    }

    private boolean isPositiveInteger(Map<String, Object> params, String key) {
        if (params == null || !params.containsKey(key)) {
            return false;
        }
        Integer value = integerValue(params.get(key));
        return value != null && value > 0;
    }

    private boolean isIntegerList(Object value) {
        if (!(value instanceof Collection)) {
            return false;
        }
        for (Object item : (Collection<?>) value) {
            if (integerValue(item) == null) {
                return false;
            }
        }
        return true;
    }

    private void normalizeInteger(Map<String, Object> params, String key) {
        if (!params.containsKey(key)) {
            return;
        }
        Integer value = integerValue(params.get(key));
        if (value != null) {
            params.put(key, value);
        }
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isGatewayReachable() {
        try {
            HttpResponse response = HttpRequest.get(gatewayApiUrl + "/api/secure-delivery/health")
                    .timeout(Math.min(httpTimeoutMs, 5000))
                    .execute();
            return response.getStatus() >= 200 && response.getStatus() < 300;
        } catch (Exception e) {
            log.warn("[控制指令] 加密网关探活失败: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildControlTask(String commandTaskId,
                                                 ControlCommandRequest request,
                                                 String command,
                                                 Map<String, Object> normalizedParams) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("requestId", request.getRequestId());
        task.put("commandTaskId", commandTaskId);
        task.put("taskId", commandTaskId);
        task.put("action", "CONTROL_SCREEN");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("clientId", "sigma-client-01");
        source.put("clientIp", getLocalHostAddress());
        source.put("hostName", getLocalHostName());
        source.put("operatorId", request.getOperatorId());
        task.put("source", source);

        Map<String, Object> target = new LinkedHashMap<>();
        if (request.getTarget() != null) {
            target.put("deviceId", request.getTarget().getDeviceId());
            target.put("ip", request.getTarget().getIp());
            target.put("port", request.getTarget().getPort());
            target.put("vendorHint", request.getTarget().getVendorHint());
        }
        task.put("target", target);

        task.put("command", command);
        task.put("params", normalizedParams);

        return task;
    }

    private String getLocalHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }

    private String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, Object> callEncryptEndpoint(String commandTaskId, String plaintextJson) {
        String url = gatewayApiUrl + "/api/crypto/command-task/encrypt";
        log.info("[控制指令] 调用加密端点: url={}, commandTaskId={}", url, commandTaskId);

        Map<String, Object> body = new HashMap<>();
        body.put("commandTaskId", commandTaskId);
        body.put("plaintextControlTask", plaintextJson);

        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(JSON.toJSONString(body))
                .timeout(httpTimeoutMs)
                .execute();

        JSONObject json = JSON.parseObject(response.body());
        if (json.getInteger("code") != 200) {
            throw new RuntimeException("加密网关返回错误: " + json.getString("msg"));
        }

        JSONObject data = json.getJSONObject("data");
        if (data == null) {
            throw new RuntimeException("加密网关返回空数据");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("encryptedCommandPackageId", data.getString("encryptedCommandPackageId"));
        result.put("encryptedCommandPackage", data.getString("encryptedCommandPackage"));
        return result;
    }

    private Map<String, Object> callDeliveryEndpoint(String commandTaskId, String encryptedPackageId,
                                                     byte[] encryptedPackage,
                                                     ControlCommandRequest request, String command) {
        String url = gatewayApiUrl + "/api/secure-delivery/control-tasks";
        log.info("[控制指令] 调用投递端点: url={}, commandTaskId={}", url, commandTaskId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("commandTaskId", commandTaskId);
        body.put("encryptedCommandPackageId", encryptedPackageId);
        body.put("encryptedCommandPackage", Base64.getEncoder().encodeToString(encryptedPackage));

        if (request.getTarget() != null) {
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("deviceId", request.getTarget().getDeviceId());
            target.put("ip", request.getTarget().getIp());
            target.put("port", request.getTarget().getPort());
            target.put("vendorHint", request.getTarget().getVendorHint());
            body.put("target", target);
        }

        body.put("command", command);

        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(JSON.toJSONString(body))
                .timeout(httpTimeoutMs)
                .execute();

        JSONObject json = JSON.parseObject(response.body());
        if (json.getInteger("code") != 200) {
            throw new RuntimeException("加密网关投递返回错误: " + json.getString("msg"));
        }

        JSONObject data = json.getJSONObject("data");
        Map<String, Object> result = new HashMap<>();
        if (data != null) {
            result.put("deliveryTaskId", data.getString("deliveryTaskId"));
            result.put("status", data.getString("status"));
            result.put("message", data.getString("message"));
        }
        return result;
    }

    private Map<String, Object> queryGatewayTaskStatus(String deliveryTaskId) {
        String url = gatewayApiUrl + "/api/secure-delivery/control-tasks/" + deliveryTaskId;

        HttpResponse response = HttpRequest.get(url)
                .timeout(httpTimeoutMs)
                .execute();

        JSONObject json = JSON.parseObject(response.body());
        if (json.getInteger("code") != 200) {
            return null;
        }

        JSONObject data = json.getJSONObject("data");
        return data != null ? new HashMap<>(data) : null;
    }
}

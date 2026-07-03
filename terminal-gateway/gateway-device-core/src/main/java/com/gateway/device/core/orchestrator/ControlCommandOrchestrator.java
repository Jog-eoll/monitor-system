package com.gateway.device.core.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.service.CryptoService;
import com.gateway.device.core.controller.dto.SecureControlResponse;
import com.gateway.device.core.controller.dto.SecureStatusCode;
import com.gateway.device.core.controller.dto.StandardizedControlPackage;
import com.gateway.device.core.service.BatchCommandService;
import com.gateway.device.core.service.CommandParamsMapper;
import com.gateway.device.core.service.SecureCommandIdempotencyCache;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.BatchCommandRequest;
import com.gateway.device.protocol.model.BatchTask;
import com.gateway.device.protocol.model.DeviceSelector;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 控制指令编排器 —— 解密 → 解析 → 英文 command 映射为内部 capability → 异步执行。
 * <p>
 * 与 PublishPackageOrchestrator（多步骤文件编排）不同，控制指令是单步执行，
 * 直接委托给 {@link BatchCommandService} 异步下发到设备。
 * </p>
 *
 * <p>命令映射规则（v2）：
 * <pre>
 *   BRIGHTNESS           → BRIGHTNESS_SET       params.brightness 原样传递
 *   REBOOT               → POWER_CONTROL_REBOOT
 *   BLACKOUT + true      → SCREEN_BLACKOUT      params.blackout = true
 *   BLACKOUT + false     → SCREEN_BLACKOUT      params.blackout = false
 *   TIME_SYNC            → TIME_SYNC            ISO 时间 → TimeSyncParams.targetTime
 *   QUERY_STATUS         → DEVICE_INFO_GET      无额外参数
 *   NTP_SET              → NTP_SET              params.ntpServer
 *   DEVICE_IP_SET        → DEVICE_IP_SET        params.ip/mask/gateway/dns
 *   SCREEN_ATTRIBUTE_SET → SCREEN_ATTRIBUTE_SET params.xCount/yCount/width/height/...
 *   FONTS_GET            → FONTS_GET            无额外参数
 *   FONTS_SYNC           → FONTS_SYNC           params.jetFileIIFonts/novaStarFonts
 *   AP_NETWORK_SWITCH    → AP_NETWORK_SWITCH    params.enable
 * </pre>
 * </p>
 */
@Slf4j
@Service
public class ControlCommandOrchestrator {

    private final CryptoService cryptoService;
    private final BatchCommandService batchCommandService;
    private final CommandParamsMapper commandParamsMapper;
    private final SecureEnvelopeParser envelopeParser;
    private final SecureCommandIdempotencyCache idempotencyCache;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ControlCommandOrchestrator(CryptoService cryptoService,
                                      BatchCommandService batchCommandService,
                                      CommandParamsMapper commandParamsMapper,
                                      SecureEnvelopeParser envelopeParser,
                                      SecureCommandIdempotencyCache idempotencyCache) {
        this.cryptoService = cryptoService;
        this.batchCommandService = batchCommandService;
        this.commandParamsMapper = commandParamsMapper;
        this.envelopeParser = envelopeParser;
        this.idempotencyCache = idempotencyCache;
    }

    /**
     * 编排入口 —— 解密 → 解析 → 映射 → 执行。
     *
     * @param commandTaskId   外部控制任务 ID（来自请求头）
     * @param encryptedData   加密后的密文字节
     * @return 控制指令响应
     */
    public SecureControlResponse orchestrate(String commandTaskId, byte[] encryptedData) {
        return orchestrate(commandTaskId, null, encryptedData);
    }

    /**
     * 编排入口（含 requestId，用于幂等与链路追踪）。
     */
    public SecureControlResponse orchestrate(String commandTaskId, String requestId, byte[] encryptedData) {
        // ── 幂等：优先按 requestId/commandTaskId 回放 ──
        String idempotencyKey = resolveIdempotencyKey(requestId, commandTaskId);
        Optional<SecureControlResponse> cached = idempotencyCache.findControl(idempotencyKey);
        if (cached.isPresent()) {
            SecureControlResponse replay = cached.get();
            log.info("[控制编排器] 幂等命中: key={}, commandTaskId={}, batchTaskId={}",
                    idempotencyKey, commandTaskId, replay.getBatchTaskId());
            replay.setRequestId(requestId);
            return replay;
        }

        // ── 第一步: 解密 ──
        byte[] decrypted;
        try {
            decrypted = cryptoService.decrypt(encryptedData);
        } catch (Exception e) {
            log.error("[控制编排器] 解密失败: commandTaskId={}, error={}", commandTaskId, e.getMessage(), e);
            return attachRequestId(SecureControlResponse.rejected(
                    commandTaskId, SecureStatusCode.DECRYPT_FAILED, "密文解密失败: " + e.getMessage()), requestId);
        }

        String json = new String(decrypted, StandardCharsets.UTF_8);
        log.info("[控制编排器] 解密成功: commandTaskId={}, requestId={}, decryptedLength={}",
                commandTaskId, requestId, json.length());

        // ── 第二步: 解析（兼容新信封与旧扁平 DTO）──
        StandardizedControlPackage pkg;
        boolean usedEnvelope = false;
        try {
            SecureEnvelopeParser.ParseOutcome<StandardizedControlPackage> outcome =
                    envelopeParser.parseControl(json);
            pkg = outcome.value();
            usedEnvelope = outcome.isEnvelope();
        } catch (Exception e) {
            log.error("[控制编排器] JSON 解析失败: commandTaskId={}, error={}", commandTaskId, e.getMessage(), e);
            return attachRequestId(SecureControlResponse.rejected(
                    commandTaskId, SecureStatusCode.BAD_JSON, "JSON 解析失败: " + e.getMessage()), requestId);
        }

        // 使用包内 taskId 覆盖（如果请求头未传）
        if (commandTaskId == null || commandTaskId.isEmpty()) {
            commandTaskId = pkg.getTaskId();
        }

        log.info("[控制编排器] 解析完成: commandTaskId={}, envelope={}, command={}, target={}",
                commandTaskId, usedEnvelope, pkg.getCommand(),
                pkg.getTarget() != null ? pkg.getTarget().getDeviceId() : null);

        // ── 第三步: 基本校验 ──
        if (pkg.getCommand() == null || pkg.getCommand().trim().isEmpty()) {
            return attachRequestId(SecureControlResponse.rejected(
                    commandTaskId, SecureStatusCode.VALIDATION_FAILED, "控制指令不能为空"), requestId);
        }
        if (pkg.getTarget() == null) {
            return attachRequestId(SecureControlResponse.rejected(
                    commandTaskId, SecureStatusCode.VALIDATION_FAILED, "目标设备信息不能为空"), requestId);
        }

        String command = pkg.getCommand().trim().toUpperCase();

        // ── 第四步: 英文 command → 内部 DeviceCapability + params 转换 ──
        DeviceCapability<?> capability;
        Map<String, Object> convertedParams;
        CommandParams commandParams;
        try {
            capability = mapCommandToCapability(command);
            if (capability == null) {
                return attachRequestId(SecureControlResponse.rejected(
                        commandTaskId, SecureStatusCode.UNSUPPORTED_CAPABILITY,
                        "不支持的控制指令: " + command), requestId);
            }
            convertedParams = convertParams(command, pkg.getParams());
            commandParams = commandParamsMapper.map(capability, convertedParams);
        } catch (Exception e) {
            log.error("[控制编排器] 指令映射失败: command={}, error={}", command, e.getMessage(), e);
            return attachRequestId(SecureControlResponse.rejected(
                    commandTaskId, SecureStatusCode.VALIDATION_FAILED,
                    "指令映射失败: " + e.getMessage()), requestId);
        }

        // ── 第五步: 构建 DeviceSelector ──
        DeviceSelector selector = buildSelector(pkg.getTarget(), capability);

        // ── 第六步: 构建 BatchCommandRequest 并提交 ──
        BatchCommandRequest request = BatchCommandRequest.builder()
                .requestId(commandTaskId)
                .capability(capability)
                .selector(selector)
                .params(commandParams)
                .build();

        try {
            BatchTask task = batchCommandService.submit(request);
            if (task.getTotal() <= 0) {
                log.warn("[控制编排器] 未找到匹配的目标设备: commandTaskId={}, command={}",
                        commandTaskId, command);
                return attachRequestId(SecureControlResponse.rejected(
                        commandTaskId, SecureStatusCode.TARGET_NOT_FOUND, "未找到匹配的目标设备"), requestId);
            }

            log.info("[控制编排器] 控制指令已提交: commandTaskId={}, requestId={}, batchTaskId={}, capability={}, command={}",
                    commandTaskId, requestId, task.getTaskId(), capability.name(), command);

            SecureControlResponse response = SecureControlResponse.accepted(commandTaskId, task.getTaskId(), capability.name());
            idempotencyCache.putControl(idempotencyKey, response);
            return attachRequestId(response, requestId);
        } catch (Exception e) {
            log.error("[控制编排器] 提交执行失败: commandTaskId={}, error={}",
                    commandTaskId, e.getMessage(), e);
            return attachRequestId(SecureControlResponse.error(
                    commandTaskId, "提交执行失败: " + e.getMessage()), requestId);
        }
    }

    // ═══════════════════════════ 命令映射 ═══════════════════════════

    /**
     * 英文控制指令 → 内部 DeviceCapability。
     * <p>
     * v2 新增：NTP_SET、DEVICE_IP_SET、SCREEN_ATTRIBUTE_SET、FONTS_GET、FONTS_SYNC、AP_NETWORK_SWITCH。
     * POWER 暂不映射，除非 gateway-device-protocol 已有明确开关机能力且对应厂商 Handler 可用。
     * </p>
     */
    private DeviceCapability<?> mapCommandToCapability(String command) {
        switch (command) {
            case "BRIGHTNESS":
                return CommonDeviceCapability.BRIGHTNESS_SET;
            case "POWER":
                log.warn("[控制编排器] POWER 指令暂未映射到 V14 能力模型，当前仅支持 REBOOT");
                return null;
            case "REBOOT":
                return CommonDeviceCapability.POWER_CONTROL_REBOOT;
            case "BLACKOUT":
                return CommonDeviceCapability.SCREEN_BLACKOUT;
            case "TIME_SYNC":
                return CommonDeviceCapability.TIME_SYNC;
            case "QUERY_STATUS":
                return CommonDeviceCapability.DEVICE_INFO_GET;
            // ── v2 新增命令映射 ──
            case "NTP_SET":
                return CommonDeviceCapability.NTP_SET;
            case "DEVICE_IP_SET":
                return CommonDeviceCapability.DEVICE_NETWORK_IP_SET;
            case "SCREEN_ATTRIBUTE_SET":
                return CommonDeviceCapability.SCREEN_ATTRIBUTE_SET;
            case "FONTS_GET":
                return CommonDeviceCapability.FONTS_GET;
            case "FONTS_SYNC":
                return CommonDeviceCapability.FONTS_SYNC;
            case "AP_NETWORK_SWITCH":
                return CommonDeviceCapability.DEVICE_NETWORK_AP_SWITCH;
            default:
                log.warn("[控制编排器] 未知的控制指令: {}", command);
                return null;
        }
    }

    /**
     * 按指令类型转换参数。
     * <p>
     * v2 新增：NTP_SET / DEVICE_IP_SET / SCREEN_ATTRIBUTE_SET / FONTS_GET / FONTS_SYNC / AP_NETWORK_SWITCH 参数转换。
     * </p>
     */
    private Map<String, Object> convertParams(String command, Map<String, Object> originalParams) {
        Map<String, Object> params = new HashMap<>();
        if (originalParams != null) {
            params.putAll(originalParams);
        }

        switch (command) {
            case "BRIGHTNESS": {
                // brightness 原样传递，handler 负责 0-100 或 0-255 转换
                break;
            }
            case "REBOOT": {
                // 重启能力没有业务参数，清除可能误传的 on 参数
                params.remove("on");
                break;
            }
            case "BLACKOUT": {
                // BLACKOUT + enabled → params.blackout = enabled
                // 注意：黑屏语义中 true 表示开启黑屏（息屏），false 表示关闭黑屏（亮屏）
                Object enabled = params.remove("enabled");
                if (enabled != null) {
                    boolean enabledBool;
                    if (enabled instanceof Boolean) {
                        enabledBool = (Boolean) enabled;
                    } else {
                        enabledBool = "true".equalsIgnoreCase(String.valueOf(enabled));
                    }
                    params.put("blackout", enabledBool);
                }
                break;
            }
            case "TIME_SYNC": {
                // ISO 时间字符串转换为 V14 TimeSyncParams.targetTime
                Object time = params.get("time");
                if (time != null && !String.valueOf(time).isEmpty()) {
                    params.put("targetTime", parseControlTime(String.valueOf(time)));
                }
                // 如果 time 为空，handler 可使用当前系统时间
                break;
            }
            case "QUERY_STATUS": {
                // 无额外参数转换
                break;
            }
            case "NTP_SET": {
                // ntpServer 原样传递，CommandParamsMapper 中会映射为 NtpSetParams
                // 也可以接受 server 字段
                Object ntpServer = params.get("ntpServer");
                if (ntpServer == null) {
                    Object server = params.get("server");
                    if (server != null) {
                        params.put("ntpServer", server);
                    }
                }
                break;
            }
            case "DEVICE_IP_SET": {
                // ip/mask/gateway/dns 原样传递，CommandParamsMapper 中映射为 IpConfigParams
                // 也接受 netmask/subnetMask → mask
                if (!params.containsKey("mask")) {
                    Object mask = params.get("netmask");
                    if (mask == null) {
                        mask = params.get("subnetMask");
                    }
                    if (mask != null) {
                        params.put("mask", mask);
                    }
                }
                break;
            }
            case "SCREEN_ATTRIBUTE_SET": {
                // 所有字段原样传递，CommandParamsMapper 中映射为 ScreenAttributeParams
                // xCount/yCount/width/height/portNumber/orders 等
                break;
            }
            case "FONTS_GET": {
                // 无额外参数，EmptyParams
                break;
            }
            case "FONTS_SYNC": {
                // jetFileIIFonts / novaStarFonts 以字符串列表传入，
                // CommandParamsMapper 中转换为 FontSyncParams
                break;
            }
            case "AP_NETWORK_SWITCH": {
                // enable 原样传递，CommandParamsMapper 中映射为 ApNetworkSwitchParams
                // 也接受 enabled/on 字段名
                if (!params.containsKey("enable")) {
                    Object enabled = params.get("enabled");
                    if (enabled == null) {
                        enabled = params.get("on");
                    }
                    if (enabled != null) {
                        boolean enableBool;
                        if (enabled instanceof Boolean) {
                            enableBool = (Boolean) enabled;
                        } else {
                            enableBool = "true".equalsIgnoreCase(String.valueOf(enabled));
                        }
                        params.put("enable", enableBool);
                    }
                }
                break;
            }
            default:
                break;
        }

        return params;
    }

    private LocalDateTime parseControlTime(String time) {
        String value = time.trim();
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        if (value.contains(" ")) {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return LocalDateTime.parse(value + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // ═══════════════════════════ 设备选择器 ═══════════════════════════

    /**
     * 根据 TargetInfo 构建设备选择器。
     */
    private DeviceSelector buildSelector(StandardizedControlPackage.TargetInfo target,
                                         DeviceCapability<?> capability) {
        DeviceSelector.DeviceSelectorBuilder builder = DeviceSelector.builder();
        builder.onlineOnly(true);
        builder.requiredCapabilities(Collections.singleton(capability));

        // 优先 deviceId，兜底 ip
        if (StringUtils.isNotBlank(target.getIp())) {
            builder.ips(Collections.singleton(target.getIp().trim()));
        } else if (StringUtils.isNotBlank(target.getDeviceId())) {
            builder.deviceIds(Collections.singleton(target.getDeviceId().trim()));
        }

        // vendorHint → DeviceVendor
        if (target.getVendorHint() != null && !target.getVendorHint().isEmpty()) {
            DeviceVendor vendor = resolveVendor(target.getVendorHint());
            if (vendor != null) {
                builder.vendors(Collections.singleton(vendor));
            }
        }

        return builder.build();
    }

    /**
     * 解析厂商提示字符串为 DeviceVendor 枚举。
     */
    private DeviceVendor resolveVendor(String vendorHint) {
        String normalized = vendorHint.trim().toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
        if ("QINGSONG".equals(normalized)
                || "QING_SONG".equals(normalized)
                || "JETFILEII".equals(normalized)
                || "JET_FILEII".equals(normalized)
                || "JET_FILE_II".equals(normalized)) {
            return DeviceVendor.JET_FILE_II_STANDARD;
        }
        if ("COLORLIGHT".equals(normalized)
                || "COLOR_LIGHT".equals(normalized)) {
            return DeviceVendor.COLOR_LIGHT_STANDARD;
        }
        try {
            return DeviceVendor.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.debug("[控制编排器] 未知 vendorHint: {}", vendorHint);
            return null;
        }
    }

    // ═══════════════════════════ 辅助方法 ═══════════════════════════

    private SecureControlResponse attachRequestId(SecureControlResponse response, String requestId) {
        if (response != null && StringUtils.isNotBlank(requestId)) {
            response.setRequestId(requestId);
        }
        return response;
    }

    private String resolveIdempotencyKey(String requestId, String commandTaskId) {
        if (StringUtils.isNotBlank(requestId)) {
            return requestId;
        }
        return commandTaskId;
    }
}

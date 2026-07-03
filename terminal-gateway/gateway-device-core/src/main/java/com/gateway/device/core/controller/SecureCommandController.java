package com.gateway.device.core.controller;

import com.gateway.common.Result;
import com.gateway.device.core.controller.dto.SecureCommandResponse;
import com.gateway.device.core.controller.dto.SecureControlResponse;
import com.gateway.device.core.orchestrator.ControlCommandOrchestrator;
import com.gateway.device.core.orchestrator.PublishPackageOrchestrator;
import com.gateway.device.core.service.PublishTaskRegistry;
import com.gateway.device.core.task.InMemoryBatchTaskManager;
import com.gateway.device.protocol.model.BatchTask;
import com.gateway.device.protocol.model.DeviceCommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 密文指令入口 —— 接收加密网关投递的加密包。
 * <p>
 * 提供两个密文入口：
 * <ul>
 *   <li>POST /api/secure-command/publish — 节目发布（多步编排：清屏→上传文件→设置播放列表）</li>
 *   <li>POST /api/secure-command/control — 控制指令（单步执行：亮度/开关机/黑屏/校时/查询/等）</li>
 * </ul>
 * 加密网关将密文以 application/octet-stream 格式 POST，请求头携带追踪 ID。
 * </p>
 *
 * <p>v2 增强：</p>
 * <ul>
 *   <li>统一 payload 大小校验</li>
 *   <li>Header 与 JSON 内任务 ID 一致性校验</li>
 *   <li>requestId 透传到编排器，用于幂等与链路追踪</li>
 *   <li>新增发布任务查询接口</li>
 *   <li>统一日志打印 requestId / deliveryTaskId / commandTaskId / target / mappedCapability</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/secure-command")
public class SecureCommandController {

    /** 默认最大 payload：10 MB */
    private static final int DEFAULT_MAX_PAYLOAD_BYTES = 10 * 1024 * 1024;

    @Resource
    private PublishPackageOrchestrator publishPackageOrchestrator;

    @Resource
    private ControlCommandOrchestrator controlCommandOrchestrator;

    @Resource
    private InMemoryBatchTaskManager batchTaskManager;

    @Resource
    private PublishTaskRegistry publishTaskRegistry;

    @Value("${secure-command.max-payload-bytes:10485760}")
    private int maxPayloadBytes;

    /**
     * 接收加密发布包并编排执行。
     *
     * @param encryptedPayload 加密后的密文字节
     * @param request          HTTP 请求（用于读取请求头）
     * @return 执行结果
     */
    @PostMapping("/publish")
    public Result<SecureCommandResponse> publish(
            @RequestBody byte[] encryptedPayload,
            HttpServletRequest request) {

        String deliveryTaskId = request.getHeader("X-Delivery-Task-Id");
        String requestId = request.getHeader("X-Request-Id");

        log.info("[密文入口] 收到发布请求: deliveryTaskId={}, requestId={}, payloadSize={}",
                deliveryTaskId, requestId,
                encryptedPayload != null ? encryptedPayload.length : 0);

        // ── 统一校验 ──
        if (encryptedPayload == null || encryptedPayload.length == 0) {
            return Result.error("加密载荷不能为空");
        }
        if (encryptedPayload.length > maxPayloadBytes) {
            log.warn("[密文入口] 发布载荷超限: deliveryTaskId={}, size={}, max={}",
                    deliveryTaskId, encryptedPayload.length, maxPayloadBytes);
            return Result.error("加密载荷超过最大限制: " + maxPayloadBytes + " bytes");
        }

        try {
            SecureCommandResponse response = publishPackageOrchestrator
                    .orchestrate(requestId, deliveryTaskId, encryptedPayload);

            if (response.isAccepted()) {
                log.info("[密文入口] 发布包已接受: deliveryTaskId={}, requestId={}, orchestrationTaskId={}",
                        deliveryTaskId, requestId, response.getOrchestrationTaskId());
                return Result.success("发布包已接受并开始编排执行", response);
            } else {
                log.warn("[密文入口] 发布包被拒绝: deliveryTaskId={}, requestId={}, status={}, reason={}",
                        deliveryTaskId, requestId, response.getStatus(), response.getMessage());
                return new Result<>(response.getCode() != null ? response.getCode() : 500,
                        response.getMessage(), response);
            }
        } catch (Exception e) {
            log.error("[密文入口] 处理发布包异常: deliveryTaskId={}, requestId={}, error={}",
                    deliveryTaskId, requestId, e.getMessage(), e);
            return Result.error("处理发布包异常: " + e.getMessage());
        }
    }

    /**
     * 接收加密控制指令并执行。
     *
     * @param encryptedPayload 加密后的密文字节
     * @param request          HTTP 请求（用于读取请求头）
     * @return 执行结果
     */
    @PostMapping("/control")
    public Result<SecureControlResponse> control(
            @RequestBody byte[] encryptedPayload,
            HttpServletRequest request) {

        String commandTaskId = request.getHeader("X-Command-Task-Id");
        String deliveryTaskId = request.getHeader("X-Delivery-Task-Id");
        String requestId = request.getHeader("X-Request-Id");

        log.info("[控制入口] 收到控制请求: commandTaskId={}, deliveryTaskId={}, requestId={}, payloadSize={}",
                commandTaskId, deliveryTaskId, requestId,
                encryptedPayload != null ? encryptedPayload.length : 0);

        // ── 统一校验 ──
        if (encryptedPayload == null || encryptedPayload.length == 0) {
            return Result.error("加密载荷不能为空");
        }
        if (encryptedPayload.length > maxPayloadBytes) {
            log.warn("[控制入口] 控制载荷超限: commandTaskId={}, size={}, max={}",
                    commandTaskId, encryptedPayload.length, maxPayloadBytes);
            return Result.error("加密载荷超过最大限制: " + maxPayloadBytes + " bytes");
        }

        try {
            SecureControlResponse response = controlCommandOrchestrator
                    .orchestrate(commandTaskId, requestId, encryptedPayload);

            if (response.isAccepted()) {
                log.info("[控制入口] 控制指令已接受: commandTaskId={}, requestId={}, batchTaskId={}, capability={}",
                        commandTaskId, requestId, response.getBatchTaskId(), response.getMappedCapability());
                return Result.success("控制指令已接受", response);
            } else {
                log.warn("[控制入口] 控制指令被拒绝: commandTaskId={}, requestId={}, status={}, reason={}",
                        commandTaskId, requestId, response.getStatus(), response.getMessage());
                return new Result<>(response.getCode() != null ? response.getCode() : 500,
                        response.getMessage(), response);
            }
        } catch (Exception e) {
            log.error("[控制入口] 处理控制指令异常: commandTaskId={}, requestId={}, error={}",
                    commandTaskId, requestId, e.getMessage(), e);
            return Result.error("处理控制指令异常: " + e.getMessage());
        }
    }

    /**
     * 查询控制指令在解密网关侧的执行状态。
     *
     * @param batchTaskId 解密网关内部批量任务 ID
     * @return 执行状态和设备级结果
     */
    @GetMapping("/control-tasks/{batchTaskId}")
    public Result<Map<String, Object>> getControlTask(@PathVariable String batchTaskId) {
        Map<String, Object> result = new HashMap<>();
        BatchTask task = batchTaskManager.get(batchTaskId).orElse(null);
        if (task == null) {
            result.put("found", false);
            result.put("batchTaskId", batchTaskId);
            return Result.success("控制任务不存在", result);
        }

        result.put("found", true);
        result.put("batchTaskId", task.getTaskId());
        result.put("status", task.getStatus().name());
        result.put("successCount", task.getSuccessCount());
        result.put("failedCount", task.getFailedCount());
        result.put("total", task.getTotal());
        result.put("message", buildTaskMessage(task));

        List<Map<String, Object>> results = new ArrayList<>();
        for (DeviceCommandResult deviceResult : task.getResults()) {
            Map<String, Object> item = new HashMap<>();
            item.put("deviceId", deviceResult.getDeviceId());
            item.put("vendor", deviceResult.getVendor());
            item.put("success", deviceResult.isSuccess());
            item.put("code", deviceResult.getCode());
            item.put("message", deviceResult.getMessage());
            item.put("data", deviceResult.getData());
            item.put("costMillis", deviceResult.getCostMillis());
            results.add(item);
        }
        result.put("results", results);
        return Result.success("控制任务状态", result);
    }

    /**
     * 查询发布编排任务在解密网关侧的执行状态（v2 新增）。
     * <p>
     * 发布包是多步骤同步编排，每次编排产生一个 orchestrationTaskId，
     * 本接口按该 ID 查询编排结果快照。
     * </p>
     *
     * @param orchestrationTaskId 解密网关内部编排任务 ID
     * @return 编排结果
     */
    @GetMapping("/publish-tasks/{orchestrationTaskId}")
    public Result<Map<String, Object>> getPublishTask(@PathVariable String orchestrationTaskId) {
        Map<String, Object> result = new HashMap<>();
        SecureCommandResponse response = publishTaskRegistry.find(orchestrationTaskId).orElse(null);
        if (response == null) {
            result.put("found", false);
            result.put("orchestrationTaskId", orchestrationTaskId);
            return Result.success("发布编排任务不存在", result);
        }

        result.put("found", true);
        result.put("orchestrationTaskId", response.getOrchestrationTaskId());
        result.put("deliveryTaskId", response.getDeliveryTaskId());
        result.put("requestId", response.getRequestId());
        result.put("accepted", response.isAccepted());
        result.put("status", response.getStatus());
        result.put("code", response.getCode());
        result.put("message", response.getMessage());

        List<Map<String, Object>> steps = new ArrayList<>();
        if (response.getSteps() != null) {
            for (SecureCommandResponse.StepResult step : response.getSteps()) {
                Map<String, Object> stepMap = new HashMap<>();
                stepMap.put("step", step.getStep());
                stepMap.put("fileOrderNo", step.getFileOrderNo());
                stepMap.put("fileName", step.getFileName());
                stepMap.put("batchTaskId", step.getBatchTaskId());
                stepMap.put("status", step.getStatus());
                stepMap.put("message", step.getMessage());
                stepMap.put("devicePath", step.getDevicePath());
                steps.add(stepMap);
            }
        }
        result.put("steps", steps);
        return Result.success("发布编排任务状态", result);
    }

    private String buildTaskMessage(BatchTask task) {
        switch (task.getStatus()) {
            case SUCCESS:
                return "控制指令执行成功";
            case FAILED:
                return "控制指令执行失败";
            case PARTIAL_SUCCESS:
                return "控制指令部分成功";
            case TIMEOUT:
                return "控制指令执行超时";
            default:
                return "控制指令执行中";
        }
    }

    /**
     * 健康检查端点 —— 加密网关用于探测解密网关可用性。
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("ok");
    }
}

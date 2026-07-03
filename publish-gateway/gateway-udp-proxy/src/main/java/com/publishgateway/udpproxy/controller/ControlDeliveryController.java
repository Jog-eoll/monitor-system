package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.entity.dto.control.*;
import com.publishgateway.udpproxy.service.ControlDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 控制指令投递控制器 —— 加密 + 投递到解密网关。
 * <p>
 * 提供三个端点：
 * <ul>
 *   <li>POST /api/crypto/command-task/encrypt — 加密明文控制任务</li>
 *   <li>POST /api/secure-delivery/control-tasks — 投递加密控制包到解密网关</li>
 *   <li>GET /api/secure-delivery/control-tasks/{commandTaskId} — 查询控制任务状态</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestController
public class ControlDeliveryController {

    @Resource
    private ControlDeliveryService controlDeliveryService;

    /**
     * 加密明文控制任务 JSON。
     *
     * @param request 加密请求（含 commandTaskId 和明文 JSON）
     * @return 加密响应（含 Base64 密文）
     */
    @PostMapping("/api/crypto/command-task/encrypt")
    public Map<String, Object> encryptControlTask(@Valid @RequestBody ControlTaskEncryptRequest request) {
        log.info("[控制加密] 收到加密请求: commandTaskId={}", request.getCommandTaskId());

        Map<String, Object> result = new HashMap<>();
        try {
            ControlTaskEncryptResponse response = controlDeliveryService.encryptControlTask(request);
            result.put("code", 200);
            result.put("msg", "success");
            result.put("data", response);
        } catch (Exception e) {
            log.error("[控制加密] 加密失败: commandTaskId={}, error={}",
                    request.getCommandTaskId(), e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "加密失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 创建控制任务投递 —— 将加密控制包转发到解密网关。
     *
     * @param request 投递请求（含加密包、目标设备、指令类型）
     * @return 投递响应（含 deliveryTaskId）
     */
    @PostMapping("/api/secure-delivery/control-tasks")
    public Map<String, Object> createControlTask(@Valid @RequestBody ControlDeliveryTaskRequest request) {
        log.info("[控制投递] 收到投递请求: commandTaskId={}, command={}",
                request.getCommandTaskId(), request.getCommand());

        Map<String, Object> result = new HashMap<>();
        try {
            ControlDeliveryTaskResponse response = controlDeliveryService.createControlTask(request);
            result.put("code", 200);
            result.put("msg", "success");
            result.put("data", response);
        } catch (Exception e) {
            log.error("[控制投递] 投递创建失败: commandTaskId={}, error={}",
                    request.getCommandTaskId(), e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "投递创建失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 查询控制任务状态。
     *
     * @param commandTaskId 控制任务 ID 或投递任务 ID
     * @return 任务状态信息
     */
    @GetMapping("/api/secure-delivery/control-tasks/{commandTaskId}")
    public Map<String, Object> getControlTaskStatus(@PathVariable String commandTaskId) {
        log.info("[控制投递] 查询任务状态: commandTaskId={}", commandTaskId);

        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> status = controlDeliveryService.getControlTaskStatus(commandTaskId);
            result.put("code", 200);
            result.put("msg", "success");
            result.put("data", status);
        } catch (Exception e) {
            log.error("[控制投递] 查询失败: commandTaskId={}, error={}",
                    commandTaskId, e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "查询失败: " + e.getMessage());
        }
        return result;
    }
}

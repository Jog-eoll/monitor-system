package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.entity.dto.delivery.DeliveryTaskStatus;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskRequest;
import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskResponse;
import com.publishgateway.udpproxy.service.SecureDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全投递控制器
 * <p>
 * 加密网关侧的安全发布任务管理接口。
 * </p>
 *
 * <p>接口清单：</p>
 * <ul>
 *   <li>POST /api/secure-delivery/tasks — 创建投递任务</li>
 *   <li>GET  /api/secure-delivery/tasks/{deliveryTaskId} — 查询任务状态</li>
 *   <li>GET  /api/secure-delivery/health — 健康检查</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/secure-delivery")
public class SecureDeliveryController {

    @Resource
    private SecureDeliveryService secureDeliveryService;

    @PostMapping("/tasks")
    public SecureDeliveryTaskResponse createTask(@Valid @RequestBody SecureDeliveryTaskRequest request) {
        log.info("[安全投递] 收到任务请求: sigmaPublishId={}, files={}",
                request.getSigmaPublishId(),
                request.getFiles() != null ? request.getFiles().size() : 0);
        try {
            return secureDeliveryService.createTask(request);
        } catch (Exception e) {
            log.error("[安全投递] 任务创建异常: {}", e.getMessage(), e);
            return SecureDeliveryTaskResponse.error("任务创建失败: " + e.getMessage());
        }
    }

    @GetMapping("/tasks/{deliveryTaskId}")
    public Map<String, Object> getTaskStatus(@PathVariable String deliveryTaskId) {
        DeliveryTaskStatus status = secureDeliveryService.getTaskStatus(deliveryTaskId);
        Map<String, Object> result = new HashMap<>();
        if (status != null) {
            result.put("found", true);
            result.put("status", status.getStatus());
            result.put("deliveryTaskId", status.getDeliveryTaskId());
            result.put("sigmaPublishId", status.getSigmaPublishId());
            result.put("totalFiles", status.getTotalFiles());
            result.put("downloadedFiles", status.getDownloadedFiles());
            result.put("deliveredFiles", status.getDeliveredFiles());
            result.put("message", status.getMessage());
            result.put("createdAt", status.getCreatedAt());
            result.put("updatedAt", status.getUpdatedAt());
        } else {
            result.put("found", false);
            result.put("message", "任务不存在: " + deliveryTaskId);
        }
        return result;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("healthy", secureDeliveryService.isHealthy());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
}

package com.infopublish.client.controller;

import com.infopublish.client.entity.dto.control.ControlCommandRequest;
import com.infopublish.client.entity.dto.control.ControlCommandResponse;
import com.infopublish.client.service.ControlCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Map;

/**
 * 控制指令 HTTP 入口 —— Sigma 客户端通过此控制器下发屏幕控制指令。
 * <p>
 * 支持的指令：QUERY_STATUS、BRIGHTNESS、BLACKOUT、REBOOT、TIME_SYNC、NTP_SET、DEVICE_IP_SET、SCREEN_ATTRIBUTE_SET
 * </p>
 *
 * <pre>
 * 链路: Sigma → POST /api/client/commands/execute
 *          → 安全检查 → 参数校验 → 加密网关加密 → 加密网关投递 → 解密网关执行
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/client/commands")
public class ControlCommandController {

    @Resource
    private ControlCommandService controlCommandService;

    /**
     * 执行控制指令
     *
     * @param request 控制指令请求（含 requestId/operatorId/target/command/params）
     * @return 执行结果
     */
    @PostMapping("/execute")
    public ControlCommandResponse execute(@Valid @RequestBody ControlCommandRequest request) {
        log.info("[控制指令入口] 收到执行请求: requestId={}, command={}, operatorId={}",
                request.getRequestId(), request.getCommand(), request.getOperatorId());

        try {
            ControlCommandResponse response = controlCommandService.execute(request);

            if (response.isSuccess()) {
                log.info("[控制指令入口] 指令同步执行成功: commandTaskId={}, deliveryTaskId={}, status={}",
                        response.getCommandTaskId(), response.getDeliveryTaskId(), response.getStatus());
            } else {
                log.warn("[控制指令入口] 指令同步执行未通过: commandTaskId={}, status={}, reason={}",
                        response.getCommandTaskId(), response.getStatus(), response.getMessage());
            }

            return response;
        } catch (Exception e) {
            log.error("[控制指令入口] 执行异常: requestId={}, error={}",
                    request.getRequestId(), e.getMessage(), e);
            return ControlCommandResponse.error(null, "执行异常: " + e.getMessage());
        }
    }

    /**
     * 查询控制任务状态
     *
     * @param commandTaskId 控制任务 ID
     * @return 任务状态信息
     */
    @GetMapping("/{commandTaskId}")
    public Map<String, Object> getStatus(@PathVariable String commandTaskId) {
        log.info("[控制指令入口] 查询任务状态: commandTaskId={}", commandTaskId);
        return controlCommandService.getTaskStatus(commandTaskId);
    }
}

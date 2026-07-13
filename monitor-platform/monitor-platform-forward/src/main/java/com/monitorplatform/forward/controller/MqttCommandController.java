package com.monitorplatform.forward.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.entity.dto.MqttNonDestructiveCommandRequest;
import com.monitorplatform.forward.entity.dto.MqttQueryStatusRequest;
import com.monitorplatform.forward.service.DeviceCommandDispatcher;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MQTT 非破坏命令入口。
 * <p>
 * 仅允许 QUERY_STATUS / NOOP / ECHO 三个非破坏命令，不开放任意 command 透传，
 * 避免平台成为未审计命令发布口。三条命令均在网关走 native probe，不触发本地 HTTP 回环。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/mqtt/commands")
public class MqttCommandController {

    @Resource
    private DeviceCommandDispatcher deviceCommandDispatcher;

    @PostMapping("/query-status")
    public Result<?> queryStatus(@Validated @RequestBody MqttQueryStatusRequest request) {
        String gatewayDeviceId = resolveGatewayDeviceId(request.getGatewayDeviceId());
        log.info("[MQTT-COMMAND] query status: gatewayDeviceId={}, businessId={}",
                gatewayDeviceId, request.getBusinessId());

        DeviceMqttCommand command = deviceCommandDispatcher.dispatch(
                gatewayDeviceId,
                MqttCommandMessage.COMMAND_QUERY_STATUS,
                request.toPayload(),
                Collections.emptyList(),
                request.getBusinessId());
        return awaitAndRespond(command, request.getWaitForReply(), request.getWaitTimeoutSec());
    }

    @PostMapping("/noop")
    public Result<?> noop(@Validated @RequestBody MqttNonDestructiveCommandRequest request) {
        String gatewayDeviceId = resolveGatewayDeviceId(request.getGatewayDeviceId());
        log.info("[MQTT-COMMAND] noop: gatewayDeviceId={}, businessId={}",
                gatewayDeviceId, request.getBusinessId());

        DeviceMqttCommand command = deviceCommandDispatcher.dispatch(
                gatewayDeviceId,
                MqttCommandMessage.COMMAND_NOOP,
                request.toPayload(),
                Collections.emptyList(),
                request.getBusinessId());
        return awaitAndRespond(command, request.getWaitForReply(), request.getWaitTimeoutSec());
    }

    @PostMapping("/echo")
    public Result<?> echo(@Validated @RequestBody MqttNonDestructiveCommandRequest request) {
        String gatewayDeviceId = resolveGatewayDeviceId(request.getGatewayDeviceId());
        log.info("[MQTT-COMMAND] echo: gatewayDeviceId={}, businessId={}",
                gatewayDeviceId, request.getBusinessId());

        DeviceMqttCommand command = deviceCommandDispatcher.dispatch(
                gatewayDeviceId,
                MqttCommandMessage.COMMAND_ECHO,
                request.toPayload(),
                Collections.emptyList(),
                request.getBusinessId());
        return awaitAndRespond(command, request.getWaitForReply(), request.getWaitTimeoutSec());
    }

    @GetMapping("/{messageId}")
    public Result<?> getByMessageId(@PathVariable String messageId) {
        DeviceMqttCommand command = deviceCommandDispatcher.findByMessageId(messageId);
        if (command == null) {
            return Result.error("MQTT command not found: " + messageId);
        }
        return Result.success(toResponse(command));
    }

    private String resolveGatewayDeviceId(String raw) {
        return raw == null ? null : raw.trim();
    }

    private Result<?> awaitAndRespond(DeviceMqttCommand command, Boolean waitForReply, Integer waitTimeoutSec) {
        if (command == null || command.getId() == null) {
            return Result.error("MQTT command publish failed");
        }

        DeviceMqttCommand current = command;
        if (Boolean.TRUE.equals(waitForReply)) {
            int timeoutSec = waitTimeoutSec == null ? 30 : waitTimeoutSec;
            current = deviceCommandDispatcher.waitForFinalStatus(command.getId(), timeoutSec);
            if (current == null) {
                current = command;
            }
            if (!deviceCommandDispatcher.isSuccess(current)) {
                return Result.error("MQTT command failed: " + current.getStatus()
                        + (current.getErrorMessage() == null ? "" : " - " + current.getErrorMessage()));
            }
        }
        return Result.success("MQTT command accepted", toResponse(current));
    }

    private Map<String, Object> toResponse(DeviceMqttCommand command) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", command.getId());
        data.put("messageId", command.getMessageId());
        data.put("businessId", command.getBusinessId());
        data.put("tenantId", command.getTenantId());
        data.put("siteId", command.getSiteId());
        data.put("gatewayDeviceId", command.getGatewayDeviceId());
        data.put("targetDeviceId", command.getTargetDeviceId());
        data.put("command", command.getCommand());
        data.put("status", command.getStatus());
        data.put("errorCode", command.getErrorCode());
        data.put("errorMessage", command.getErrorMessage());
        data.put("replyPayload", command.getReplyPayload());
        data.put("publishedAt", command.getPublishedAt());
        data.put("ackTime", command.getAckTime());
        data.put("timeoutAt", command.getTimeoutAt());
        data.put("updateTime", command.getUpdateTime());
        return data;
    }
}

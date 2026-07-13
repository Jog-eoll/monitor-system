package com.monitorplatform.forward.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.entity.dto.ControlDeliveryRequest;
import com.monitorplatform.forward.service.DeviceCommandDispatcher;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MQTT 控制命令投递入口（CONTROL_DELIVERY）。
 * <p>
 * 受控控制命令，不开放任意 HTTP 透传。目标为发布网关时 action 到
 * /api/secure-delivery/control-tasks；目标为终端网关时 action 到
 * /api/secure-command/control。覆盖亮度、黑屏、校时、查询状态等最小控制命令。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/mqtt/commands")
public class MqttControlDeliveryController {

    private static final String COMMAND_CONTROL_DELIVERY = "CONTROL_DELIVERY";

    private static final String TYPE_PUBLISH_GATEWAY = "publish_gateway";
    private static final String TYPE_TERMINAL_GATEWAY = "terminal_encrypt_gateway";

    private static final String PATH_PUBLISH_CONTROL = "/api/secure-delivery/control-tasks";
    private static final String PATH_TERMINAL_CONTROL = "/api/secure-command/control";

    @Resource
    private DeviceCommandDispatcher deviceCommandDispatcher;

    @PostMapping("/control-delivery")
    public Result<?> controlDelivery(@Validated @RequestBody ControlDeliveryRequest request) {
        String gatewayDeviceId = request.getGatewayDeviceId() == null ? null : request.getGatewayDeviceId().trim();
        String targetDeviceType = request.getTargetDeviceType();
        log.info("[MQTT-COMMAND] control delivery: gatewayDeviceId={}, targetDeviceType={}, controlCommand={}, businessId={}",
                gatewayDeviceId, targetDeviceType, request.getControlCommand(), request.getBusinessId());

        MqttCommandMessage.Action action = new MqttCommandMessage.Action();
        action.setTargetDeviceType(targetDeviceType);
        action.setPath(resolveControlPath(targetDeviceType));
        action.setHttpMethod("POST");

        // toActionBody 仅调用一次，保证 action body 与 dispatch payload 中的 commandTaskId/encryptedCommandPackage 一致
        Map<String, Object> actionBody = request.toActionBody();
        action.setBody(actionBody);

        if (request.getTargetIp() == null || request.getTargetIp().trim().isEmpty()
                || request.getTargetPort() == null || request.getTargetPort() <= 0) {
            return Result.error("targetIp/targetPort cannot be blank for control delivery");
        }

        if (TYPE_PUBLISH_GATEWAY.equalsIgnoreCase(targetDeviceType)) {
            action.setMode("SELF_APPLY");
        } else if (request.getTargetIp() != null && !request.getTargetIp().trim().isEmpty()
                && request.getTargetPort() != null && request.getTargetPort() > 0) {
            action.setMode("HTTP");
            action.setTargetIp(request.getTargetIp().trim());
            action.setTargetPort(request.getTargetPort());
        } else {
            action.setMode("SELF_APPLY");
        }

        DeviceMqttCommand command = deviceCommandDispatcher.dispatch(
                gatewayDeviceId,
                COMMAND_CONTROL_DELIVERY,
                actionBody,
                Collections.singletonList(action),
                request.getBusinessId());

        return awaitAndRespond(command, request.getWaitForReply(), request.getWaitTimeoutSec());
    }

    private String resolveControlPath(String targetDeviceType) {
        if (TYPE_TERMINAL_GATEWAY.equalsIgnoreCase(targetDeviceType)) {
            return PATH_TERMINAL_CONTROL;
        }
        return PATH_PUBLISH_CONTROL;
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

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", current.getId());
        data.put("messageId", current.getMessageId());
        data.put("status", current.getStatus());
        data.put("errorMessage", current.getErrorMessage());
        data.put("replyPayload", current.getReplyPayload());
        return Result.success("MQTT control delivery accepted", data);
    }
}

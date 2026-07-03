package com.monitorplatform.forward.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.entity.dto.GatewayNetworkChangeRequest;
import com.monitorplatform.forward.entity.dto.GatewayNetworkConfirmRequest;
import com.monitorplatform.forward.service.IpChangeCallbackResult;
import com.monitorplatform.forward.service.IpChangeCallbackService;
import com.monitorplatform.forward.service.MqttCommandPublishService;
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

@Slf4j
@RestController
@RequestMapping("/gateway/network")
public class GatewayNetworkController {

    private static final int WAIT_TIMEOUT_SEC = 30;

    @Resource
    private MqttCommandPublishService mqttCommandPublishService;

    @Resource
    private IpChangeCallbackService ipChangeCallbackService;

    @PostMapping("/change-ip")
    public Result<?> changeIp(@Validated @RequestBody GatewayNetworkChangeRequest request) {
        try {
            String targetDeviceId = request.resolveTargetDeviceId();
            log.info("下发目标设备系统IP变更命令: targetDeviceId={}, interfaceName={}, newIp={}, dryRun={}",
                    targetDeviceId, request.getInterfaceName(), request.getNewIp(), request.getDryRun());
            DeviceMqttCommand record = mqttCommandPublishService.publishCommand(
                    targetDeviceId,
                    "CHANGE_SYSTEM_IP",
                    request.toPayload(),
                    Collections.emptyList());
            Result<?> result = waitAndBuildResult(record);

            if (Boolean.TRUE.equals(request.getDryRun())) {
                log.info("[change-ip] dryRun mode, skip DB callback. targetDeviceId={}", targetDeviceId);
                return result;
            }

            if (result.getCode() == 200) {
                IpChangeCallbackResult callbackResult = ipChangeCallbackService.onIpSuccess(targetDeviceId, request.getNewIp());
                if (result.getData() instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) result.getData();
                    data.put("callbackDeviceUpdated", callbackResult.isDeviceUpdated());
                    data.put("callbackLinkNodesUpdated", callbackResult.getLinkNodesUpdated());
                    if (!callbackResult.isSuccess()) {
                        data.put("callbackWarning", "设备表或链路节点IP同步失败，请检查日志");
                        log.warn("[change-ip] DB sync incomplete. deviceId={}, deviceUpdated={}, linkNodesUpdated={}",
                                targetDeviceId, callbackResult.isDeviceUpdated(), callbackResult.getLinkNodesUpdated());
                    }
                }
            }

            return result;
        } catch (Exception e) {
            log.error("下发目标设备系统IP变更命令失败", e);
            return Result.error("下发目标设备系统IP变更命令失败: " + e.getMessage());
        }
    }

    @PostMapping("/confirm-ip")
    public Result<?> confirmIp(@Validated @RequestBody GatewayNetworkConfirmRequest request) {
        try {
            String targetDeviceId = request.resolveTargetDeviceId();
            log.info("下发目标设备系统IP变更确认命令: targetDeviceId={}, changeId={}",
                    targetDeviceId, request.getChangeId());
            DeviceMqttCommand record = mqttCommandPublishService.publishCommand(
                    targetDeviceId,
                    "CONFIRM_SYSTEM_IP",
                    request.toPayload(),
                    Collections.emptyList());
            return waitAndBuildResult(record);
        } catch (Exception e) {
            log.error("下发目标设备系统IP变更确认命令失败", e);
            return Result.error("下发目标设备系统IP变更确认命令失败: " + e.getMessage());
        }
    }

    private Result<?> waitAndBuildResult(DeviceMqttCommand record) {
        if (record == null || record.getId() == null) {
            return Result.error("MQTT命令发布失败");
        }
        DeviceMqttCommand latest = mqttCommandPublishService.waitForFinalStatus(record.getId(), WAIT_TIMEOUT_SEC);
        Map<String, Object> data = new LinkedHashMap<>();
        DeviceMqttCommand current = latest == null ? record : latest;
        data.put("commandId", current.getId());
        data.put("messageId", current.getMessageId());
        data.put("targetDeviceId", current.getTargetDeviceId());
        data.put("gatewayDeviceId", current.getGatewayDeviceId());
        data.put("command", current.getCommand());
        data.put("status", current.getStatus());
        data.put("errorMessage", current.getErrorMessage());
        data.put("ackTime", current.getAckTime());
        data.put("updateTime", current.getUpdateTime());
        if (mqttCommandPublishService.isSuccess(current)) {
            return Result.success("MQTT命令执行成功", data);
        }
        return Result.error("MQTT命令执行失败: " + current.getStatus()
                + (current.getErrorMessage() == null ? "" : " - " + current.getErrorMessage()));
    }
}

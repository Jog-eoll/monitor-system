package com.monitorplatform.forward.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.mapper.DeviceMqttCommandMapper;
import com.monitorplatform.mqtt.core.dto.MqttReplyMessage;
import com.monitorplatform.upgrade.service.RemoteUpgradeReplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class MqttReplyHandler {

    @Autowired
    private DeviceMqttCommandMapper deviceMqttCommandMapper;

    @Autowired(required = false)
    private RemoteUpgradeReplyService remoteUpgradeReplyService;

    @Autowired(required = false)
    private MqttCommandEventService mqttCommandEventService;

    public void handleReply(MqttReplyMessage reply) {
        if (reply == null || reply.getCommandMessageId() == null) {
            log.warn("[MQTT-REPLY] empty reply or commandMessageId, ignored");
            return;
        }

        String commandMessageId = reply.getCommandMessageId();
        String status = reply.getStatus();
        log.info("[MQTT-REPLY] received reply: commandMessageId={}, status={}, gatewayDeviceId={}, message={}",
                commandMessageId, status, reply.getGatewayDeviceId(), reply.getMessage());

        LambdaQueryWrapper<DeviceMqttCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceMqttCommand::getMessageId, commandMessageId);
        wrapper.last("LIMIT 1");
        DeviceMqttCommand command = deviceMqttCommandMapper.selectOne(wrapper);

        if (command == null) {
            log.warn("[MQTT-REPLY] command record not found: commandMessageId={}", commandMessageId);
            return;
        }
        if (isFinalStatus(command.getStatus())) {
            log.info("[MQTT-REPLY] ignore reply for final command: commandMessageId={}, currentStatus={}, incomingStatus={}",
                    commandMessageId, command.getStatus(), status);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        command.setAckTime(now);
        command.setUpdateTime(now);
        command.setReplyPayload(reply.getData() == null ? null : JSON.toJSONString(reply.getData()));
        command.setErrorCode(reply.getErrorCode());

        if (MqttReplyMessage.STATUS_SUCCESS.equalsIgnoreCase(status)) {
            command.setStatus(DeviceMqttCommand.STATUS_SUCCESS);
            log.info("[MQTT-REPLY] command success: commandMessageId={}, commandId={}", commandMessageId, command.getId());
        } else if (MqttReplyMessage.STATUS_FAILED.equalsIgnoreCase(status)) {
            command.setStatus(DeviceMqttCommand.STATUS_FAILED);
            command.setErrorMessage(reply.getMessage());
            log.warn("[MQTT-REPLY] command failed: commandMessageId={}, commandId={}, error={}",
                    commandMessageId, command.getId(), reply.getMessage());
        } else if (MqttReplyMessage.STATUS_PROCESSING.equalsIgnoreCase(status)) {
            command.setStatus(DeviceMqttCommand.STATUS_PROCESSING);
            log.info("[MQTT-REPLY] command processing: commandMessageId={}, commandId={}", commandMessageId, command.getId());
        } else if (MqttReplyMessage.STATUS_RECEIVED.equalsIgnoreCase(status)) {
            command.setStatus(DeviceMqttCommand.STATUS_RECEIVED);
            log.info("[MQTT-REPLY] command received by gateway: commandMessageId={}, commandId={}", commandMessageId, command.getId());
        } else if (MqttReplyMessage.STATUS_REJECTED.equalsIgnoreCase(status)) {
            command.setStatus(DeviceMqttCommand.STATUS_FAILED);
            command.setErrorMessage("gateway rejected: " + reply.getMessage());
            if (command.getErrorCode() == null) {
                command.setErrorCode(MqttReplyMessage.ERROR_REJECTED);
            }
            log.warn("[MQTT-REPLY] command rejected: commandMessageId={}, commandId={}, reason={}",
                    commandMessageId, command.getId(), reply.getMessage());
        } else {
            log.warn("[MQTT-REPLY] unknown reply status: commandMessageId={}, status={}", commandMessageId, status);
            return;
        }

        deviceMqttCommandMapper.updateById(command);
        recordEvent(command, command.getStatus(), reply);

        if (remoteUpgradeReplyService != null) {
            try {
                remoteUpgradeReplyService.handleReply(command, reply);
            } catch (Exception e) {
                log.error("[MQTT-REPLY] sync remote upgrade status failed: commandMessageId={}", commandMessageId, e);
            }
        }
    }

    private boolean isFinalStatus(String status) {
        return DeviceMqttCommand.STATUS_SUCCESS.equals(status)
                || DeviceMqttCommand.STATUS_FAILED.equals(status)
                || DeviceMqttCommand.STATUS_TIMEOUT.equals(status)
                || DeviceMqttCommand.STATUS_CANCELED.equals(status);
    }

    private void recordEvent(DeviceMqttCommand command, String status, Object payload) {
        if (mqttCommandEventService != null) {
            mqttCommandEventService.record(command, status, payload);
        }
    }
}

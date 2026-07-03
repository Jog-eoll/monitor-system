package com.monitorplatform.forward.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.mapper.DeviceMqttCommandMapper;
import com.monitorplatform.mqtt.core.dto.MqttReplyMessage;
import com.monitorplatform.upgrade.service.RemoteUpgradeReplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * MQTT 回执消息处理器
 * <p>
 * 接收网关上行回报的 MqttReplyMessage，根据 commandMessageId 关联
 * DeviceMqttCommand 记录，更新命令状态为 SUCCESS / FAILED。
 * </p>
 */
@Slf4j
@Service
public class MqttReplyHandler {

    @Autowired
    private DeviceMqttCommandMapper deviceMqttCommandMapper;

    @Autowired(required = false)
    private RemoteUpgradeReplyService remoteUpgradeReplyService;

    /**
     * 处理回执消息
     *
     * @param reply 网关上行的回执消息
     */
    public void handleReply(MqttReplyMessage reply) {
        if (reply == null || reply.getCommandMessageId() == null) {
            log.warn("[MQTT回执] 回执消息或 commandMessageId 为空，忽略");
            return;
        }

        String commandMessageId = reply.getCommandMessageId();
        String status = reply.getStatus();
        log.info("[MQTT回执] 收到回执: commandMessageId={}, status={}, gatewayDeviceId={}, message={}",
                commandMessageId, status, reply.getGatewayDeviceId(), reply.getMessage());

        // 根据 commandMessageId 查找命令记录
        LambdaQueryWrapper<DeviceMqttCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceMqttCommand::getMessageId, commandMessageId);
        wrapper.last("LIMIT 1");
        DeviceMqttCommand command = deviceMqttCommandMapper.selectOne(wrapper);

        if (command == null) {
            log.warn("[MQTT回执] 未找到对应的命令记录: commandMessageId={}", commandMessageId);
            return;
        }

        // 根据回执状态更新命令记录
        LocalDateTime now = LocalDateTime.now();
        command.setAckTime(now);
        command.setUpdateTime(now);

        if ("SUCCESS".equalsIgnoreCase(status)) {
            command.setStatus(DeviceMqttCommand.STATUS_SUCCESS);
            log.info("[MQTT回执] 命令执行成功: commandMessageId={}, commandId={}", commandMessageId, command.getId());
        } else if ("FAILED".equalsIgnoreCase(status)) {
            command.setStatus(DeviceMqttCommand.STATUS_FAILED);
            command.setErrorMessage(reply.getMessage());
            log.warn("[MQTT回执] 命令执行失败: commandMessageId={}, commandId={}, error={}",
                    commandMessageId, command.getId(), reply.getMessage());
        } else if ("PROCESSING".equalsIgnoreCase(status)) {
            command.setStatus(DeviceMqttCommand.STATUS_PROCESSING);
            log.info("[MQTT回执] 命令正在执行中: commandMessageId={}, commandId={}", commandMessageId, command.getId());
        } else if ("RECEIVED".equalsIgnoreCase(status)) {
            command.setStatus(DeviceMqttCommand.STATUS_RECEIVED);
            log.info("[MQTT回执] 命令已被网关接收: commandMessageId={}, commandId={}", commandMessageId, command.getId());
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            command.setStatus(DeviceMqttCommand.STATUS_FAILED);
            command.setErrorMessage("网关拒绝执行: " + reply.getMessage());
            log.warn("[MQTT回执] 命令被网关拒绝: commandMessageId={}, commandId={}, reason={}",
                    commandMessageId, command.getId(), reply.getMessage());
        } else {
            log.warn("[MQTT回执] 未知的回执状态: commandMessageId={}, status={}", commandMessageId, status);
        }

        deviceMqttCommandMapper.updateById(command);

        if (remoteUpgradeReplyService != null) {
            try {
                remoteUpgradeReplyService.handleReply(command, reply);
            } catch (Exception e) {
                log.error("[MQTT回执] 同步远程升级任务状态失败: commandMessageId={}", commandMessageId, e);
            }
        }
    }
}

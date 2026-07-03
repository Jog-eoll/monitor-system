package com.monitorplatform.forward.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.mapper.DeviceMqttCommandMapper;
import com.monitorplatform.upgrade.service.RemoteUpgradeReplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * MQTT 命令超时扫描定时任务
 * <p>
 * 定期扫描状态为 PUBLISHED / RECEIVED / PROCESSING 且已超过 timeoutAt 的命令记录，
 * 将其状态更新为 TIMEOUT 并记录警告日志。
 * </p>
 */
@Slf4j
@Service
public class MqttCommandTimeoutTask {

    @Autowired
    private DeviceMqttCommandMapper deviceMqttCommandMapper;

    @Autowired(required = false)
    private RemoteUpgradeReplyService remoteUpgradeReplyService;

    /**
     * 需要检查超时的命令状态列表
     */
    private static final List<String> ACTIVE_STATUSES = Arrays.asList(
            DeviceMqttCommand.STATUS_CREATED,
            DeviceMqttCommand.STATUS_PUBLISHED,
            DeviceMqttCommand.STATUS_RECEIVED,
            DeviceMqttCommand.STATUS_PROCESSING
    );

    /**
     * 扫描超时命令
     * <p>
     * 查询 status in (PUBLISHED, RECEIVED, PROCESSING) 且 timeoutAt < now() 的记录，
     * 逐条更新为 TIMEOUT 状态并记录警告日志。
     * </p>
     */
    @Scheduled(fixedDelayString = "${forward.dispatch.timeout-scan-interval-ms:30000}")
    public void scanTimeout() {
        try {
            LambdaQueryWrapper<DeviceMqttCommand> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(DeviceMqttCommand::getStatus, ACTIVE_STATUSES)
                    .lt(DeviceMqttCommand::getTimeoutAt, LocalDateTime.now());

            List<DeviceMqttCommand> timeoutCommands = deviceMqttCommandMapper.selectList(wrapper);

            if (timeoutCommands == null || timeoutCommands.isEmpty()) {
                return;
            }

            log.warn("[MQTT超时扫描] 发现 {} 条超时命令，开始更新状态", timeoutCommands.size());

            LocalDateTime now = LocalDateTime.now();
            for (DeviceMqttCommand command : timeoutCommands) {
                try {
                    command.setStatus(DeviceMqttCommand.STATUS_TIMEOUT);
                    command.setErrorMessage("命令超时，超过 timeoutAt 未收到回执");
                    command.setAckTime(now);
                    command.setUpdateTime(now);
                    deviceMqttCommandMapper.updateById(command);
                    if (remoteUpgradeReplyService != null) {
                        remoteUpgradeReplyService.handleTimeout(command);
                    }

                    log.warn("[MQTT超时扫描] 命令超时: id={}, messageId={}, gatewayDeviceId={}, command={}, timeoutAt={}",
                            command.getId(),
                            command.getMessageId(),
                            command.getGatewayDeviceId(),
                            command.getCommand(),
                            command.getTimeoutAt());
                } catch (Exception e) {
                    log.error("[MQTT超时扫描] 更新超时命令失败: id={}, messageId={}",
                            command.getId(), command.getMessageId(), e);
                }
            }

            log.info("[MQTT超时扫描] 超时扫描完成，共处理 {} 条超时命令", timeoutCommands.size());

        } catch (Exception e) {
            log.error("[MQTT超时扫描] 超时扫描任务执行失败", e);
        }
    }
}

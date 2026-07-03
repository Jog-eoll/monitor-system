package com.monitorplatform.upgrade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.mqtt.core.dto.MqttReplyMessage;
import com.monitorplatform.upgrade.entity.RemoteUpgradeConstants;
import com.monitorplatform.upgrade.entity.RemoteUpgradeTask;
import com.monitorplatform.upgrade.entity.RemoteUpgradeTaskDevice;
import com.monitorplatform.upgrade.mapper.RemoteUpgradeTaskDeviceMapper;
import com.monitorplatform.upgrade.mapper.RemoteUpgradeTaskMapper;
import com.monitorplatform.upgrade.service.RemoteUpgradeReplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 远程升级 MQTT 回执同步。
 */
@Slf4j
@Service
public class RemoteUpgradeReplyServiceImpl implements RemoteUpgradeReplyService {

    @Resource
    private RemoteUpgradeTaskDeviceMapper taskDeviceMapper;

    @Resource
    private RemoteUpgradeTaskMapper taskMapper;

    @Override
    public void handleReply(DeviceMqttCommand command, MqttReplyMessage reply) {
        if (command == null || reply == null
                || !RemoteUpgradeConstants.COMMAND_REMOTE_UPGRADE.equalsIgnoreCase(command.getCommand())) {
            return;
        }
        LambdaQueryWrapper<RemoteUpgradeTaskDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RemoteUpgradeTaskDevice::getMqttMessageId, command.getMessageId())
                .last("LIMIT 1");
        RemoteUpgradeTaskDevice taskDevice = taskDeviceMapper.selectOne(wrapper);
        if (taskDevice == null) {
            log.warn("[远程升级] 未找到 MQTT 回执对应任务明细: messageId={}", command.getMessageId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        taskDevice.setAckTime(now);
        taskDevice.setUpdateTime(now);
        Map<String, Object> data = reply.getData();
        applyProgressData(taskDevice, data);

        String status = reply.getStatus();
        if ("SUCCESS".equalsIgnoreCase(status)) {
            taskDevice.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_SUCCESS);
            taskDevice.setStage("SUCCESS");
            taskDevice.setProgress(100);
            taskDevice.setFinishTime(now);
        } else if ("FAILED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
            taskDevice.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_FAILED);
            taskDevice.setStage(taskDevice.getStage() == null ? "FAILED" : taskDevice.getStage());
            taskDevice.setErrorMessage(reply.getMessage());
            taskDevice.setFinishTime(now);
        } else if ("PROCESSING".equalsIgnoreCase(status) || "RECEIVED".equalsIgnoreCase(status)) {
            taskDevice.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_PROCESSING);
            if (taskDevice.getProgress() == null || taskDevice.getProgress() < 10) {
                taskDevice.setProgress(10);
            }
            if (taskDevice.getStage() == null || "PUBLISHED".equals(taskDevice.getStage())) {
                taskDevice.setStage("PROCESSING");
            }
        }

        taskDeviceMapper.updateById(taskDevice);
        refreshTaskSummary(taskDevice.getTaskId());
    }

    private void applyProgressData(RemoteUpgradeTaskDevice taskDevice, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Object stage = data.get("stage");
        if (stage != null && stage.toString().trim().length() > 0) {
            taskDevice.setStage(stage.toString().trim());
        }
        Object progress = data.get("progress");
        Integer parsed = toInt(progress);
        if (parsed != null) {
            taskDevice.setProgress(Math.max(0, Math.min(100, parsed)));
        }
        Object message = data.get("message");
        if (message != null && message.toString().trim().length() > 0
                && RemoteUpgradeConstants.DEVICE_STATUS_FAILED.equals(taskDevice.getStatus())) {
            taskDevice.setErrorMessage(message.toString());
        }
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private void refreshTaskSummary(Long taskId) {
        if (taskId == null) {
            return;
        }
        LambdaQueryWrapper<RemoteUpgradeTaskDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RemoteUpgradeTaskDevice::getTaskId, taskId);
        List<RemoteUpgradeTaskDevice> devices = taskDeviceMapper.selectList(wrapper);
        int success = 0;
        int failed = 0;
        int timeout = 0;
        for (RemoteUpgradeTaskDevice device : devices) {
            if (RemoteUpgradeConstants.DEVICE_STATUS_SUCCESS.equals(device.getStatus())) {
                success++;
            } else if (RemoteUpgradeConstants.DEVICE_STATUS_FAILED.equals(device.getStatus())) {
                failed++;
            } else if (RemoteUpgradeConstants.DEVICE_STATUS_TIMEOUT.equals(device.getStatus())) {
                timeout++;
            }
        }
        RemoteUpgradeTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setSuccessCount(success);
        task.setFailedCount(failed);
        task.setTimeoutCount(timeout);
        if (!devices.isEmpty() && success == devices.size()) {
            task.setStatus(RemoteUpgradeConstants.TASK_STATUS_SUCCESS);
            task.setFinishTime(LocalDateTime.now());
        } else if (!devices.isEmpty() && failed + timeout == devices.size()) {
            task.setStatus(RemoteUpgradeConstants.TASK_STATUS_FAILED);
            task.setFinishTime(LocalDateTime.now());
        } else if (!devices.isEmpty() && success + failed + timeout == devices.size()) {
            task.setStatus(RemoteUpgradeConstants.TASK_STATUS_PARTIAL_FAILED);
            task.setFinishTime(LocalDateTime.now());
        } else {
            task.setStatus(RemoteUpgradeConstants.TASK_STATUS_RUNNING);
        }
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }
}

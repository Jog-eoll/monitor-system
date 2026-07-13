package com.monitorplatform.forward.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.mapper.DeviceMqttCommandMapper;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class MqttDeviceCommandDispatcher implements DeviceCommandDispatcher {

    @Resource
    private MqttCommandPublishService mqttCommandPublishService;

    @Resource
    private DeviceMqttCommandMapper deviceMqttCommandMapper;

    @Override
    public DeviceMqttCommand dispatch(String gatewayDeviceId,
                                      String command,
                                      Map<String, Object> payload,
                                      List<MqttCommandMessage.Action> actions,
                                      String businessId) {
        return mqttCommandPublishService.publishCommand(gatewayDeviceId, command, payload, actions, businessId);
    }

    @Override
    public DeviceMqttCommand waitForFinalStatus(Long commandId, int timeoutSec) {
        return mqttCommandPublishService.waitForFinalStatus(commandId, timeoutSec);
    }

    @Override
    public DeviceMqttCommand findByMessageId(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<DeviceMqttCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceMqttCommand::getMessageId, messageId.trim());
        wrapper.last("LIMIT 1");
        return deviceMqttCommandMapper.selectOne(wrapper);
    }

    @Override
    public boolean isSuccess(DeviceMqttCommand command) {
        return mqttCommandPublishService.isSuccess(command);
    }
}

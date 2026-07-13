package com.monitorplatform.forward.service;

import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;

import java.util.List;
import java.util.Map;

public interface DeviceCommandDispatcher {

    DeviceMqttCommand dispatch(String gatewayDeviceId,
                               String command,
                               Map<String, Object> payload,
                               List<MqttCommandMessage.Action> actions,
                               String businessId);

    DeviceMqttCommand waitForFinalStatus(Long commandId, int timeoutSec);

    DeviceMqttCommand findByMessageId(String messageId);

    boolean isSuccess(DeviceMqttCommand command);
}

package com.monitorplatform.upgrade.service;

import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.mqtt.core.dto.MqttReplyMessage;

public interface RemoteUpgradeReplyService {

    void handleReply(DeviceMqttCommand command, MqttReplyMessage reply);

    void handleTimeout(DeviceMqttCommand command);
}

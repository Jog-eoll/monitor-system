package com.monitorplatform.forward.service;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.entity.DeviceMqttCommandEvent;
import com.monitorplatform.forward.mapper.DeviceMqttCommandEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Service
public class MqttCommandEventService {

    @Resource
    private DeviceMqttCommandEventMapper deviceMqttCommandEventMapper;

    public void record(DeviceMqttCommand command, String status, Object eventPayload) {
        if (command == null || command.getMessageId() == null || command.getMessageId().trim().isEmpty()) {
            return;
        }
        try {
            DeviceMqttCommandEvent event = new DeviceMqttCommandEvent();
            event.setCommandMessageId(command.getMessageId());
            event.setStatus(status == null ? command.getStatus() : status);
            event.setEventPayload(eventPayload == null ? null : JSON.toJSONString(eventPayload));
            event.setEventTime(LocalDateTime.now());
            deviceMqttCommandEventMapper.insert(event);
        } catch (Exception e) {
            log.warn("[MQTT-EVENT] record command event failed: messageId={}, status={}, error={}",
                    command.getMessageId(), status, e.getMessage(), e);
        }
    }
}

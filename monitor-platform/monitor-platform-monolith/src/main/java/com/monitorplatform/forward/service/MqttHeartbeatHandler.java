package com.monitorplatform.forward.service;

import com.monitorplatform.device.entity.dto.DeviceHeartbeatDTO;
import com.monitorplatform.device.service.UnifiedDeviceService;
import com.monitorplatform.mqtt.core.dto.MqttHeartbeatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * MQTT 心跳消息处理器。
 */
@Slf4j
@Service
public class MqttHeartbeatHandler {

    @Autowired(required = false)
    private UnifiedDeviceService unifiedDeviceService;

    /**
     * 处理心跳消息。
     *
     * @param heartbeat 网关上行的心跳消息
     */
    public void handleHeartbeat(MqttHeartbeatMessage heartbeat) {
        if (heartbeat == null || heartbeat.getDeviceId() == null) {
            log.warn("[MQTT心跳] 心跳消息或 deviceId 为空，忽略");
            return;
        }

        log.info("[MQTT心跳] 收到心跳: deviceId={}, deviceType={}, status={}, timestamp={}",
                heartbeat.getDeviceId(),
                heartbeat.getDeviceType(),
                heartbeat.getStatus(),
                heartbeat.getTimestamp());

        if (unifiedDeviceService == null) {
            log.warn("[MQTT心跳] UnifiedDeviceService 未注入，跳过设备在线状态刷新: deviceId={}",
                    heartbeat.getDeviceId());
            return;
        }

        try {
            DeviceHeartbeatDTO dto = new DeviceHeartbeatDTO();
            dto.setDeviceId(heartbeat.getDeviceId());
            dto.setStatus(resolvePlatformStatus(heartbeat.getStatus()));
            dto.setExtraData(heartbeat.getMetadata());
            boolean success = unifiedDeviceService.receiveHeartbeat(dto);
            if (success) {
                log.debug("[MQTT心跳] 已刷新设备在线状态: deviceId={}", heartbeat.getDeviceId());
            } else {
                log.warn("[MQTT心跳] 刷新设备在线状态失败: deviceId={}", heartbeat.getDeviceId());
            }
        } catch (Exception e) {
            log.warn("[MQTT心跳] 刷新设备在线状态异常: deviceId={}, error={}",
                    heartbeat.getDeviceId(), e.getMessage());
        }
    }

    private String resolvePlatformStatus(String mqttStatus) {
        if ("OFFLINE".equalsIgnoreCase(mqttStatus)) {
            return "离线";
        }
        return "在线";
    }
}

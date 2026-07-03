package com.monitorplatform.forward.service;

import com.monitorplatform.forward.feign.DeviceFeignClient;
import com.monitorplatform.mqtt.core.dto.MqttHeartbeatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * MQTT 心跳消息处理器
 * <p>
 * 接收网关上行的心跳消息，记录设备在线状态。
 * v1 版本仅记录日志，后续可通过 DeviceFeignClient 更新设备在线状态。
 * </p>
 */
@Slf4j
@Service
public class MqttHeartbeatHandler {

    @Autowired
    private DeviceFeignClient deviceFeignClient;

    /**
     * 处理心跳消息
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

        try {
            if (deviceFeignClient != null) {
                Map<String, Object> response = deviceFeignClient.heartbeat(heartbeat.getDeviceId());
                Object code = response == null ? null : response.get("code");
                if (code instanceof Number && ((Number) code).intValue() == 200) {
                    log.debug("[MQTT心跳] 已刷新设备在线状态: deviceId={}", heartbeat.getDeviceId());
                } else {
                    log.warn("[MQTT心跳] 刷新设备在线状态失败: deviceId={}, response={}",
                            heartbeat.getDeviceId(), response);
                }
            }
        } catch (Exception e) {
            log.warn("[MQTT心跳] 调用 DeviceFeignClient 失败: deviceId={}, error={}",
                    heartbeat.getDeviceId(), e.getMessage());
        }
    }
}

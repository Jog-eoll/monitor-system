package com.publishgateway.udpproxy.mqtt;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.mqtt.core.dto.MqttEnvelope;
import com.monitorplatform.mqtt.core.dto.MqttHeartbeatMessage;
import com.monitorplatform.mqtt.core.dto.MqttTopicBuilder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 心跳发布器 —— 定期上行 HEARTBEAT 消息，平台据此维护设备在线状态。
 * <p>
 * fixedDelay 由 mqtt-agent.heartbeat-interval-sec 控制（单位：秒），
 * 通过 SpEL 拼接毫秒值。仅当 MQTT 客户端已连接时才真正发布。
 * </p>
 */
@Slf4j
@Component
public class HeartbeatPublisher {

    @Resource
    private MqttAgentProperties properties;

    @Resource
    private MqttConnectionManager mqttConnectionManager;

    @Value("${server.port:8092}")
    private int serverPort;

    @Value("${registry.client.version:}")
    private String deviceVersion;

    @Value("${registry.client.location:}")
    private String location;

    @Value("${registry.client.manufacturer:}")
    private String manufacturer;

    @Value("${registry.client.model:}")
    private String model;

    /**
     * 定时发布心跳。fixedDelayString 取
     * {@code ${mqtt-agent.heartbeat-interval-sec:30}000}，即 30 秒 * 1000 毫秒。
     */
    @Scheduled(fixedDelayString = "${mqtt-agent.heartbeat-interval-sec:30}000")
    public void publishHeartbeat() {
        if (!properties.isEnabled()) {
            return;
        }
        MqttClient client = mqttConnectionManager.getMqttClient();
        if (client == null || !client.isConnected()) {
            log.debug("[MQTT-HEARTBEAT] 客户端未连接，跳过心跳");
            return;
        }

        String deviceId = properties.resolveDeviceId();

        MqttHeartbeatMessage heartbeat = MqttHeartbeatMessage.online(
                deviceId, properties.getDeviceType(), buildMetadata());

        MqttEnvelope envelope = new MqttEnvelope();
        envelope.setMessageId(UUID.randomUUID().toString());
        envelope.setMessageType("HEARTBEAT");
        envelope.setTenantId(properties.getTenantId());
        envelope.setSiteId(properties.getSiteId());
        envelope.setDeviceId(deviceId);
        envelope.setDeviceType(properties.getDeviceType());
        envelope.setTimestamp(System.currentTimeMillis());
        envelope.setPayload(JSON.toJSONString(heartbeat));

        String topic = MqttTopicBuilder.upHeartbeat(
                properties.getTenantId(), properties.getSiteId(), deviceId);
        String envelopeJson = JSON.toJSONString(envelope);

        try {
            MqttMessage message = new MqttMessage(envelopeJson.getBytes(StandardCharsets.UTF_8));
            message.setQos(properties.getQos());
            client.publish(topic, message);
            log.info("[MQTT-HEARTBEAT] 已发布心跳: topic={}, deviceId={}, qos={}",
                    topic, deviceId, properties.getQos());
        } catch (MqttException e) {
            log.error("[MQTT-HEARTBEAT] 发布心跳失败: topic={}, {}",
                    topic, e.getMessage(), e);
        }
    }

    private Map<String, Object> buildMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("clientId", properties.getClientId());
        metadata.put("innerIp", resolveLocalIp());
        metadata.put("serverPort", serverPort);
        putIfNotBlank(metadata, "version", deviceVersion);
        putIfNotBlank(metadata, "location", location);
        putIfNotBlank(metadata, "manufacturer", manufacturer);
        putIfNotBlank(metadata, "model", model);
        return metadata;
    }

    private void putIfNotBlank(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            metadata.put(key, value.trim());
        }
    }

    private String resolveLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }
}

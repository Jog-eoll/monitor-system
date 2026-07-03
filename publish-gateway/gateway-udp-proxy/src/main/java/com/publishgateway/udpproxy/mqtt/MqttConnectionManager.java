package com.publishgateway.udpproxy.mqtt;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.mqtt.core.client.MqttClientFactory;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import com.monitorplatform.mqtt.core.dto.MqttEnvelope;
import com.monitorplatform.mqtt.core.dto.MqttMessageTypes;
import com.monitorplatform.mqtt.core.dto.MqttTopicBuilder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * MQTT connection manager for the gateway-side agent.
 */
@Slf4j
@Component
public class MqttConnectionManager {

    @Resource
    private MqttAgentProperties properties;

    @Resource
    private GatewayCommandDispatcher gatewayCommandDispatcher;

    @Resource
    private DeviceRegisterPublisher deviceRegisterPublisher;

    private volatile MqttClient mqttClient;

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("[MQTT] mqtt-agent.enabled=false, skip MQTT connection init");
            return;
        }
        try {
            String deviceId = properties.resolveDeviceId();
            mqttClient = MqttClientFactory.createAndConnect(
                    properties.getBrokerUrl(),
                    properties.getClientId(),
                    properties.getUsername(),
                    properties.getPassword(),
                    properties.isCleanSession(),
                    properties.getKeepAliveSec(),
                    properties.isAutoReconnect(),
                    properties.buildSslOptions());

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("[MQTT] connection lost: {}", cause == null ? "" : cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleIncomingMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    log.debug("[MQTT] delivery complete token={}", token == null ? null : token.getMessageId());
                }
            });

            subscribeDownTopics(deviceId);
            if (properties.isRegisterOnConnect()) {
                deviceRegisterPublisher.publishRegister(mqttClient, "CONNECT");
            }
        } catch (MqttException e) {
            log.error("[MQTT] init connection failed: {}", e.getMessage(), e);
            mqttClient = null;
        } catch (Exception e) {
            log.error("[MQTT] init failed: {}", e.getMessage(), e);
            mqttClient = null;
        }
    }

    @Scheduled(fixedDelayString = "${mqtt-agent.reconnect-interval-ms:30000}")
    public void reconnectIfNecessary() {
        if (!properties.isEnabled() || mqttClient != null) {
            return;
        }
        log.warn("[MQTT] client not initialized, reconnect broker");
        init();
    }

    @Scheduled(fixedDelayString = "${mqtt-agent.registration-interval-sec:300}000")
    public void publishRegisterPeriodically() {
        if (!properties.isEnabled()
                || !properties.isRegistrationEnabled()
                || properties.getRegistrationIntervalSec() <= 0) {
            return;
        }
        deviceRegisterPublisher.publishRegister(mqttClient, "PERIODIC");
    }

    private void subscribeDownTopics(String deviceId) throws MqttException {
        String downAllTopic = MqttTopicBuilder.downSubscribeAll(
                properties.getTenantId(), properties.getSiteId(), deviceId);
        mqttClient.subscribe(downAllTopic, properties.getQos());
        log.info("[MQTT] subscribed down topic: {}, QoS={}", downAllTopic, properties.getQos());

        log.info("[MQTT] down/# subscription covers legacy topic: {}",
                MqttTopicBuilder.downCommand(properties.getTenantId(), properties.getSiteId(), deviceId));
    }

    private void handleIncomingMessage(String topic, MqttMessage message) {
        try {
            String json = new String(message.getPayload(), StandardCharsets.UTF_8);
            MqttEnvelope envelope = JSON.parseObject(json, MqttEnvelope.class);
            log.info("[MQTT] received down message topic={}, messageId={}, messageType={}",
                    topic,
                    envelope == null ? null : envelope.getMessageId(),
                    envelope == null ? null : envelope.getMessageType());

            MqttEnvelope normalized = normalizeEnvelope(topic, envelope);
            if (normalized != null && normalized.getPayload() != null) {
                gatewayCommandDispatcher.onCommand(normalized);
            } else {
                log.warn("[MQTT] ignored empty or unsupported down message: {}", topic);
            }
        } catch (Exception e) {
            log.error("[MQTT] handle down message failed: {}", e.getMessage(), e);
        }
    }

    private MqttEnvelope normalizeEnvelope(String topic, MqttEnvelope envelope) {
        if (envelope == null || envelope.getPayload() == null) {
            return envelope;
        }
        if (isCommandTopic(topic) || MqttMessageTypes.PROXY_COMMAND.equalsIgnoreCase(envelope.getMessageType())
                || MqttMessageTypes.COMMAND.equalsIgnoreCase(envelope.getMessageType())) {
            envelope.setMessageType(MqttMessageTypes.PROXY_COMMAND);
            return envelope;
        }
        if (isUpgradeTopic(topic) || MqttMessageTypes.UPGRADE.equalsIgnoreCase(envelope.getMessageType())) {
            MqttCommandMessage command = new MqttCommandMessage();
            command.setCommand("REMOTE_UPGRADE");
            command.setPayload(JSON.parseObject(envelope.getPayload(), Map.class));
            command.setQos(properties.getQos());
            envelope.setMessageType(MqttMessageTypes.PROXY_COMMAND);
            envelope.setPayload(JSON.toJSONString(command));
            return envelope;
        }
        log.warn("[MQTT] unsupported down message type: topic={}, messageType={}", topic, envelope.getMessageType());
        return null;
    }

    private boolean isCommandTopic(String topic) {
        return topic != null && (topic.endsWith("/down/proxy-command") || topic.endsWith("/down/command"));
    }

    private boolean isUpgradeTopic(String topic) {
        return topic != null && topic.endsWith("/down/upgrade");
    }

    @PreDestroy
    public void destroy() {
        if (mqttClient != null) {
            log.info("[MQTT] application closing, disconnect MQTT");
            MqttClientFactory.disconnectQuietly(mqttClient);
            mqttClient = null;
        }
    }

    public MqttClient getMqttClient() {
        return mqttClient;
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
}

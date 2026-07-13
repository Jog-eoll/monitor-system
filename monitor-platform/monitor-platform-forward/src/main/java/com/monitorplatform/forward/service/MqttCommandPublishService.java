package com.monitorplatform.forward.service;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.forward.config.MqttDispatchProperties;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.mapper.DeviceMqttCommandMapper;
import com.monitorplatform.mqtt.core.client.MqttClientFactory;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import com.monitorplatform.mqtt.core.dto.MqttDeviceRegisterMessage;
import com.monitorplatform.mqtt.core.dto.MqttEnvelope;
import com.monitorplatform.mqtt.core.dto.MqttHeartbeatMessage;
import com.monitorplatform.mqtt.core.dto.MqttMessageTypes;
import com.monitorplatform.mqtt.core.dto.MqttReplyMessage;
import com.monitorplatform.mqtt.core.dto.MqttTopicBuilder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MQTT command publisher and upstream message dispatcher.
 */
@Slf4j
@Service
public class MqttCommandPublishService {

    @Autowired
    private DeviceMqttCommandMapper deviceMqttCommandMapper;

    @Autowired
    private MqttDispatchProperties properties;

    @Autowired
    private MqttReplyHandler mqttReplyHandler;

    @Autowired
    private MqttHeartbeatHandler mqttHeartbeatHandler;

    @Autowired(required = false)
    private MqttRegisterHandler mqttRegisterHandler;

    @Autowired(required = false)
    private MqttCommandEventService mqttCommandEventService;

    private volatile MqttClient mqttClient;

    @PostConstruct
    public void init() {
        if (!isMqttEnabled()) {
            log.info("[MQTT发布] 当前下发模式为{}，不启用MQTT客户端", properties.getMode());
            return;
        }

        try {
            log.info("[MQTT发布] 初始化MQTT客户端 broker={}, clientId={}",
                    properties.getMqttBrokerUrl(), properties.getPlatformClientId());

            mqttClient = MqttClientFactory.createAndConnect(
                    properties.getMqttBrokerUrl(),
                    properties.getPlatformClientId(),
                    properties.getMqttUsername(),
                    properties.getMqttPassword(),
                    false,
                    properties.getKeepAliveSec(),
                    true,
                    properties.buildSslOptions()
            );

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("[MQTT发布] 连接断开: {}", cause == null ? "" : cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    try {
                        String payloadJson = new String(message.getPayload(), StandardCharsets.UTF_8);
                        dispatchIncomingMessage(topic, payloadJson);
                    } catch (Exception e) {
                        log.error("[MQTT发布] 解析消息失败: topic={}", topic, e);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    log.debug("[MQTT发布] 消息投递完成 token={}", token.getMessageId());
                }
            });

            String subscribeTopic = MqttTopicBuilder.upSubscribeAll();
            mqttClient.subscribe(subscribeTopic, properties.getQos());
            log.info("[MQTT发布] 已订阅上行通配Topic: {}", subscribeTopic);
        } catch (MqttException e) {
            log.error("[MQTT发布] MQTT客户端初始化失败", e);
            mqttClient = null;
        }
    }

    @Scheduled(fixedDelayString = "${forward.dispatch.reconnect-interval-ms:30000}")
    public void reconnectIfNecessary() {
        if (!isMqttEnabled() || mqttClient != null) {
            return;
        }
        log.warn("[MQTT发布] MQTT客户端未初始化，尝试重新连接");
        init();
    }

    public DeviceMqttCommand publishCommand(String gatewayDeviceId,
                                             String command,
                                             Map<String, Object> payload,
                                             List<MqttCommandMessage.Action> actions) {
        return publishCommand(gatewayDeviceId, command, payload, actions, null);
    }

    public DeviceMqttCommand publishCommand(String gatewayDeviceId,
                                             String command,
                                             Map<String, Object> payload,
                                             List<MqttCommandMessage.Action> actions,
                                             String businessId) {
        if (!hasText(gatewayDeviceId)) {
            log.warn("[MQTT发布] gatewayDeviceId为空，拒绝发布命令 command={}", command);
            return null;
        }

        String messageId = UUID.randomUUID().toString().replace("-", "");
        String tenantId = properties.getTenantId();
        String siteId = properties.getSiteId();

        MqttCommandMessage commandMessage = new MqttCommandMessage();
        commandMessage.setCommand(command);
        commandMessage.setBusinessId(businessId);
        commandMessage.setPayload(payload);
        commandMessage.setActions(actions);
        commandMessage.setQos(properties.getQos());
        commandMessage.setTimeoutAt(System.currentTimeMillis()
                + (long) properties.getCommandTimeoutSec() * 1000);

        String topic = MqttTopicBuilder.downCommand(tenantId, siteId, gatewayDeviceId);
        String payloadJson = JSON.toJSONString(commandMessage);

        MqttEnvelope envelope = new MqttEnvelope();
        envelope.setMessageId(messageId);
        envelope.setMessageType(MqttMessageTypes.PROXY_COMMAND);
        envelope.setTenantId(tenantId);
        envelope.setSiteId(siteId);
        envelope.setDeviceId(gatewayDeviceId);
        envelope.setTimestamp(System.currentTimeMillis());
        envelope.setPayload(payloadJson);

        DeviceMqttCommand record = new DeviceMqttCommand();
        record.setMessageId(messageId);
        record.setTenantId(tenantId);
        record.setSiteId(siteId);
        record.setGatewayDeviceId(gatewayDeviceId);
        record.setTargetDeviceId(gatewayDeviceId);
        record.setBusinessId(businessId);
        record.setCommand(command);
        record.setMessageType(MqttMessageTypes.PROXY_COMMAND);
        record.setTopic(topic);
        record.setPayloadJson(payloadJson);
        record.setStatus(DeviceMqttCommand.STATUS_CREATED);
        record.setQos(properties.getQos());
        record.setRetryCount(0);
        record.setTimeoutAt(LocalDateTime.now().plusSeconds(properties.getCommandTimeoutSec()));
        deviceMqttCommandMapper.insert(record);
        recordEvent(record, DeviceMqttCommand.STATUS_CREATED, record);

        if (mqttClient == null || !mqttClient.isConnected()) {
            log.warn("[MQTT发布] MQTT客户端未连接，命令仅入库未发送 messageId={}", messageId);
            record.setStatus(DeviceMqttCommand.STATUS_FAILED);
            record.setErrorMessage("MQTT客户端未连接，命令未发送");
            deviceMqttCommandMapper.updateById(record);
            recordEvent(record, DeviceMqttCommand.STATUS_FAILED, record);
            return null;
        }

        try {
            MqttMessage mqttMessage = new MqttMessage(JSON.toJSONString(envelope).getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(properties.getQos());
            mqttClient.publish(topic, mqttMessage);

            record.setStatus(DeviceMqttCommand.STATUS_PUBLISHED);
            record.setPublishedAt(LocalDateTime.now());
            deviceMqttCommandMapper.updateById(record);
            recordEvent(record, DeviceMqttCommand.STATUS_PUBLISHED, record);
            log.info("[MQTT发布] 命令已发布 messageId={}, topic={}, qos={}", messageId, topic, properties.getQos());
            return record;
        } catch (Exception e) {
            log.error("[MQTT发布] 命令发布失败: messageId={}, topic={}", messageId, topic, e);
            record.setStatus(DeviceMqttCommand.STATUS_FAILED);
            record.setErrorMessage("MQTT发布失败: " + e.getMessage());
            deviceMqttCommandMapper.updateById(record);
            recordEvent(record, DeviceMqttCommand.STATUS_FAILED, record);
            return null;
        }
    }

    public DeviceMqttCommand waitForFinalStatus(Long commandId, int timeoutSec) {
        if (commandId == null) {
            return null;
        }
        long deadline = System.currentTimeMillis() + Math.max(1, timeoutSec) * 1000L;
        DeviceMqttCommand latest = null;
        while (System.currentTimeMillis() <= deadline) {
            latest = deviceMqttCommandMapper.selectById(commandId);
            if (latest != null && isFinalStatus(latest.getStatus())) {
                return latest;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[MQTT发布] 等待回执被中断 commandId={}", commandId);
                return latest;
            }
        }

        if (latest != null && !isFinalStatus(latest.getStatus())) {
            latest.setStatus(DeviceMqttCommand.STATUS_TIMEOUT);
            latest.setErrorMessage("等待MQTT回执超时");
            latest.setUpdateTime(LocalDateTime.now());
            deviceMqttCommandMapper.updateById(latest);
            recordEvent(latest, DeviceMqttCommand.STATUS_TIMEOUT, latest);
        }
        return latest;
    }

    public boolean isSuccess(DeviceMqttCommand command) {
        return command != null && DeviceMqttCommand.STATUS_SUCCESS.equals(command.getStatus());
    }

    @PreDestroy
    public void destroy() {
        if (mqttClient != null) {
            log.info("[MQTT发布] 销毁MQTT客户端");
            MqttClientFactory.disconnectQuietly(mqttClient);
            mqttClient = null;
        }
    }

    public MqttClient getMqttClient() {
        return mqttClient;
    }

    public boolean isMqttEnabled() {
        String mode = properties.getMode();
        return "mqtt".equalsIgnoreCase(mode) || "dual".equalsIgnoreCase(mode);
    }

    public void onMessage(String topic, MqttEnvelope envelope) {
        if (envelope == null || !hasText(envelope.getMessageType())) {
            log.warn("[MQTT发布] 收到空信封或无messageType的消息 topic={}", topic);
            return;
        }

        String messageType = envelope.getMessageType();
        String payloadStr = envelope.getPayload();
        try {
            if (MqttMessageTypes.REPLY.equalsIgnoreCase(messageType)) {
                MqttReplyMessage reply = JSON.parseObject(payloadStr, MqttReplyMessage.class);
                if (reply != null) {
                    mqttReplyHandler.handleReply(reply);
                }
            } else if (MqttMessageTypes.HEARTBEAT.equalsIgnoreCase(messageType)) {
                MqttHeartbeatMessage heartbeat = JSON.parseObject(payloadStr, MqttHeartbeatMessage.class);
                if (heartbeat != null) {
                    mqttHeartbeatHandler.handleHeartbeat(heartbeat);
                }
            } else if (MqttMessageTypes.REGISTER.equalsIgnoreCase(messageType)) {
                MqttDeviceRegisterMessage register = JSON.parseObject(payloadStr, MqttDeviceRegisterMessage.class);
                if (register != null && mqttRegisterHandler != null) {
                    mqttRegisterHandler.handleRegister(register);
                }
            } else {
                log.debug("[MQTT发布] 未处理的消息类型: messageType={}, topic={}", messageType, topic);
            }
        } catch (Exception e) {
            log.error("[MQTT发布] 处理消息失败: messageType={}, topic={}", messageType, topic, e);
        }
    }

    private void dispatchIncomingMessage(String topic, String payloadJson) {
        MqttEnvelope envelope = JSON.parseObject(payloadJson, MqttEnvelope.class);
        if (envelope != null && hasText(envelope.getMessageType())) {
            log.debug("[MQTT发布] 收到消息: topic={}, messageType={}, messageId={}",
                    topic, envelope.getMessageType(), envelope.getMessageId());
            onMessage(topic, envelope);
            return;
        }

        if (isReplyTopic(topic)) {
            MqttReplyMessage reply = JSON.parseObject(payloadJson, MqttReplyMessage.class);
            if (reply != null && hasText(reply.getCommandMessageId())) {
                mqttReplyHandler.handleReply(reply);
                return;
            }
        }

        if (isHeartbeatTopic(topic)) {
            MqttHeartbeatMessage heartbeat = JSON.parseObject(payloadJson, MqttHeartbeatMessage.class);
            if (heartbeat != null && hasText(heartbeat.getDeviceId())) {
                mqttHeartbeatHandler.handleHeartbeat(heartbeat);
                return;
            }
        }

        if (isRegisterTopic(topic)) {
            MqttDeviceRegisterMessage register = JSON.parseObject(payloadJson, MqttDeviceRegisterMessage.class);
            if (register != null && mqttRegisterHandler != null) {
                mqttRegisterHandler.handleRegister(register);
                return;
            }
        }

        log.warn("[MQTT发布] 收到空信封或无messageType的消息 topic={}", topic);
    }

    private boolean isReplyTopic(String topic) {
        return topic != null && topic.endsWith("/up/reply");
    }

    private boolean isHeartbeatTopic(String topic) {
        return topic != null && topic.endsWith("/up/heartbeat");
    }

    private boolean isRegisterTopic(String topic) {
        return topic != null && topic.endsWith("/up/register");
    }

    private boolean isFinalStatus(String status) {
        return DeviceMqttCommand.STATUS_SUCCESS.equals(status)
                || DeviceMqttCommand.STATUS_FAILED.equals(status)
                || DeviceMqttCommand.STATUS_TIMEOUT.equals(status)
                || DeviceMqttCommand.STATUS_CANCELED.equals(status);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void recordEvent(DeviceMqttCommand command, String status, Object payload) {
        if (mqttCommandEventService != null) {
            mqttCommandEventService.record(command, status, payload);
        }
    }
}

package com.publishgateway.udpproxy.mqtt;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.mqtt.core.dto.MqttEnvelope;
import com.monitorplatform.mqtt.core.dto.MqttReplyMessage;
import com.monitorplatform.mqtt.core.dto.MqttTopicBuilder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 回执发布器 —— 将命令执行结果以 REPLY 信封上行回报给管控平台。
 * <p>
 * 依赖 MqttConnectionManager 获取 MQTT 客户端。由于 MqttConnectionManager
 * 反向依赖命令分发链（GatewayCommandDispatcher → ReplyPublisher），这里使用
 * {@link Lazy} 注入代理，避免启动期循环依赖报错（Spring Boot 2.6+ 默认禁止循环引用）。
 * 回执发布发生在命令执行阶段，此时 MqttConnectionManager 已完成初始化。
 * </p>
 */
@Slf4j
@Component
public class ReplyPublisher {

    @Resource
    private MqttAgentProperties properties;

    @Lazy
    @Resource
    private MqttConnectionManager mqttConnectionManager;

    /**
     * 发布回执消息到上行 reply Topic。
     *
     * @param reply 回执消息体
     */
    public void publishReply(MqttReplyMessage reply) {
        if (reply == null) {
            log.warn("[MQTT-REPLY] 回执为空，跳过发布");
            return;
        }
        MqttClient client = mqttConnectionManager.getMqttClient();
        if (client == null || !client.isConnected()) {
            log.warn("[MQTT-REPLY] MQTT 客户端未连接，无法发布回执: status={}",
                    reply.getStatus());
            return;
        }

        String deviceId = properties.resolveDeviceId();

        // 构建上行信封
        MqttEnvelope envelope = new MqttEnvelope();
        envelope.setMessageId(UUID.randomUUID().toString());
        envelope.setMessageType("REPLY");
        envelope.setTenantId(properties.getTenantId());
        envelope.setSiteId(properties.getSiteId());
        envelope.setDeviceId(deviceId);
        envelope.setDeviceType(properties.getDeviceType());
        envelope.setTimestamp(System.currentTimeMillis());
        envelope.setPayload(JSON.toJSONString(reply));

        String topic = MqttTopicBuilder.upReply(
                properties.getTenantId(), properties.getSiteId(), deviceId);
        String envelopeJson = JSON.toJSONString(envelope);

        try {
            MqttMessage message = new MqttMessage(envelopeJson.getBytes(StandardCharsets.UTF_8));
            message.setQos(properties.getQos());
            client.publish(topic, message);
            log.info("[MQTT-REPLY] 已发布回执: topic={}, commandMessageId={}, status={}, qos={}",
                    topic, reply.getCommandMessageId(), reply.getStatus(), properties.getQos());
        } catch (MqttException e) {
            log.error("[MQTT-REPLY] 发布回执失败: topic={}, {}",
                    topic, e.getMessage(), e);
        }
    }
}

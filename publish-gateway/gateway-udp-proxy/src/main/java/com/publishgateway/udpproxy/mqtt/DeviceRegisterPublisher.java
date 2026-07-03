package com.publishgateway.udpproxy.mqtt;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.mqtt.core.dto.MqttDeviceRegisterMessage;
import com.monitorplatform.mqtt.core.dto.MqttEnvelope;
import com.monitorplatform.mqtt.core.dto.MqttMessageTypes;
import com.monitorplatform.mqtt.core.dto.MqttTopicBuilder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes device active registration to the platform through MQTT.
 */
@Slf4j
@Component
public class DeviceRegisterPublisher {

    @Resource
    private MqttAgentProperties properties;

    @Value("${server.port:8092}")
    private int serverPort;

    @Value("${registry.client.version:}")
    private String registryVersion;

    @Value("${registry.client.location:}")
    private String registryLocation;

    @Value("${registry.client.manufacturer:}")
    private String registryManufacturer;

    @Value("${registry.client.model:}")
    private String registryModel;

    public void publishRegister(MqttClient client, String reason) {
        if (!properties.isEnabled() || !properties.isRegistrationEnabled()) {
            return;
        }
        if (client == null || !client.isConnected()) {
            log.debug("[MQTT-REGISTER] client not connected, skip register");
            return;
        }

        String deviceId = properties.resolveDeviceId();
        MqttDeviceRegisterMessage register = new MqttDeviceRegisterMessage();
        register.setServiceName(firstText(properties.resolveText(properties.getServiceName()), properties.getDeviceType()));
        register.setInstanceId(deviceId);
        register.setDeviceId(deviceId);
        register.setDeviceType(properties.getDeviceType());
        register.setHost(resolveRegisterHost());
        register.setPort(resolveRegisterPort());
        register.setMacAddress(properties.resolveText(properties.getMacAddress()));
        register.setLocation(firstText(properties.resolveText(properties.getLocation()), registryLocation));
        register.setVersion(firstText(properties.resolveText(properties.getVersion()), registryVersion));
        register.setManufacturer(firstText(properties.resolveText(properties.getManufacturer()), registryManufacturer));
        register.setModel(firstText(properties.resolveText(properties.getModel()), registryModel));
        register.setRemark(properties.resolveText(properties.getRemark()));
        register.setTimestamp(System.currentTimeMillis());
        register.setMetadata(buildMetadata(reason));

        MqttEnvelope envelope = new MqttEnvelope();
        envelope.setMessageId(UUID.randomUUID().toString());
        envelope.setMessageType(MqttMessageTypes.REGISTER);
        envelope.setTenantId(properties.getTenantId());
        envelope.setSiteId(properties.getSiteId());
        envelope.setDeviceId(deviceId);
        envelope.setDeviceType(properties.getDeviceType());
        envelope.setTimestamp(System.currentTimeMillis());
        envelope.setPayload(JSON.toJSONString(register));

        String topic = MqttTopicBuilder.upRegister(properties.getTenantId(), properties.getSiteId(), deviceId);
        try {
            MqttMessage message = new MqttMessage(JSON.toJSONString(envelope).getBytes(StandardCharsets.UTF_8));
            message.setQos(properties.getQos());
            client.publish(topic, message);
            log.info("[MQTT-REGISTER] register published: topic={}, deviceId={}, host={}, port={}, reason={}",
                    topic, deviceId, register.getHost(), register.getPort(), reason);
        } catch (MqttException e) {
            log.error("[MQTT-REGISTER] publish register failed: topic={}, {}", topic, e.getMessage(), e);
        }
    }

    private Map<String, Object> buildMetadata(String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("clientId", properties.getClientId());
        metadata.put("reason", reason);
        metadata.put("innerIp", resolveRegisterHost());
        metadata.put("serverPort", resolveRegisterPort());
        return metadata;
    }

    private String resolveRegisterHost() {
        String configured = properties.resolveText(properties.getRegisterHost());
        if (hasText(configured)) {
            return configured;
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private Integer resolveRegisterPort() {
        return properties.getRegisterPort() == null ? serverPort : properties.getRegisterPort();
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : (hasText(second) ? second.trim() : null);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

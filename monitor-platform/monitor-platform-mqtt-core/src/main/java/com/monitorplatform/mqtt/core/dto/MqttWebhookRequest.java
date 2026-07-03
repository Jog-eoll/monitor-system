package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * MQTT broker lifecycle webhook request.
 */
@Data
public class MqttWebhookRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String event;

    private String clientId;

    private String clientid;

    private String username;

    private String ipaddress;

    private String peerhost;

    private String reason;

    private Long connectedAt;

    private Long connected_at;

    private Map<String, Object> raw;

    public String normalizedClientId() {
        return hasText(clientId) ? clientId : clientid;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

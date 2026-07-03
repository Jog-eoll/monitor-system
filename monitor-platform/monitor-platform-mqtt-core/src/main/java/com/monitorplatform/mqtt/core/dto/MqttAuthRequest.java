package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * HTTP auth request sent by EMQX or another MQTT broker webhook.
 */
@Data
public class MqttAuthRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String clientId;

    private String clientid;

    private String username;

    private String password;

    private String ipaddress;

    private String peerhost;

    public String normalizedClientId() {
        return hasText(clientId) ? clientId : clientid;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

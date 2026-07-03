package com.monitorplatform.mqtt.core.dto;

/**
 * MQTT envelope messageType constants shared by platform and device agents.
 */
public final class MqttMessageTypes {

    private MqttMessageTypes() {
    }

    public static final String PROXY_COMMAND = "PROXY_COMMAND";
    public static final String COMMAND = "COMMAND";
    public static final String CONFIG = "CONFIG";
    public static final String PROPERTY_SET = "PROPERTY_SET";
    public static final String UPGRADE = "UPGRADE";
    public static final String CERT = "CERT";
    public static final String MODEL = "MODEL";
    public static final String REPLY = "REPLY";
    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String REGISTER = "REGISTER";
    public static final String DISCOVERY = "DISCOVERY";
    public static final String PROPERTY = "PROPERTY";
    public static final String EVENT = "EVENT";
}

package com.monitorplatform.mqtt.core.dto;

/**
 * MQTT Topic 构建器 —— 统一管理上行/下行 Topic 格式。
 * <p>
 * Topic 格式：/{tenantId}/{siteId}/{deviceId}/{direction}/{messageType}
 * <ul>
 *   <li>direction: up（设备→平台）或 down（平台→设备）</li>
 *   <li>messageType: proxy-command / reply / heartbeat / discovery</li>
 * </ul>
 * 平台订阅上行通配：/+/+/+/up/#</br>
 * 平台下发：/{tenantId}/{siteId}/{deviceId}/down/{messageType}
 * </p>
 */
public final class MqttTopicBuilder {

    private MqttTopicBuilder() {
    }

    /**
     * 下行命令 Topic。
     * <p>例：/default/site-001/GW-001/down/proxy-command</p>
     */
    public static String downCommand(String tenantId, String siteId, String deviceId) {
        return "/" + tenantId + "/" + siteId + "/" + deviceId + "/down/proxy-command";
    }

    /**
     * 标准下行命令 Topic。新设备优先订阅该 Topic，旧 Topic 继续保留兼容。
     */
    public static String downCommandV2(String tenantId, String siteId, String deviceId) {
        return build(tenantId, siteId, deviceId, "down", "command");
    }

    public static String downConfig(String tenantId, String siteId, String deviceId) {
        return build(tenantId, siteId, deviceId, "down", "config");
    }

    public static String downPropertySet(String tenantId, String siteId, String deviceId) {
        return build(tenantId, siteId, deviceId, "down", "property-set");
    }

    public static String downUpgrade(String tenantId, String siteId, String deviceId) {
        return build(tenantId, siteId, deviceId, "down", "upgrade");
    }

    public static String downCert(String tenantId, String siteId, String deviceId) {
        return build(tenantId, siteId, deviceId, "down", "cert");
    }

    public static String downModel(String tenantId, String siteId, String deviceId) {
        return build(tenantId, siteId, deviceId, "down", "model");
    }

    /**
     * 上行回执 Topic。
     */
    public static String upReply(String tenantId, String siteId, String deviceId) {
        return "/" + tenantId + "/" + siteId + "/" + deviceId + "/up/reply";
    }

    /**
     * 上行心跳 Topic。
     */
    public static String upHeartbeat(String tenantId, String siteId, String deviceId) {
        return "/" + tenantId + "/" + siteId + "/" + deviceId + "/up/heartbeat";
    }

    public static String upRegister(String tenantId, String siteId, String deviceId) {
        return build(tenantId, siteId, deviceId, "up", "register");
    }

    public static String upProperty(String tenantId, String siteId, String deviceId) {
        return build(tenantId, siteId, deviceId, "up", "property");
    }

    public static String upEvent(String tenantId, String siteId, String deviceId) {
        return build(tenantId, siteId, deviceId, "up", "event");
    }

    /**
     * 上行发现 Topic。
     */
    public static String upDiscovery(String tenantId, String siteId, String deviceId) {
        return "/" + tenantId + "/" + siteId + "/" + deviceId + "/up/discovery";
    }

    /**
     * 平台订阅上行通配 Topic。
     */
    public static String upSubscribeAll() {
        return "/+/+/+/up/#";
    }

    public static String downSubscribeAll(String tenantId, String siteId, String deviceId) {
        return "/" + tenantId + "/" + siteId + "/" + deviceId + "/down/#";
    }

    public static boolean isTopicSuffix(String topic, String suffix) {
        return topic != null && suffix != null && topic.endsWith(suffix);
    }

    private static String build(String tenantId, String siteId, String deviceId, String direction, String messageType) {
        return "/" + tenantId + "/" + siteId + "/" + deviceId + "/" + direction + "/" + messageType;
    }
}

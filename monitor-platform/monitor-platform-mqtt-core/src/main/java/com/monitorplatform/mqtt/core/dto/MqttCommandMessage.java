package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 下发命令消息 —— 平台通过 MQTT 下发给现场网关的 PROXY_COMMAND。
 * <p>
 * 网关收到后按 actions 列表逐条执行：SELF_APPLY 直接本地处理，
 * HTTP 转发到局域网内其它设备的现有 REST 接口。
 * </p>
 */
@Data
public class MqttCommandMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SCHEMA_VERSION = "1.0";
    public static final String COMMAND_QUERY_STATUS = "QUERY_STATUS";
    public static final String COMMAND_NOOP = "NOOP";
    public static final String COMMAND_ECHO = "ECHO";

    private String schemaVersion = SCHEMA_VERSION;

    private String businessId;

    /** 命令名称（枚举）：APPLY_CHAIN_CONFIG / STOP_CHAIN / SET_CHAIN_STATUS / ... */
    private String command;

    /** 命令参数（与现有 /udp-proxy/config DTO 兼容） */
    private Map<String, Object> payload;

    /** 动作列表（网关按顺序执行） */
    private List<Action> actions;

    /** QoS 级别（0/1/2），默认 1 */
    private Integer qos;

    /** 超时时间（epoch millis） */
    private Long timeoutAt;

    @Data
    public static class Action implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 目标设备类型：publish_gateway / terminal_encrypt_gateway / publish_server / info_board */
        private String targetDeviceType;

        /** 执行模式：SELF_APPLY（网关自身处理）/ HTTP（转发到局域网设备） */
        private String mode;

        /** HTTP 转发路径（mode=HTTP 时必填），如 /udp-proxy/config */
        private String path;

        /** HTTP 转发方法：POST / PUT / DELETE，默认 POST */
        private String httpMethod;

        /** HTTP 转发目标 IP（mode=HTTP 时必填） */
        private String targetIp;

        /** HTTP 转发目标端口（mode=HTTP 时必填） */
        private Integer targetPort;

        /** 转发请求体（JSON 对象） */
        private Map<String, Object> body;

        /** 目标设备 ID（用于查询设备台账获取 IP:Port） */
        private String targetDeviceId;
    }
}

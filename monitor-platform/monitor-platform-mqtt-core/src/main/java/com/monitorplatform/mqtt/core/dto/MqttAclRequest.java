package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * EMQX 5 HTTP ACL 回调请求体 —— 平台侧 /mqtt/acl 端点的入参。
 * <p>
 * EMQX 在客户端发布/订阅时触发此回调，平台根据 clientId/username/topic/action
 * 判定是否放行。
 * </p>
 */
@Data
public class MqttAclRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客户端 ID */
    private String clientId;

    /** 兼容 EMQX 某些版本全写字段 */
    private String clientid;

    /** 用户名 */
    private String username;

    /** 发布= publish，订阅= subscribe */
    private String action;

    /** 发布/订阅的目标 topic */
    private String topic;

    /** 客户端 IP */
    private String ipaddress;

    /** 客户端主机 */
    private String peerhost;

    /** 订阅时的 QoS（仅 action=subscribe 时有效） */
    private Integer mountpoint;

    public String normalizedClientId() {
        return hasText(clientId) ? clientId : clientid;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

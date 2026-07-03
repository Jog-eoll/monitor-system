package com.monitorplatform.mqtt.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * /mqtt/acl 端点响应体 —— 直接返回给 EMQX HTTP ACL 回调的 JSON。
 * <p>
 * <ul>
 *   <li>放行：HTTP 204 或 200 + body</li>
 *   <li>拒绝：HTTP 401/403</li>
 * </ul>
 * 该类仅作为Acl决策结果的内存表示，Controller 根据 isAllowed 决定返回状态码。
 * </p>
 */
@Data
public class MqttAclResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean isAllowed;

    /** 平台分配的租户 ID（设备校验时关联） */
    private String tenantId;

    /** 平台分配的站点 ID */
    private String siteId;

    /** 设备 ID（从 clientId 解析得到） */
    private String deviceId;

    /** 拒绝原因（仅用于日志记录，不返回给设备） */
    private String reason;

    public static MqttAclResponse allow(String tenantId, String siteId, String deviceId) {
        MqttAclResponse r = new MqttAclResponse();
        r.isAllowed = true;
        r.tenantId = tenantId;
        r.siteId = siteId;
        r.deviceId = deviceId;
        return r;
    }

    public static MqttAclResponse deny(String reason) {
        MqttAclResponse r = new MqttAclResponse();
        r.isAllowed = false;
        r.reason = reason;
        return r;
    }

    public Map<String, Object> toResultMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("result", isAllowed ? "allow" : "deny");
        if (isAllowed) {
            if (tenantId != null) { result.put("tenantId", tenantId); }
            if (siteId != null) { result.put("siteId", siteId); }
            if (deviceId != null) { result.put("deviceId", deviceId); }
        } else if (reason != null) {
            result.put("reason", reason);
        }
        return result;
    }
}

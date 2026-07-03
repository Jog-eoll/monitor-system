package com.publishgateway.udpproxy.entity.dto.secure;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 加密网关统一出站信封 DTO
 * <p>
 * 发布包结构：
 * <pre>
 * {
 *   "schemaVersion": "1.0",
 *   "messageType": "PUBLISH",
 *   "requestId": "...",
 *   "deliveryTaskId": "...",
 *   "source": { "gatewayId": "publish-gateway", ... },
 *   "target": { "deviceId", "ip", "port", "vendorHint" },
 *   "publish": { ... }
 * }
 * </pre>
 * 控制包结构：
 * <pre>
 * {
 *   "schemaVersion": "1.0",
 *   "messageType": "CONTROL",
 *   "requestId": "...",
 *   "commandTaskId": "...",
 *   "source": { "gatewayId": "publish-gateway", ... },
 *   "target": { "deviceId", "ip", "port", "vendorHint" },
 *   "control": { ... }
 * }
 * </pre>
 * requestId、deliveryTaskId/commandTaskId 必须同时写入 JSON 信封和 HTTP header。
 * </p>
 */
@Data
public class SecureGatewayEnvelope implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 信封模式版本 */
    private String schemaVersion;

    /** 消息类型：PUBLISH / CONTROL */
    private String messageType;

    /** 请求 ID，用于幂等和链路追踪 */
    private String requestId;

    /** 发布任务 ID（messageType=PUBLISH 时使用） */
    private String deliveryTaskId;

    /** 控制任务 ID（messageType=CONTROL 时使用） */
    private String commandTaskId;

    /** 来源信息 */
    private SourceRef source;

    /** 目标设备 */
    private TargetRef target;

    /** 发布载荷（messageType=PUBLISH 时使用） */
    private Map<String, Object> publish;

    /** 控制载荷（messageType=CONTROL 时使用） */
    private Map<String, Object> control;

    @Data
    public static class SourceRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String gatewayId;
        private String clientId;
    }

    @Data
    public static class TargetRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        private String vendorHint;
    }
}

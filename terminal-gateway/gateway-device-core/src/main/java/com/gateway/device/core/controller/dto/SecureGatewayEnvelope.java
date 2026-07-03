package com.gateway.device.core.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 统一信封 DTO —— 加密网关与解密网关之间 JSON 通信的外层包装。
 * <p>
 * 明文 JSON 在加密前使用本信封结构，{@link #publish} 与 {@link #control}
 * 二选一，由 {@link #messageType} 决定。旧版无 {@code schemaVersion}/{@code messageType}
 * 的扁平 JSON 仍按 {@link StandardizedPublishPackage} / {@link StandardizedControlPackage}
 * 解析，保证向后兼容。
 * </p>
 *
 * <p>该 DTO 是 {@code gateway-device-core} 的本地镜像，不与 protocol 模块产生依赖。
 * 发布网关侧构造的 JSON 字段名必须与本类一一对应。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecureGatewayEnvelope implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 信封版本，当前 {@code 1.0} */
    private String schemaVersion;

    /** 消息类型：PUBLISH / CONTROL */
    private String messageType;

    /** 请求 ID（幂等键，发布网关生成） */
    private String requestId;

    /** 发布任务投递 ID（messageType=PUBLISH 时使用） */
    private String deliveryTaskId;

    /** 控制任务 ID（messageType=CONTROL 时使用） */
    private String commandTaskId;

    /** 来源信息 */
    private SourceRef source;

    /** 目标设备 */
    private TargetRef target;

    /** 发布包内容（messageType=PUBLISH 时使用） */
    private PublishPayload publish;

    /** 控制包内容（messageType=CONTROL 时使用） */
    private ControlPayload control;

    /** 兜底字段：兼容旧版扁平 JSON 时把 publishPermit 提升到信封顶层 */
    private String publishPermit;

    // ──────────────── 透传/扩展 ────────────────

    /** 可选扩展字段（透传，不参与业务校验） */
    private Map<String, Object> extensions;

    public boolean hasEnvelopeMarker() {
        return (schemaVersion != null && !schemaVersion.isEmpty())
                || (messageType != null && !messageType.isEmpty());
    }

    public boolean isPublish() {
        return messageType != null && "PUBLISH".equalsIgnoreCase(messageType.trim());
    }

    public boolean isControl() {
        return messageType != null && "CONTROL".equalsIgnoreCase(messageType.trim());
    }

    // ──────────────── 内部类 ────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SourceRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String gatewayId;
        private String clientId;
        private String clientIp;
        private String hostName;
        private String operatorId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TargetRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        /** 可选厂商提示；解密网关优先使用设备注册信息，vendorHint 作为兜底 */
        private String vendorHint;
    }

    /**
     * 发布包载荷 —— 字段名与 {@link StandardizedPublishPackage} 对齐，
     * 兼容把扁平发布包内联到信封内的场景。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PublishPayload implements Serializable {
        private static final long serialVersionUID = 1L;
        private String action;
        private StandardizedPublishPackage.PlaylistRef playlist;
        private java.util.List<StandardizedPublishPackage.FileEntry> files;
        private StandardizedPublishPackage.PublishOptions options;
        private String sigmaPublishId;
        /** 兼容旧版：把 publishPermit 放在 publish 内层 */
        private String publishPermit;
    }

    /**
     * 控制包载荷 —— 字段名与 {@link StandardizedControlPackage} 对齐。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ControlPayload implements Serializable {
        private static final long serialVersionUID = 1L;
        private String action;
        private String command;
        private Map<String, Object> params;
    }
}

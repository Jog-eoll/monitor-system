package com.gateway.standardization.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 标准化消息体
 * <p>
 * 对应 Sigma 对接文档 Section 3.1 中加密端需要补齐的标准化字段。
 * 本类用于封装上报到管控平台（monitor-content）的结构化数据，
 * 也可作为后续加密转发到 terminal-gateway 的消息模型。
 * </p>
 */
@Data
public class StandardizedMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========== 文档要求的必填字段 ==========

    /** 发布会话 ID，由 precheck 通过后创建 */
    private String sessionId;

    /** 本次发布请求 ID */
    private String requestId;

    /** 加密端接收到的 Sigma 包序号，用于排序和排查 */
    private Integer sequenceNo;

    /** 消息类型: COMMAND / CONTENT / CONTENT_FRAGMENT / PLAYLIST / RAW_PACKET */
    private String messageType;

    /** 目标设备信息 */
    private TargetInfo target;

    /**
     * 业务动作（对外契约字段）
     * <p>
     * 中文动作名称，如"设置播放列表"、"调节亮度"、"黑屏"等。
     * Sigma 团队和管控平台只需理解此字段，无需关心内部 capability。
     * 解密网关通过 ActionMapper 将 action 翻译为内部 DeviceCapability，
     * 再根据目标设备厂商路由到青松、卡莱特等厂商适配器指令。
     * </p>
     */
    private String action;

    /**
     * 内部能力标识（补充字段，非对外契约）
     * <p>
     * 对应解密网关 DeviceCapability 枚举名称，仅供网关内部排查使用。
     * 外部对接方（Sigma）无需关注此字段。
     * </p>
     */
    private String capability;

    // ========== 文档要求的可选字段 ==========

    /** 指令信息（主命令、子命令、是否需要 ACK） */
    private CommandInfo command;

    /** 内容信息（文件名、文件类型、内容引用） */
    private ContentInfo content;

    /** Sigma 原始包兜底字段（Base64） */
    private String rawPacketBase64;

    // ========== 网关侧补充字段（不影响文档契约） ==========

    /** 发布网关标识 */
    private String gatewayId;

    /** 链路 ID */
    private String chainId;

    /** 来源 IP */
    private String sourceIp;

    /** 协议类型（原始协议标识，如 JetFileII-Type1, Sigma-FileTransfer） */
    private String protocol;

    /** 内容类型（text, image, video, binary） */
    private String contentType;

    // ========== 内部结构化子类 ==========

    /**
     * 指令信息
     */
    @Data
    public static class CommandInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 主命令 */
        private String mainCommand;

        /** 子命令 */
        private String subCommand;

        /** 是否需要 ACK */
        private boolean ackRequired;
    }

    /**
     * 内容信息
     */
    @Data
    public static class ContentInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 文件名 */
        private String fileName;

        /** 文件类型/扩展名 */
        private String fileType;

        /** 内容数据（文本或小文件的 Base64） */
        private String data;

        /** MinIO 对象存储路径（大文件优先使用此引用） */
        private String minioPath;

        /** 图片格式（jpeg, png, gif, bmp 等） */
        private String imageFormat;
    }
}

package com.gateway.device.core.controller.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 标准化发布包 —— 加密网关投递到解密网关的标准信封。
 * <p>
 * 与 publish-gateway 中 {@code com.gateway.standardization.dto.StandardizedPublishPackage} 对齐。
 * 解密网关本地维护一份镜像 DTO，避免跨模块依赖。
 * </p>
 */
@Data
public class StandardizedPublishPackage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递任务 ID */
    private String deliveryTaskId;

    /** Sigma 发布 ID */
    private String sigmaPublishId;

    /** 动作类型，固定值 PUBLISH_PLAYLIST */
    private String action;

    /** 目标设备 */
    private TargetRef target;

    /** 播放列表引用 */
    private PlaylistRef playlist;

    /** 文件列表（v1: Base64 内嵌） */
    private List<FileEntry> files;

    /** 发布选项 */
    private PublishOptions options;

    /** 客户端签发的 JWT publishPermit（透传） */
    private String publishPermit;

    // ──────────────── 内部类 ────────────────

    @Data
    public static class TargetRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        /** 可选厂商提示；解密网关优先使用设备注册信息，vendorHint 作为兜底 */
        private String vendorHint;
    }

    @Data
    public static class PlaylistRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String playlistId;
        /** SHA-256 十六进制摘要 */
        private String digest;
    }

    @Data
    public static class FileEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer orderNo;
        private String fileName;
        /** 文件类型标识，如 IMAGE / VIDEO / TEXT / NMG / PMG / QST */
        private String fileType;
        /** v1 模式: Base64 编码的文件内容 */
        private String contentBase64;
        private Integer durationSeconds;
        /** 可选 SHA-256 哈希 */
        private String fileHash;
    }

    @Data
    public static class PublishOptions implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 发布前是否清除播放列表和文件，默认 true */
        private boolean clearBeforePublish = true;
        /** 上传后是否校验文件存在性，默认 true */
        private boolean checkExistence = true;
    }
}

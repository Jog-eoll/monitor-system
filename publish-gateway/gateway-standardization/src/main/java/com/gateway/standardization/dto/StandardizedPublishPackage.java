package com.gateway.standardization.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 标准化发布包
 * <p>
 * 加密网关与解密网关之间传输的标准化发布数据。
 * 不再塞入单条 {@link StandardizedMessage}，而是作为独立的批量传输单元。
 * </p>
 *
 * <p>v1 文件传输方式：文件内容以 Base64 编码嵌入 {@code files[*].contentBase64}。</p>
 */
@Data
public class StandardizedPublishPackage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递任务 ID */
    private String deliveryTaskId;

    /** Sigma 发布 ID */
    private String sigmaPublishId;

    /** 动作类型: PUBLISH_PLAYLIST */
    private String action;

    /** 目标设备 */
    private TargetRef target;

    /** 播放列表信息 */
    private PlaylistRef playlist;

    /** 文件列表 */
    private List<FileEntry> files;

    /** 发布选项 */
    private PublishOptions options;

    /** publishPermit JWT（由客户端签发，加密网关透传） */
    private String publishPermit;

    @Data
    public static class TargetRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        /** 厂商标识提示（可选，解密网关优先使用设备注册信息） */
        private String vendorHint;
    }

    @Data
    public static class PlaylistRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String playlistId;
        /** playlistDigest SHA-256 hex */
        private String digest;
    }

    @Data
    public static class FileEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer orderNo;
        private String fileName;
        private String fileType;
        /** 文件内容 Base64 编码 */
        private String contentBase64;
        private Integer durationSeconds;
        /** 文件 SHA-256 摘要（可选） */
        private String fileHash;
    }

    @Data
    public static class PublishOptions implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 发布前是否清空播放列表和文件 */
        private boolean clearBeforePublish = true;
        /** 上传完成后是否校验文件存在 */
        private boolean checkExistence = true;
    }
}

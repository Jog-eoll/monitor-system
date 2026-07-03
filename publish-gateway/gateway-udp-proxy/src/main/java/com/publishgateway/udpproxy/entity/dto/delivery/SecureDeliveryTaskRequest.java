package com.publishgateway.udpproxy.entity.dto.delivery;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 安全投递任务请求
 * <p>
 * 对应 POST /api/secure-delivery/tasks。
 * Sigma（或客户端 verify 通过后）将加密包 + publishPermit + 播放列表信息提交给加密网关。
 * </p>
 */
@Data
public class SecureDeliveryTaskRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发布请求 ID，用于链路追踪并透传到解密网关 */
    private String requestId;

    /** 加密任务包 ID，由 /api/crypto/publish-task/encrypt 返回 */
    private String encryptedPackageId;

    /** Base64 编码的加密任务包；存在时服务端优先解密该包并兼容明文字段 */
    private String encryptedPackage;

    /** Sigma 发布 ID */
    private String sigmaPublishId;

    /** publishPermit JWT */
    private String publishPermit;

    /** 目标设备 */
    private TargetRef target;

    /** 播放列表信息 */
    private PlaylistInfo playlist;

    /** 文件 URL 列表（加密网关负责下载） */
    private List<FileRef> files;

    /** 发布选项 */
    private Options options;

    @Data
    public static class TargetRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        private String vendorHint;
    }

    @Data
    public static class PlaylistInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String playlistId;
        private String digest;
    }

    @Data
    public static class FileRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer orderNo;
        private String fileName;
        private String fileType;
        /** 文件下载 URL */
        private String fileUrl;
        private Integer durationSeconds;
        /** 文件 SHA-256（可选，用于下载完整性校验） */
        private String fileHash;
    }

    @Data
    public static class Options implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean clearBeforePublish = true;
        private boolean checkExistence = true;
    }
}

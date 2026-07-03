package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;

/**
 * 发布许可签发请求
 * <p>
 * 对应 POST /api/client/publish/verify。
 * 新版对接已合并到 precheck，本请求仅作为旧版兼容入口使用。
 * 客户端根据完整播放列表信息生成 playlistDigest 并签发 publishPermit (JWT)。
 * </p>
 */
@Data
public class SigmaVerifyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发布请求 ID（与 precheck 阶段一致，用于关联） */
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    /** precheck 阶段返回的 precheckId */
    @NotBlank(message = "precheckId 不能为空")
    private String precheckId;

    /** 信发平台播放列表 ID */
    @NotBlank(message = "playlistId 不能为空")
    private String playlistId;

    /** 目标设备信息 */
    private TargetRef target;

    /** 播放列表项 */
    private List<PlaylistItem> items;

    /** 操作人 ID */
    private String operatorId;

    @Data
    public static class TargetRef implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deviceId;
        private String ip;
        private Integer port;
        private String vendorHint;
    }

    @Data
    public static class PlaylistItem implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 排序号 */
        private Integer orderNo;
        /** 文件名 */
        private String fileName;
        /** 文件类型（image/video/text/nmg/pmg/qst） */
        private String fileType;
        /** 文件下载 URL */
        private String fileUrl;
        /** 播放时长（秒） */
        private Integer durationSeconds;
        /** 文件 SHA-256 摘要（用于下载完整性校验，可选） */
        private String fileHash;
    }
}

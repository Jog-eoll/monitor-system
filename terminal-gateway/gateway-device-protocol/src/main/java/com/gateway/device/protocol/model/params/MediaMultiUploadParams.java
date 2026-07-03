package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.common.file.MediaFileEntry;
import com.gateway.device.protocol.model.params.depend.DimensionedParams;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 混合媒体上传参数 — MEDIA_MULTI_UPLOAD。
 *
 * <p>每个媒体文件对应一个独立容器和独立 zOrder，按 {@link MediaFileEntry} #order 升序排列。
 * zOrder 为顺序编号（1,2,3…），不使用 order 值。</p>
 */
@Data
@Builder
public class MediaMultiUploadParams implements DimensionedParams {

    /**
     * 媒体文件列表
     */
    private List<MediaFileEntry> mediaFiles;

    /**
     * 屏宽（可选回退到设备宽）
     */
    private Integer width;

    /**
     * 屏高（可选回退到设备高）
     */
    private Integer height;
}

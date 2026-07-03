package com.gateway.device.protocol.common.file;

import com.gateway.device.protocol.common.constant.MediaType;
import lombok.Builder;
import lombok.Data;

/**
 * 单个媒体文件条目 — MEDIA_MULTI_UPLOAD 的最小数据单元。
 *
 * <p>每个条目对应一个独立容器和独立 zOrder。按 {@code order} 升序排列后
 * 依次分配 zOrder=1,2,3…</p>
 */
@Data
@Builder
public class MediaFileEntry {

    /**
     * 排序优先级（值越小越靠前），默认 0。同一请求内不得重复。
     */
    @Builder.Default
    private int order = 0;

    /**
     * 文件二进制内容（必填）
     */
    private byte[] data;

    /**
     * 文件名含扩展名，如 {@code image.jpg}
     */
    private String fileName;

    /**
     * 媒体类型
     */
    private MediaType mediaType;

    /**
     * 播放时长(ms)，默认 10000
     */
    @Builder.Default
    private Integer duration = 10000;
}

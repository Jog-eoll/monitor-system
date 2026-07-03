package com.gateway.device.protocol.base.novastar.viplexcore.builder;

import com.gateway.device.protocol.common.constant.MediaType;
import lombok.Builder;
import lombok.Data;

/**
 * 通用媒体文件信息 — 替代 {@link ImageFileInfo} 仅支持图片的限制。
 *
 * <p>供 {@link ViplexMediaPageBuilder} 和 {@link ViplexProgramPipeline}
 * 之间传递，支持 PICTURE 和 VIDEO 两种媒体类型。</p>
 */
@Data
@Builder
public final class MediaFileInfo {
    private final String filePath;
    private final String md5;
    private final String fileName;
    private final MediaType mediaType;
    private final int fileSize;
    private final int duration;       // 播放时长(ms)
}

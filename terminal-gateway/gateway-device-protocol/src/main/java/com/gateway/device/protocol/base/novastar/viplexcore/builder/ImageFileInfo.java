package com.gateway.device.protocol.base.novastar.viplexcore.builder;

import lombok.Builder;
import lombok.Data;

/**
 * 单个图片文件信息 — 多图页面的最小数据单元。
 *
 * <p>替代散装的 (filePath, md5, fileName, fileSize) 四元组，
 * 用于 {@link ViplexImagePageBuilder} 和 {@link ViplexProgramPipeline} 之间传递图片信息。</p>
 */
@Data
@Builder
public final class ImageFileInfo {
    private final String filePath;
    private final String md5;
    private final String fileName;
    private final int fileSize;
}

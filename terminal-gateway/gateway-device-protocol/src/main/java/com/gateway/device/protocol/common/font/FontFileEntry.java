package com.gateway.device.protocol.common.font;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * 字体文件扫描条目 —— 封装单次扫描的完整信息。
 *
 * <p>{@link FontFileScanner#scan(Path, FontFileType...)} 返回此对象的列表，
 * 包含文件完整路径、文件名和匹配到的字体类型。调用方无需再从 Path 二次提取文件名，
 * 也可直接用 {@link #fullPath} 读取文件内容。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FontFileEntry {

    /**
     * 文件完整路径（可直接传给 {@link java.nio.file.Files#readAllBytes(Path)}）
     */
    private Path fullPath;

    /**
     * 文件名（含扩展名），取自 {@code fullPath.getFileName().toString()}
     */
    private String fileName;

    /**
     * 匹配到的字体类型
     */
    private FontFileType type;

    /**
     * 创建扫描条目。
     *
     * @param fullPath 文件完整路径
     * @param type     匹配到的字体文件类型
     */
    public static FontFileEntry of(Path fullPath, FontFileType type) {
        return FontFileEntry.builder()
                .fullPath(fullPath)
                .fileName(fullPath.getFileName().toString())
                .type(type)
                .build();
    }
}

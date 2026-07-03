package com.gateway.device.protocol.common.font;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 字体文件递归扫描器 —— 协议无关的公共组件。
 *
 * <p>递归扫描指定目录下所有匹配的字体文件，按 {@link FontFileType} 过滤，
 * 返回包含完整路径和文件名的 {@link FontFileEntry} 列表。
 * 扩展名匹配大小写无关（.fnt / .FNT / .ttf / .TTF 等）。</p>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * // JetFileII: 扫描 .fnt 文件
 * List<FontFileEntry> fntFiles = FontFileScanner.scan(Paths.get("./fonts"), FontFileType.FNT);
 *
 * // NovaStar: 扫描 .ttf 文件
 * List<FontFileEntry> ttfFiles = FontFileScanner.scan(Paths.get("C:\\Windows\\Fonts"), FontFileType.TTF);
 *
 * // 多类型扫描
 * List<FontFileEntry> all = FontFileScanner.scan(dir, FontFileType.FNT, FontFileType.TTF);
 * }</pre>
 */
@Slf4j
public final class FontFileScanner {

    private FontFileScanner() {
    }

    /**
     * 递归扫描目录，返回所有匹配字体文件的扫描条目。
     *
     * @param dir   字库根目录
     * @param types 要匹配的字体类型（至少一个）
     * @return 匹配的 {@link FontFileEntry} 列表（无序）；目录不存在或非目录时返回空列表
     */
    public static List<FontFileEntry> scan(Path dir, FontFileType... types) {
        if (types == null || types.length == 0) return Collections.emptyList();
        Set<FontFileType> typeSet = EnumSet.copyOf(Arrays.asList(types));

        if (!Files.isDirectory(dir)) {
            log.warn("字库目录不存在或不是目录: {}", dir);
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> {
                        FontFileType matched = matchType(p.getFileName().toString(), typeSet);
                        return matched != null ? FontFileEntry.of(p, matched) : null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描字库目录失败: {}", dir, e);
            return Collections.emptyList();
        }
    }

    // ── 内部 ──

    /**
     * 判断文件名是否匹配给定类型集合，返回匹配到的第一个类型。
     *
     * @param fileName 文件名
     * @param types    字体类型集合
     * @return 匹配的 FontFileType，未匹配返回 null
     */
    private static FontFileType matchType(String fileName, Set<FontFileType> types) {
        for (FontFileType t : types) {
            if (t.matches(fileName)) return t;
        }
        return null;
    }
}

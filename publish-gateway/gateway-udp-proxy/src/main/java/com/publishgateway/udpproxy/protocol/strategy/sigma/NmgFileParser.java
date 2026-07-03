package com.publishgateway.udpproxy.protocol.strategy.sigma;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sigma .Nmg 播放列表文件解析器
 *
 * .Nmg 文件是 Sigma Play 的播放列表格式，文本内容中包含对各文件的路径引用，
 * 格式类似：D:\T\bao4.jpg、D:\FILESLTI.SYS 等。
 *
 * 支持两种输入：
 *   1. 原始文本内容（直接解析）
 *   2. Base64 编码内容（先解码再解析）
 */
@Slf4j
public class NmgFileParser {

    /**
     * 匹配 Windows 文件路径：盘符(A-Z) + 冒号 + 反斜杠 + 路径字符
     * 路径字符允许：字母、数字、中文、空格、下划线、连字符、点、反斜杠
     */
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "[A-Za-z]:\\\\[^\\x00-\\x1F\\x7F\"<>|?*]+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 从 .Nmg 文件的缓存 data 字段中提取所有引用的文件路径
     *
     * @param nmgData .Nmg 文件内容（文本或 Base64 编码）
     * @return 提取到的文件路径列表，不含 .Nmg 自身路径
     */
    public static List<String> extractFilePaths(String nmgData) {
        List<String> paths = new ArrayList<>();
        if (nmgData == null || nmgData.isEmpty()) {
            return paths;
        }

        // 尝试解析原始文本
        extractFromText(nmgData, paths);

        // 如果原始文本没找到，尝试 Base64 解码后再解析
        if (paths.isEmpty()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(nmgData.trim());
                String decodedText = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                extractFromText(decodedText, paths);
                if (paths.isEmpty()) {
                    // 尝试 GBK 编码（Sigma 软件使用 Windows GBK）
                    decodedText = new String(decoded, "GBK");
                    extractFromText(decodedText, paths);
                }
            } catch (Exception e) {
                log.debug("【Nmg解析】Base64解码尝试失败（内容可能本就是明文）: {}", e.getMessage());
            }
        }

        log.debug("【Nmg解析】从 .Nmg 内容中提取到 {} 个文件路径: {}", paths.size(), paths);
        return paths;
    }

    /**
     * 从文本字符串中扫描所有 Windows 文件路径
     */
    private static void extractFromText(String text, List<String> result) {
        Matcher matcher = FILE_PATH_PATTERN.matcher(text);
        while (matcher.find()) {
            String path = matcher.group().trim();
            // 过滤极短路径（盘符+冒号+斜杠+文件名，至少要有扩展名）
            if (path.length() > 5 && !result.contains(path)) {
                result.add(path);
                log.debug("【Nmg解析】找到文件路径: {}", path);
            }
        }
    }
}

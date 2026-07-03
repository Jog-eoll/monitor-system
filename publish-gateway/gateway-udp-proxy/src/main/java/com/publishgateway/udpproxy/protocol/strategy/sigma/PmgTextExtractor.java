package com.publishgateway.udpproxy.protocol.strategy.sigma;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PMG 文件文本提取器
 *
 * 支持三种策略，按优先级依次尝试：
 *   策略1   — 从文件中找 Base64 块 → 解码为 RTF → 提取 GBK 中文
 *   策略1.5 — 在二进制中直接定位 {\rtf1 魔数 → 提取 GBK 中文（无 Base64 中转）
 *   策略2   — 直接扫描二进制中连续的 GBK 双字节序列（严格噪声过滤）
 */
@Slf4j
public class PmgTextExtractor {

    // GBK 字符集用于解码中文字符
    private static final Charset GBK = Charset.forName("GBK");

    // 匹配 \'xx 格式的 GBK 编码 (如 \'ce\'d2 = 我)
    private static final Pattern GBK_PATTERN = Pattern.compile("\\\\'([0-9a-fA-F]{2})");

    // 匹配 Base64 字符串（最短 20 字符，降低门槛以兼容短块）
    private static final Pattern BASE64_PATTERN = Pattern.compile("[A-Za-z0-9+/]{20,}={0,2}");

    /**
     * 从 PMG 文件数据中提取中文文本
     * 
     * @param pmgData PMG 文件的二进制数据
     * @return 提取的中文文本，如果提取失败返回 null
     */
    public static String extractText(byte[] pmgData) {
        if (pmgData == null || pmgData.length < 100) {
            return null;
        }

        try {
            // === 策略1: 找 Base64 块 → 解码为 RTF → 提取 GBK ===
            String base64Data = extractBase64FromPmg(pmgData);
            if (base64Data != null) {
                log.debug("【PMG提取】找到 Base64 块，长度: {}", base64Data.length());
                try {
                    byte[] rtfData = Base64.getDecoder().decode(base64Data);
                    // 只有解码后看起来像 RTF 才继续（以 { 开头或含 \rtf）
                    String rtfStr = new String(rtfData, java.nio.charset.StandardCharsets.ISO_8859_1);
                    if (rtfStr.contains("\\rtf") || rtfStr.contains("\\par")) {
                        String text = extractChineseFromRtf(rtfData);
                        if (text != null && !text.isEmpty()) {
                            log.info("【PMG提取】策略1(Base64+RTF)成功: '{}'", text);
                            return text;
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Base64 块不是合法的 Base64 数据，跳过
                }
            } else {
                log.debug("【PMG提取】未找到 Base64 块");
            }

            // === 策略1.5: 直接在二进制中定位 {\rtf1 魔数 ===
            String rtfDirectText = extractFromEmbeddedRtf(pmgData);
            if (rtfDirectText != null && !rtfDirectText.isEmpty()) {
                log.info("【PMG提取】策略1.5(内嵌RTF)成功: '{}'", rtfDirectText);
                return rtfDirectText;
            }

            // === 策略2: 二进制 GBK 扫描（严格噪声过滤） ===
            String binaryText = extractGbkTextFromBinary(pmgData);
            if (binaryText != null && !binaryText.isEmpty()) {
                log.info("【PMG提取】策略2(GBK扫描)成功: '{}'", binaryText);
                return binaryText;
            }

            log.debug("【PMG提取】所有策略均未提取到文本");
            return null;

        } catch (Exception e) {
            log.warn("【PMG提取】提取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 PMG 文件数据中提取 Base64 编码
     * 查找文件末尾最长的 Base64 字符串
     */
    private static String extractBase64FromPmg(byte[] pmgData) {
        // 将文件内容转为字符串 (使用 ISO-8859-1 避免多字节字符问题)
        String content = new String(pmgData, java.nio.charset.StandardCharsets.ISO_8859_1);
        
        Matcher matcher = BASE64_PATTERN.matcher(content);
        String longestMatch = null;
        int maxLength = 0;
        
        while (matcher.find()) {
            String match = matcher.group();
            if (match.length() > maxLength) {
                maxLength = match.length();
                longestMatch = match;
            }
        }
        
        return longestMatch;
    }

    /**
     * 从 RTF 数据中提取中文字符（纯文本内容，不包含字体信息）
     * RTF 中中文使用 \'xx\'yy 格式存储，xx 和 yy 是 GBK 编码的字节
     * 
     * 提取策略：只提取 \par 段落标记中的文本内容，忽略字体表(fonttbl)中的字体名称
     */
    private static String extractChineseFromRtf(byte[] rtfData) {
        // 将 RTF 转为字符串
        String rtf = new String(rtfData, java.nio.charset.StandardCharsets.ISO_8859_1);
        
        // 查找 \par 段落标记，只提取段落中的文本（排除字体表中的字体名称）
        // RTF 结构: {\fonttbl...} 是字体定义，{\...\par } 是正文段落
        StringBuilder textContent = new StringBuilder();
        
        // 方法：查找所有在 \par 附近的 \'xx 编码
        // 先找到所有的 \par 位置
        int parIndex = rtf.indexOf("\\par");
        while (parIndex != -1) {
            // 向前查找 { 开始标记
            int blockStart = rtf.lastIndexOf("{", parIndex);
            if (blockStart != -1 && blockStart < parIndex) {
                // 提取这个段落块中的 GBK 编码
                String paragraph = rtf.substring(blockStart, parIndex);
                String paragraphText = extractGbkFromParagraph(paragraph);
                if (paragraphText != null && !paragraphText.isEmpty()) {
                    textContent.append(paragraphText);
                }
            }
            // 查找下一个 \par
            parIndex = rtf.indexOf("\\par", parIndex + 4);
        }
        
        // 如果没找到 \par，尝试直接提取（兼容模式）
        if (textContent.length() == 0) {
            // 排除 fonttbl 部分
            String rtfWithoutFontTbl = rtf.replaceAll("\\{\\\\fonttbl[^\\}]*\\}", "");
            return extractAllGbkText(rtfWithoutFontTbl);
        }
        
        return textContent.toString().trim();
    }
    
    /**
     * 从单个段落中提取 GBK 编码的文本
     */
    private static String extractGbkFromParagraph(String paragraph) {
        Matcher matcher = GBK_PATTERN.matcher(paragraph);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        while (matcher.find()) {
            String hexByte = matcher.group(1);
            try {
                int byteValue = Integer.parseInt(hexByte, 16);
                baos.write(byteValue);
            } catch (NumberFormatException e) {
                log.debug("【PMG提取】无效的十六进制: {}", hexByte);
            }
        }
        
        byte[] gbkBytes = baos.toByteArray();
        if (gbkBytes.length == 0) {
            return null;
        }
        
        try {
            return new String(gbkBytes, GBK);
        } catch (Exception e) {
            log.warn("【PMG提取】GBK 解码失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从整个 RTF 中提取所有 GBK 文本（备用方法）
     */
    private static String extractAllGbkText(String rtf) {
        Matcher matcher = GBK_PATTERN.matcher(rtf);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        while (matcher.find()) {
            String hexByte = matcher.group(1);
            try {
                int byteValue = Integer.parseInt(hexByte, 16);
                baos.write(byteValue);
            } catch (NumberFormatException e) {
                log.debug("【PMG提取】无效的十六进制: {}", hexByte);
            }
        }
        
        byte[] gbkBytes = baos.toByteArray();
        if (gbkBytes.length == 0) {
            return null;
        }
        
        try {
            return new String(gbkBytes, GBK).trim();
        } catch (Exception e) {
            log.warn("【PMG提取】GBK 解码失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否为 PMG 文件
     * 通过检查文件头或内容特征
     */
    public static boolean isPmgFile(byte[] data) {
        if (data == null || data.length < 10) {
            return false;
        }
        
        // 检查文件内容是否包含 "NotePmg" 或 "PMG" 特征
        String content = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);
        return content.contains("NotePmg") || content.contains("PMG");
    }

    /**
     * 在 PMG 二进制中直接定位内嵌的 RTF 数据（策略1.5）
     *
     * 部分 PMG 版本不经 Base64 编码，直接将 RTF 内容嵌入文件，
     * 通过查找 "{\rtf1" 魔数头可以定位 RTF 数据块。
     */
    private static String extractFromEmbeddedRtf(byte[] data) {
        String content = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);

        // 查找 RTF 魔数头
        int rtfStart = content.indexOf("{\\rtf1");
        if (rtfStart < 0) {
            rtfStart = content.indexOf("{\\rtf");
        }
        if (rtfStart < 0) {
            return null;
        }

        // 找到对应的结束 } (计数嵌套大括号)
        int depth = 0;
        int rtfEnd = content.length();
        for (int i = rtfStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    rtfEnd = i + 1;
                    break;
                }
            }
        }

        String rtfBlock = content.substring(rtfStart, rtfEnd);
        log.debug("【PMG提取-1.5】找到内嵌 RTF，长度: {}", rtfBlock.length());

        // 用已有的 RTF 中文提取逻辑处理
        return extractChineseFromRtf(rtfBlock.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    /**
     * 直接从 PMG 二进制数据中扫描 GBK 编码的中文文本（策略2，严格噪声过滤）
     *
     * 噪声过滤规则：
     *   1. 每段至少 5 个汉字（10 字节）
     *   2. 段内不能全部是同一个字符（排除 "换换换换" 类二进制重复）
     *   3. 中文字符占比 > 70%
     */
    private static String extractGbkTextFromBinary(byte[] data) {
        if (data == null || data.length < 10) {
            return null;
        }

        String bestText = null;
        int i = 0;
        while (i < data.length - 1) {
            int b1 = data[i] & 0xFF;
            int b2 = data[i + 1] & 0xFF;

            // GBK 双字节范围：首字节 0x81-0xFE，次字节 0x40-0xFE（排除 0x7F）
            if (b1 >= 0x81 && b1 <= 0xFE && b2 >= 0x40 && b2 <= 0xFE && b2 != 0x7F) {
                ByteArrayOutputStream segment = new ByteArrayOutputStream();
                while (i < data.length - 1) {
                    int c1 = data[i] & 0xFF;
                    int c2 = data[i + 1] & 0xFF;
                    if (c1 >= 0x81 && c1 <= 0xFE && c2 >= 0x40 && c2 <= 0xFE && c2 != 0x7F) {
                        segment.write(c1);
                        segment.write(c2);
                        i += 2;
                    } else {
                        break;
                    }
                }

                // ── 规则1: 至少 5 个汉字 ──
                if (segment.size() < 10) continue;

                try {
                    String text = new String(segment.toByteArray(), GBK);

                    // ── 规则2: 不能全是同一个字符 ──
                    if (isAllSameChar(text)) continue;

                    // ── 规则3: 中文占比 > 70% ──
                    long chineseCount = text.chars()
                            .filter(c -> c >= 0x4E00 && c <= 0x9FFF)
                            .count();
                    if (chineseCount * 100 / text.length() < 70) continue;

                    // 保留最长满足条件的段
                    if (bestText == null || text.length() > bestText.length()) {
                        bestText = text;
                    }
                } catch (Exception e) {
                    log.debug("【PMG扫描】GBK解码失败: {}", e.getMessage());
                }
            } else {
                i++;
            }
        }

        return bestText != null ? bestText.trim() : null;
    }

    /** 判断字符串是否全由同一个字符组成 */
    private static boolean isAllSameChar(String text) {
        if (text == null || text.isEmpty()) return true;
        char first = text.charAt(0);
        for (int i = 1; i < text.length(); i++) {
            if (text.charAt(i) != first) return false;
        }
        return true;
    }
}

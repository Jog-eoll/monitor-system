package com.gateway.device.protocol.base.jetfileii.standard.helper;

import com.gateway.device.protocol.base.jetfileii.standard.text.constant.JetFileIIFont;

/**
 * 从 NMG 原始字节中提取控制前缀中的字体信息。
 *
 * <p>控制前缀末尾: ... 0x1C [color] 0x1D [bg] 0x1A [fontCode] 0x07 [blink]
 * 在控制前缀区域（约前 256 字节）扫描 0x1A 标记读取字体代码。</p>
 */
public final class NmgControlPrefixParser {

    private NmgControlPrefixParser() {
    }

    /**
     * 从 NMG 原始字节提取 JetFileIIFont 枚举。可能含 18 字节 NG 头。
     */
    public static JetFileIIFont extractFont(byte[] nmgRaw) {
        Character code = extractFontCode(nmgRaw);
        return code != null ? JetFileIIFont.ofCode(code) : null;
    }

    /**
     * 从 NMG 原始字节提取字体代码字符。
     * 跳过 NG 头（若存在），在前 256 字节内扫描 0x1A 标记。
     */
    public static Character extractFontCode(byte[] nmgRaw) {
        if (nmgRaw == null || nmgRaw.length < 2) return null;
        int start = (nmgRaw[0] == 'N' && nmgRaw[1] == 'G') ? 18 : 0;
        int end = Math.min(start + 256, nmgRaw.length);
        Character last = null;
        for (int i = start; i < end - 1; i++) {
            if ((nmgRaw[i] & 0xFF) == 0x1A) {
                last = (char) (nmgRaw[i + 1] & 0xFF);
            }
        }
        return last;
    }
}

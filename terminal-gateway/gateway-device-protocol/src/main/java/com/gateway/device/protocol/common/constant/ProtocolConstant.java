package com.gateway.device.protocol.common.constant;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.Charset;

/**
 * 协议级共享常量 —— 线程安全，全模块复用。
 */
public final class ProtocolConstant {

    /**
     * GB18030 字符集（线程安全）
     */
    public static final Charset GB18030 = Charset.forName("GB18030");
    /**
     * 回车符 CR (0x0D)，Nmg 文本行分隔符
     */
    public static final byte CR = 0x0D;
    /**
     * 换行符 LF (0x0A)
     */
    public static final byte LF = 0x0A;
    /**
     * NUL 终止符
     */
    public static final byte NUL = 0x00;

    private ProtocolConstant() {
    }

    /**
     * 将 MAC 地址格式化为 {@code 5C-EA-1D-18-D3-0D} 大写短横线模式。
     *
     * <p>移除已有分隔符（冒号、短横线、空格、点），取 12 位 HEX 分组。</p>
     */
    public static String formatMac(String raw) {
        if (StringUtils.isEmpty(raw)) return null;
        String hex = raw.replaceAll("[:-]|\\s|\\.", "").toUpperCase();
        if (hex.length() != 12) return hex;
        return String.join("-",
                hex.substring(0, 2), hex.substring(2, 4),
                hex.substring(4, 6), hex.substring(6, 8),
                hex.substring(8, 10), hex.substring(10, 12));
    }
}

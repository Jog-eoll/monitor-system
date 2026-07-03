package com.gateway.device.protocol.base.jetfileii.standard.text;

import com.gateway.device.protocol.base.jetfileii.standard.text.constant.FontColor;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.JetFileIIFont;

/**
 * Nmg 文本段 — 一段连续文本及其字体颜色和大小。
 *
 * <p>用于 {@link NmgTextBuilder#segments(NmgTextSegment...)} 按字符/词组精细控制颜色。</p>
 */
public class NmgTextSegment {

    final String text;
    /**
     * 0x1C 之后的字体颜色字节 (预定义1B / BGR 4B)，null=沿用上一段
     */
    final byte[] fontColor;
    /**
     * 0x1A 之后的字体代码，0=沿用上一段
     */
    final byte fontCode;

    NmgTextSegment(String text, byte[] fontColor, byte fontCode) {
        this.text = text;
        this.fontColor = fontColor;
        this.fontCode = fontCode;
    }

    /**
     * 创建文本段 (颜色和字体不变)
     */
    public static NmgTextSegment of(String text) {
        return new NmgTextSegment(text, null, (byte) 0);
    }

    /**
     * 创建文本段 (指定预定义颜色+字体)
     */
    public static NmgTextSegment of(String text, FontColor color, JetFileIIFont font) {
        return new NmgTextSegment(text, new byte[]{color.getCode()}, (byte) font.getCode());
    }

    /**
     * 创建文本段 (指定 BGR 颜色+字体)
     */
    public static NmgTextSegment ofBgr(String text, int r, int g, int b, JetFileIIFont font) {
        return new NmgTextSegment(text, NmgResources.customBgr(r, g, b), (byte) font.getCode());
    }
}

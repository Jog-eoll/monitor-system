package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.15 字体背景颜色 (0x1D)。
 */
@Getter
@AllArgsConstructor
public enum BgColor {
    BLACK('0', "black", "黑色"),
    RED('1', "red", "红色"),
    GREEN('2', "green", "绿色"),
    YELLOW('3', "yellow", "黄色");

    private final char code;
    private final String tagName;
    private final String label;

    public static BgColor ofCode(char code) {
        for (BgColor v : values()) if (v.code == code) return v;
        return null;
    }

    public static BgColor ofName(String name) {
        for (BgColor v : values()) if (v.tagName.equalsIgnoreCase(name)) return v;
        return null;
    }

    /**
     * 自定义 BGR 颜色 (0x1D '/' + BGR 3B)
     */
    public static byte[] customBgr(int r, int g, int b) {
        return new byte[]{0x1D, '/', (byte) b, (byte) g, (byte) r};
    }
}

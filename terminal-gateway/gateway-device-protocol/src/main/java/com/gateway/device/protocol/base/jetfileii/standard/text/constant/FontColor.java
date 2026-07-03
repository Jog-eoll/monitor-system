package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.14 字体颜色 (0x1C) — 19 种预定义 + 自定义 BGR。
 */
@Getter
@AllArgsConstructor
public enum FontColor {
    BLACK((byte) 0x30, "黑色"),
    RED((byte) 0x31, "红色"),
    GREEN((byte) 0x32, "绿色"),
    YELLOW((byte) 0x33, "黄色"),
    RGY_CHAR((byte) 0x34, "黄绿红[字符]"),
    RGY_HORIZONTAL((byte) 0x35, "黄绿红[水平]"),
    RGY_WAVE((byte) 0x36, "黄绿红[波浪]"),
    RGY_DIAGONAL((byte) 0x37, "黄绿红[斜线]"),
    BLUE_WHITE_V((byte) 0x38, "蓝白[垂直渐变]"),
    BLUE_WHITE_H((byte) 0x39, "蓝白[水平渐变]"),
    YELLOW_WHITE_V((byte) 0x3A, "黄白[垂直渐变]"),
    YELLOW_WHITE_H((byte) 0x3B, "黄白[水平渐变]"),
    RED_WHITE_V((byte) 0x3C, "红白[垂直渐变]"),
    RED_WHITE_H((byte) 0x3D, "红白[水平渐变]"),
    GREEN_WHITE_V((byte) 0x3E, "绿白[垂直渐变]"),
    GREEN_WHITE_H((byte) 0x3F, "绿白[水平渐变]"),
    BLUE_WHITE_H0X40((byte) 0x40, "蓝白[水平渐变]②"),
    BLUE_WHITE_V0X41((byte) 0x41, "蓝白[垂直渐变]②"),
    BLUE_WHITE_H0X42((byte) 0x42, "蓝白[水平渐变]③");

    private final byte code;
    private final String label;

    public static FontColor ofCode(byte code) {
        for (FontColor v : values()) if (v.code == code) return v;
        return null;
    }
}

package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.2 特殊字符 (0x0B) — 日期/时间/时区。
 */
@Getter
@AllArgsConstructor
public enum DateTime {
    DATE_MM_DD_YY_SLASH((byte) 0x20, "MM/DD/YY"),
    DATE_DD_MM_YY_SLASH((byte) 0x21, "DD/MM/YY"),
    DATE_MM_DD_YY_DASH((byte) 0x22, "MM-DD-YY"),
    DATE_DD_MM_YY_DASH((byte) 0x23, "DD-MM-YY"),
    DATE_MM_DD_YYYY_DOT((byte) 0x24, "MM.DD.YYYY"),
    YEAR_2D((byte) 0x25, "YY"),
    YEAR_4D((byte) 0x26, "YYYY"),
    MONTH_NUMBER((byte) 0x27, "MM(数字)"),
    MONTH_ABBR((byte) 0x28, "MMM(缩写)"),
    DAY_NUMBER((byte) 0x29, "DD(数字)"),
    DOW_NUMBER((byte) 0x2A, "星期(数字)"),
    DOW_ABBR((byte) 0x2B, "星期(缩写)"),

    TIME_HH24((byte) 0x2C, "HH(24小时)"),
    TIME_MIN((byte) 0x2D, "MIN"),
    TIME_SEC((byte) 0x2E, "SEC"),
    TIME_HH_MM_24((byte) 0x2F, "HH:MIN(24小时)"),
    TIME_HH_MM_12((byte) 0x30, "HH:MIN(12小时)"),
    TIME_CUSTOM((byte) 0x34, "自定义时间格式(4B)"),
    TIME_HH12((byte) 0x35, "HH(12小时制)"),

    TIME_HH_MM_GMT((byte) 0x53, "HH:MIN(含GMT偏移)"),
    TZ_MINUS_12((byte) 0x54, "HH:MIN(GMT-12)"),
    TZ_MINUS_11((byte) 0x55, "HH:MIN(GMT-11)"),
    TZ_MINUS_10((byte) 0x56, "HH:MIN(GMT-10)"),
    TZ_MINUS_9((byte) 0x57, "HH:MIN(GMT-9)"),
    TZ_MINUS_8((byte) 0x58, "HH:MIN(GMT-8)"),
    TZ_MINUS_7((byte) 0x59, "HH:MIN(GMT-7)"),
    TZ_MINUS_6((byte) 0x5A, "HH:MIN(GMT-6)"),
    TZ_MINUS_5((byte) 0x5B, "HH:MIN(GMT-5)"),
    TZ_MINUS_4((byte) 0x5C, "HH:MIN(GMT-4)"),
    TZ_MINUS_3((byte) 0x5D, "HH:MIN(GMT-3)"),
    TZ_MINUS_2((byte) 0x5E, "HH:MIN(GMT-2)"),
    TZ_MINUS_1((byte) 0x5F, "HH:MIN(GMT-1)"),
    TZ_PLUS_0((byte) 0x60, "HH:MIN(GMT+0)"),
    TZ_PLUS_1((byte) 0x61, "HH:MIN(GMT+1)"),
    TZ_PLUS_2((byte) 0x62, "HH:MIN(GMT+2)"),
    TZ_PLUS_3((byte) 0x63, "HH:MIN(GMT+3)"),
    TZ_PLUS_4((byte) 0x64, "HH:MIN(GMT+4)"),
    TZ_PLUS_5((byte) 0x65, "HH:MIN(GMT+5)"),
    TZ_PLUS_6((byte) 0x66, "HH:MIN(GMT+6)"),
    TZ_PLUS_7((byte) 0x67, "HH:MIN(GMT+7)"),
    TZ_PLUS_8((byte) 0x68, "HH:MIN(GMT+8)"),
    TZ_PLUS_9((byte) 0x69, "HH:MIN(GMT+9)"),
    TZ_PLUS_10((byte) 0x6A, "HH:MIN(GMT+10)"),
    TZ_PLUS_11((byte) 0x6B, "HH:MIN(GMT+11)"),
    TZ_PLUS_12((byte) 0x6C, "HH:MIN(GMT+12)"),
    TZ_PLUS_13((byte) 0x6D, "HH:MIN(GMT+13)"),
    TZ_MINUS_3_30((byte) 0x6E, "HH:MIN(GMT-3:30)"),
    TZ_PLUS_5_30((byte) 0x6F, "HH:MIN(GMT+5:30)"),
    TZ_PLUS_5_45((byte) 0x70, "HH:MIN(GMT+5:45)"),
    TZ_PLUS_6_30((byte) 0x71, "HH:MIN(GMT+6:30)"),
    TZ_PLUS_9_30((byte) 0x72, "HH:MIN(GMT+9:30)"),
    TZ_PLUS_3_30_2((byte) 0x73, "HH:MIN(GMT+3:30)"),
    TZ_PLUS_4_30((byte) 0x74, "HH:MIN(GMT+4:30)"),
    TZ_MINUS_4_30((byte) 0x75, "HH:MIN(GMT-4:30)");

    private final byte code;
    private final String label;

    public static DateTime ofCode(byte code) {
        for (DateTime v : values()) if (v.code == code) return v;
        return null;
    }

    public byte[] sequence() {
        return new byte[]{0x0B, code};
    }
}

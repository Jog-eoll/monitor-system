package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.1 速度 (0x0F) — 0~6 级。
 */
@Getter
@AllArgsConstructor
public enum Speed {
    S0('0', "最快"),
    S1('1', "快速"),
    S2('2', "较快"),
    S3('3', "中速"),
    S4('4', "较慢"),
    S5('5', "慢速"),
    S6('6', "最慢");

    private final char code;
    private final String label;

    public static Speed ofCode(int code) {
        for (Speed v : values()) if (v.code == code) return v;
        return null;
    }
}

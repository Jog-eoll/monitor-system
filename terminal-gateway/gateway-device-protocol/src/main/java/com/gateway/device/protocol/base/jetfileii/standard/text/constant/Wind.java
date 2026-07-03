package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.2 特殊字符 (0x0B) — 风速/风向。
 */
@Getter
@AllArgsConstructor
public enum Wind {
    WIND_SPEED_UNIT((byte) 0xF1, "风速(xxm/s,带单位)"),
    WIND_DIRECTION((byte) 0xF2, "风向"),
    WIND_SPEED_NO_UNIT((byte) 0xF3, "风速(无单位)");

    private final byte code;
    private final String label;

    public static Wind ofCode(byte code) {
        for (Wind v : values()) if (v.code == code) return v;
        return null;
    }

    public byte[] sequence() {
        return new byte[]{0x0B, code};
    }
}

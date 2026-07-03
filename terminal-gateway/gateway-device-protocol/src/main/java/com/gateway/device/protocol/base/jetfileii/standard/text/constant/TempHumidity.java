package com.gateway.device.protocol.base.jetfileii.standard.text.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NMG §15.2 特殊字符 (0x0B) — 温度/湿度。
 */
@Getter
@AllArgsConstructor
public enum TempHumidity {
    TEMP_CELSIUS((byte) 0x31, "温度(摄氏)"),
    HUMIDITY((byte) 0x32, "湿度"),
    TEMP_FAHRENHEIT((byte) 0x33, "温度(华氏)");

    private final byte code;
    private final String label;

    public static TempHumidity ofCode(byte code) {
        for (TempHumidity v : values()) if (v.code == code) return v;
        return null;
    }

    public byte[] sequence() {
        return new byte[]{0x0B, code};
    }
}

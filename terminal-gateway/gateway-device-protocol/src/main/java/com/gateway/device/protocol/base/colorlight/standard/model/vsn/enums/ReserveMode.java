package com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 缩放模式（宽高比保持）—— SDK 3.4.4。
 */
public enum ReserveMode {

    /**
     * 拉伸填充，不保持宽高比
     */
    FIT_XY(0),
    /**
     * 等比缩放，保持宽高比
     */
    CENTER_INSIDE(1);

    private final int code;

    ReserveMode(int code) {
        this.code = code;
    }

    @JsonCreator
    public static ReserveMode fromValue(int code) {
        for (ReserveMode m : values()) {
            if (m.code == code) return m;
        }
        // 兼容未知值，默认返回 FIT_XY
        return FIT_XY;
    }

    @JsonValue
    public int toValue() {
        return code;
    }
}

package com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 页面循环类型 —— SDK 3.2。
 */
public enum LoopType {

    /**
     * 播放指定时长
     */
    FIXED(0),
    /**
     * 自动计算时长
     */
    AUTO(1);

    private final int code;

    LoopType(int code) {
        this.code = code;
    }

    @JsonCreator
    public static LoopType fromValue(int code) {
        for (LoopType t : values()) {
            if (t.code == code) return t;
        }
        return AUTO;
    }

    @JsonValue
    public int toValue() {
        return code;
    }
}

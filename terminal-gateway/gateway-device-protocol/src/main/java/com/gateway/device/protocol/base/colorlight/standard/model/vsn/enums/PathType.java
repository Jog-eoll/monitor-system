package com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 文件路径类型 —— SDK 3.4.4。
 * <p>图片和视频必须使用 {@link #RELATIVE}。</p>
 */
public enum PathType {

    /**
     * 绝对路径
     */
    ABSOLUTE(0),
    /**
     * 相对路径（图片/视频必须使用此值）
     */
    RELATIVE(1);

    private final int code;

    PathType(int code) {
        this.code = code;
    }

    @JsonCreator
    public static PathType fromValue(int code) {
        for (PathType t : values()) {
            if (t.code == code) return t;
        }
        return RELATIVE;
    }

    @JsonValue
    public int toValue() {
        return code;
    }
}

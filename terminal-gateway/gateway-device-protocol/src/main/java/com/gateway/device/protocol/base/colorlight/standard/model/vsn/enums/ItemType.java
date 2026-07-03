package com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * VSN 素材类型 —— SDK 3.4.1~3.4.13 定义的完整类型集合。
 */
@Getter
public enum ItemType {

    /**
     * 图片
     */
    PICTURE(2, "图片"),
    /**
     * 视频
     */
    VIDEO(3, "视频"),
    /**
     * 单行文本
     */
    SINGLE_TEXT(4, "单行文本"),
    /**
     * 多行文本
     */
    MULTI_TEXT(5, "多行文本"),
    /**
     * GIF
     */
    GIF(6, "GIF"),
    /**
     * 模拟时钟
     */
    CLOCK(7, "时钟"),
    /**
     * 摄像头
     */
    CAMERA(8, "摄像头"),
    /**
     * 数字时钟
     */
    DIGITAL_CLOCK(9, "普通时钟"),
    /**
     * 文档（doc/excel 等）
     */
    DOC(11, "文档"),
    /**
     * 天气预报
     */
    WEATHER(14, "气象"),
    /**
     * 计时
     */
    TIMER(15, "计时器"),
    /**
     * 精美时钟
     */
    EXQUISITE_CLOCK(16, "精美时钟"),
    /**
     * 湿度
     */
    HUMIDITY(21, "湿度"),
    /**
     * 温度
     */
    TEMPERATURE(22, "温度"),
    /**
     * 噪声
     */
    NOISE(23, "噪音"),
    /**
     * 空气质量
     */
    AIR_QUALITY(24, "空气质量"),
    /**
     * 网页/流媒体/新闻聚合
     */
    WEB(27, "网页/流媒体"),
    /**
     * 烟雾
     */
    SMOKE(28, "烟雾"),
    /**
     * 亮度
     */
    NO_SENSOR(29, "无传感器提示"),
    /**
     * 自定义传感器
     */
    SENSOR_INIT(30, "传感器初始值"),
    /**
     * 单列文本
     */
    SINGLE_COLUMN(102, "单列文本");

    private final int code;
    private final String displayName;

    ItemType(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    @JsonCreator
    public static ItemType fromValue(int code) {
        for (ItemType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知 ItemType: " + code);
    }

    @JsonValue
    public int toValue() {
        return code;
    }
}

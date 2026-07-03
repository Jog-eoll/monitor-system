package com.gateway.device.protocol.common;

/**
 * 数值范围裁剪工具 —— 将数值约束在指定区间内。
 *
 * <p>适用于亮度、音量等百分比类参数（0-100）。</p>
 */
public final class ValueClamp {

    /**
     * 百分比参数下限
     */
    public static final int PERCENT_MIN = 0;
    /**
     * 百分比参数上限
     */
    public static final int PERCENT_MAX = 100;

    private ValueClamp() {
        // 工具类不可实例化
    }

    /**
     * 裁剪到 [0, 100] 百分比区间。
     *
     * @param value 原始值
     * @return 约束后的值（0-100）
     */
    public static int ratio(int value) {
        return clamp(value, PERCENT_MIN, PERCENT_MAX);
    }

    /**
     * 通用范围裁剪。
     *
     * @param value 原始值
     * @param min   下限
     * @param max   上限
     * @return 约束后的值
     */
    public static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}

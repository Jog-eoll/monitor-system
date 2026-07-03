package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 色彩测试参数 — COLOR_TEST。
 *
 * <p>{@code enabled=true} 开启，{@code false} 关闭。
 * {@code color} 指定目标色彩，默认 {@link Color#RED}，预留后续扩展。</p>
 */
@Data
@Builder
public class ColorTestParams implements CommandParams {

    /**
     * 色彩测试开关：{@code true} 开启，{@code false} 关闭，默认 true
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 目标色彩，默认 {@link Color#RED}
     */
    @Builder.Default
    private Color color = Color.RED;

    /**
     * 色彩测试目标色彩枚举。
     */
    public enum Color {
        RED,
        GREEN,
        BLUE,
        WHITE,
        GRAY,
        /**
         * 彩条测试
         */
        COLOR_BAR
    }
}

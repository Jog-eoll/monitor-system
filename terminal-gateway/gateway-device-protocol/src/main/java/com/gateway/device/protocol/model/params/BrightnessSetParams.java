package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

/**
 * 亮度控制参数 — BRIGHTNESS_SET。
 *
 * <p>Nova: 使用 {@code action} + {@code ratio}(0-100)
 * <br>JetFileII: 使用 {@code ratio}(0-100)
 * <br>ratio 默认 80，有效范围 [0, 100]</p>
 */
@Data
@Builder
public class BrightnessSetParams implements CommandParams {

    /**
     * 操作类型：GET 查询当前亮度，SET 设定亮度
     */
    @Builder.Default
    private BrightnessAction action = BrightnessAction.GET;

    /**
     * 亮度百分比 0-100，默认 80
     */
    @Builder.Default
    private int ratio = 80;

    public enum BrightnessAction {
        GET, SET
    }
}

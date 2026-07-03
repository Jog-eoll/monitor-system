package com.gateway.device.protocol.base.colorlight.standard.model.vsn.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 入场特效，支持图片节目和 MultiPicInfo 渲染文本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Effect {
    /**
     * 特效类型，参考 ColorLightCommand.EFFECT_*
     */
    @Builder.Default
    private Integer type = 0;
    /**
     * 特效时长（毫秒）
     */
    private Long time;
}

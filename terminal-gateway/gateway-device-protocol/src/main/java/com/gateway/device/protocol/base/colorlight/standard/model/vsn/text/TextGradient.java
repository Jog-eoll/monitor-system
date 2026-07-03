package com.gateway.device.protocol.base.colorlight.standard.model.vsn.text;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本渐变色配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextGradient {
    @JsonProperty("gradientStartX")
    private Integer gradientStartX;
    @JsonProperty("gradientStartY")
    private Integer gradientStartY;
    @JsonProperty("gradientEndX")
    private Integer gradientEndX;
    @JsonProperty("gradientEndY")
    private Integer gradientEndY;
    @JsonProperty("gradientColors")
    private String gradientColors;
    @JsonProperty("gradientPositions")
    private String gradientPositions;
    @JsonProperty("gradientMode")
    private Integer gradientMode;
}

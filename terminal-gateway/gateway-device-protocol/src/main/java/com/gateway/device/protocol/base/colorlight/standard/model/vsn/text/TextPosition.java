package com.gateway.device.protocol.base.colorlight.standard.model.vsn.text;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本位置配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextPosition {
    @JsonProperty("text")
    private Integer text;
    @JsonProperty("week")
    private Integer week;
    @JsonProperty("date")
    private Integer date;
}

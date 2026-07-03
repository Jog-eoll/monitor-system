package com.gateway.device.protocol.base.colorlight.standard.model.vsn.text;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字体样式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogFont {
    @JsonProperty("lfHeight")
    private Integer lfHeight;
    @JsonProperty("lfWeight")
    private Integer lfWeight;
    @JsonProperty("lfItalic")
    private Integer lfItalic;
    @JsonProperty("lfUnderline")
    private Integer lfUnderline;
    @JsonProperty("lfFaceName")
    private String lfFaceName;
}

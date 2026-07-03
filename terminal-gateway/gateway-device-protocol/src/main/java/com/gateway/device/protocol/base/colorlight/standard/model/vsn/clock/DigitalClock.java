package com.gateway.device.protocol.base.colorlight.standard.model.vsn.clock;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigitalClock {
    @Builder.Default
    private Integer type = 1;
    private Integer flags;
    @JsonProperty("ftSize")
    private Integer ftSize;
    @JsonProperty("ftColor")
    private String ftColor;
    @JsonProperty("bItalic")
    private Integer bItalic;
    @JsonProperty("bUnderline")
    private Integer bUnderline;
    @JsonProperty("bBold")
    private Integer bBold;
    private Integer weight;
    private String name;
}

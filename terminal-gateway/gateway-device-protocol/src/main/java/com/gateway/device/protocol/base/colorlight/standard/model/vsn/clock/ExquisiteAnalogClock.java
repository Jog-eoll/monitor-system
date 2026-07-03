package com.gateway.device.protocol.base.colorlight.standard.model.vsn.clock;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.text.LogFont;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.text.TextGradient;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.text.TextPosition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 精美模拟时钟（Type=16）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExquisiteAnalogClock {
    @JsonProperty("useDark")
    private Integer useDark;
    @JsonProperty("centerPointRadius")
    private Integer centerPointRadius;
    @JsonProperty("centerPointColor")
    private String centerPointColor;
    @JsonProperty("boardType")
    private Integer boardType;
    @JsonProperty("hourHandType")
    private Integer hourHandType;
    @JsonProperty("hourHandColor")
    private String hourHandColor;
    @JsonProperty("hourHandRatio")
    private Float hourHandRatio;
    @JsonProperty("hourHandGradient")
    private TextGradient hourHandGradient;
    @JsonProperty("hourTickWidth")
    private Integer hourTickWidth;
    @JsonProperty("hourTickHeight")
    private Integer hourTickHeight;
    @JsonProperty("hourTickColor")
    private String hourTickColor;
    @JsonProperty("hourTickFontColor")
    private String hourTickFontColor;
    @JsonProperty("hourTickFont")
    private LogFont hourTickFont;

    @JsonProperty("minuteHandType")
    private Integer minuteHandType;
    @JsonProperty("minuteHandColor")
    private String minuteHandColor;
    @JsonProperty("minuteHandRatio")
    private Float minuteHandRatio;
    @JsonProperty("minuteHandGradient")
    private TextGradient minuteHandGradient;
    @JsonProperty("minuteTickWidth")
    private Integer minuteTickWidth;
    @JsonProperty("minuteTickHeight")
    private Integer minuteTickHeight;
    @JsonProperty("minuteTickColor")
    private String minuteTickColor;

    @JsonProperty("secondHandType")
    private Integer secondHandType;
    @JsonProperty("secondHandColor")
    private String secondHandColor;
    @JsonProperty("secondHandRatio")
    private Float secondHandRatio;

    @JsonProperty("flags")
    private Integer flags;
    @JsonProperty("dateColor")
    private String dateColor;
    @JsonProperty("dateFont")
    private LogFont dateFont;
    @JsonProperty("dateGradient")
    private TextGradient dateGradient;
    @JsonProperty("weekColor")
    private String weekColor;
    @JsonProperty("weekFont")
    private LogFont weekFont;
    @JsonProperty("weekGradient")
    private TextGradient weekGradient;
    @JsonProperty("textPosition")
    private TextPosition textPosition;

    @JsonProperty("hourTickType")
    private Integer hourTickType;
    @JsonProperty("minuteTickType")
    private Integer minuteTickType;
    private Long duration;
}

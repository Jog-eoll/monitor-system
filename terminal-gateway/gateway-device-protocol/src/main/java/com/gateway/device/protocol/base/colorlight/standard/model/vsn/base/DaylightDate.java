package com.gateway.device.protocol.base.colorlight.standard.model.vsn.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 夏令时日期配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DaylightDate {
    @JsonProperty("wMonth")
    private Integer wMonth;

    @JsonProperty("wDay")
    private Integer wDay;

    @JsonProperty("wDayOfWeek")
    private Integer wDayOfWeek;

    @JsonProperty("wHour")
    private Integer wHour;

    @JsonProperty("wMinute")
    private Integer wMinute;

    @JsonProperty("wSecond")
    private Integer wSecond;

    @JsonProperty("wYear")
    private Integer wYear;

    @JsonProperty("wMilliseconds")
    private Integer wMilliseconds;
}

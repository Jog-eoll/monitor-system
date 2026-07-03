package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/brightcurve.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrightCurveInfo {

    private Boolean auto;
    private Boolean isNewBrightness;
    private Integer maxPercent;
    private Integer maxAdjustValue;
    private Integer midPercent;
    private Integer midAdjustValue;
    private Integer minPercent;
    private Integer minAdjustValue;
    private Integer sensorErrorDefaultValue;
    private Boolean save;
    private Integer sensorSource485;
    private Integer sensorSourceMultifunctionCard;
    private Integer sensorSourceLight;
    private Integer sensorSourceM2;
    @JsonProperty("noneReverseGammaValues")
    private List<Double> noneReverseGammaValues;
    @JsonProperty("reverseGammaValues")
    private List<Double> reverseGammaValues;
}

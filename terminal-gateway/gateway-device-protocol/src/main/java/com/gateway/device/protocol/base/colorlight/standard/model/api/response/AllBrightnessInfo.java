package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/allbrightnessinfo.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllBrightnessInfo {

    private Integer realTimeBrightValue;
    private Integer savedBrightValue;
    private Boolean isbShowOn;
    private Boolean isHasSensor;
    private Integer sensorBright;
    private Integer briAndClrTAdjustType;
    private Integer sensorSource485;
    private Integer sensorSourceMultifunctionCard;
}

package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/autorelay.json 单条响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoRelayInfo {

    private Integer enable;
    private Integer relay;
    private Integer isHumitureOnBoard;
    private Integer humidityLimitEnable;
    private Integer temperatureLimitEnable;
    private Integer humidityLimitValue;
    private Integer temperatureLimitValue;
    private Integer smokeLimitEnable;
    private Integer smokeLimitValue;
}

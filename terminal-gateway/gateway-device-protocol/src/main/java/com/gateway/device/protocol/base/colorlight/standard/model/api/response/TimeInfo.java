package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/newrtc.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeInfo {

    private String time;
    private String timezoneId;
    private Double timezone;
    private Integer isautotime;
}

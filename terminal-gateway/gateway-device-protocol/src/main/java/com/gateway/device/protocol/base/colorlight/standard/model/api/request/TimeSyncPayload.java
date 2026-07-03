package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ColorLight PUT /api/newrtc 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSyncPayload {

    private String time;
    private String timezoneId;
    private double timezone;
    private int isautotime;
}

package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/ntptest 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NtpTestResult {

    @JsonProperty("ntp.timestamp.now")
    private Long ntpTimestampNow;

    @JsonProperty("ntp.date.now")
    private String ntpDateNow;
}

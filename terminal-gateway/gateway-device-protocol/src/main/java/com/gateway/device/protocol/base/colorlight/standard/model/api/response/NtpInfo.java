package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ColorLight /api/ntp.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NtpInfo {

    private String ntpserver;
    private Long ntpinterval;
    private Long ntpthreshold;
}

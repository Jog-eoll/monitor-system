package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/http_verification.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpVerificationInfo {

    @JsonProperty("http_ftp_verification")
    private String httpFtpVerification;
}

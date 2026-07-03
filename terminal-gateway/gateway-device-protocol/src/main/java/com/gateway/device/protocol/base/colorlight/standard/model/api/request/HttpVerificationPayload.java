package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PUT /api/http_verification 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpVerificationPayload {

    @JsonProperty("http_ftp_verification")
    private String httpFtpVerification;

    @JsonProperty("http_ftp_username")
    private String httpFtpUsername;

    @JsonProperty("http_ftp_password")
    private String httpFtpPassword;
}

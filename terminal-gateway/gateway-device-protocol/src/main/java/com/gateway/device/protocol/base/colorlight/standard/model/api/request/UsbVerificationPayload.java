package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PUT /api/usbplay_verification 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsbVerificationPayload {

    @JsonProperty("usbplay_verification")
    private String usbplayVerification;

    @JsonProperty("usbplay_name")
    private String usbplayName;

    @JsonProperty("usbplay_password")
    private String usbplayPassword;
}

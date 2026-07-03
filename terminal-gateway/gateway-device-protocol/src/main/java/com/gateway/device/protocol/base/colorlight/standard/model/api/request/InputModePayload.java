package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PUT /api/inputmode 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputModePayload {

    private String inputmode;
}

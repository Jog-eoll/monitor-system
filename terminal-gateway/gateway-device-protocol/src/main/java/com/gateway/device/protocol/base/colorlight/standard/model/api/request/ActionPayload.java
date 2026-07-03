package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ColorLight POST /api/action 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionPayload {

    private String command;
}

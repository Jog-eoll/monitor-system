package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/relay.json 单条响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayInfo {

    private Integer relay;
    private Integer delay;
    private Integer status;
}

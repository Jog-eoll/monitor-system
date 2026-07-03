package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PUT /api/dimension 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionSetPayload {

    private Integer width;
    private Integer height;
    private Integer freq;
}

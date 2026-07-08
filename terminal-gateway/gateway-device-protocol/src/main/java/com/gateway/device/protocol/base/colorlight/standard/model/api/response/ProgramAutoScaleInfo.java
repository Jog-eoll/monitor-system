package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gateway.device.protocol.common.serialize.BoolSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/programautoscale.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramAutoScaleInfo {

    @JsonSerialize(using = BoolSerializer.BoolToIntSerializer.class)
    @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
    private boolean programautoscale;
}

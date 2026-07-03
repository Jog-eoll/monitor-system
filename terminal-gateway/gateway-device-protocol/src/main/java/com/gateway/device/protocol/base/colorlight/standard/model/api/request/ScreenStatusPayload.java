package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gateway.device.protocol.common.serialize.BoolSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ColorLight PUT /api/screenstatus 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenStatusPayload {
    @JsonSerialize(using = BoolSerializer.BoolToIntSerializer.class)
    @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
    private Boolean screenstatus;
}

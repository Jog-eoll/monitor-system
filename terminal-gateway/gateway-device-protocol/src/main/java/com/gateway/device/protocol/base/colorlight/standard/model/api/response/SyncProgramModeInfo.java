package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.gateway.device.protocol.common.serialize.BoolSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/sync_program_mode.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SyncProgramModeInfo {

    @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
    private Boolean syncProgramGpsEnable;

    @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
    private Boolean syncProgramNtpEnable;

    private String syncProgramNtpServer;
    private Integer syncProgramNtpInterval;
    private Integer syncProgramNtpThreshold;
    private Integer syncProgramNtpDeviation;

    @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
    private Boolean syncProgramLanEnable;

    private String syncProgramLanRole;

    @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
    private Boolean syncProgramAudioEnable;
}

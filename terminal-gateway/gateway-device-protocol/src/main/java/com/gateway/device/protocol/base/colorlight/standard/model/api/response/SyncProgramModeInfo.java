package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

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
public class SyncProgramModeInfo {

    private Boolean syncProgramGpsEnable;
    private Boolean syncProgramNtpEnable;
    private String syncProgramNtpServer;
    private Integer syncProgramNtpInterval;
    private Integer syncProgramNtpThreshold;
    private Integer syncProgramNtpDeviation;
    private Boolean syncProgramLanEnable;
    private String syncProgramLanRole;
    private Boolean syncProgramAudioEnable;
}

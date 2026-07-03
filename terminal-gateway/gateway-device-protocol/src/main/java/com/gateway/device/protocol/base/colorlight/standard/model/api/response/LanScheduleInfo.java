package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/lanschedule.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanScheduleInfo {

    private List<Object> commandSchedule;
    private List<Object> contentsSchedule;
}

package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/rcv_layout.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RcvLayoutInfo {

    private Integer errorCode;
    private Integer rcvCount;
    private List<RcvRegion> rcvRegions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RcvRegion {
        private Integer portIndex;
        private Integer rcvIndex;
        private Integer x;
        private Integer y;
        private Integer width;
        private Integer height;
    }
}

package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST /api/rcv_layout 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RcvLayoutPayload {

    private Integer rcvCount;
    private Integer portCount;
    private List<RcvRegion> rcvRegions;
    private Boolean isSend;

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

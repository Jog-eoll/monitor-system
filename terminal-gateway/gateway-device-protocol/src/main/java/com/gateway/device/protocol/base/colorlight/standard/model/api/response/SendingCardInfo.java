package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/sendingcard.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendingCardInfo {

    private Dimension dimension;
    private FrameFlavor frameflavor;
    private List<NetArea> netareas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dimension {
        private Integer width;
        private Integer height;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrameFlavor {
        private Boolean isinterlaced;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetArea {
        private Integer width;
        private Integer height;
        private Integer startx;
        private Integer starty;
    }
}

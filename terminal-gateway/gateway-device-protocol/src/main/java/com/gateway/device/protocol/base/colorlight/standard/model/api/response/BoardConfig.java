package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ColorLight /api/boardconfig.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardConfig {

    private Integer code;
    private BoardConfigData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoardConfigData {
        private Long rotationSupport;
        private Long dualWifiSupport;
        private Long cpuColorControlSupport;
        private Long bootTime;
        private Long contrastSupport;
        private Long hueSupport;
        private Long saturationSupport;
        private Long wifi5GBandSupport;
        private String wifiDriver;
        private DimensionLimit dimensionLimit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionLimit {
        private Long maxArea;
        private Long maxHeight;
        private Long maxWidth;
        private Long minArea;
        private Long minHeight;
        private Long minWidth;
    }
}

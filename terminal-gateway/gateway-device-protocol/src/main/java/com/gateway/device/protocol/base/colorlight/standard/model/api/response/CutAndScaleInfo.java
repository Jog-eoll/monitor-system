package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/setcutandscale.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutAndScaleInfo {

    private Window srcWindow;
    private Window destWindow;
    private Integer videoSource;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Window {
        private Integer top;
        private Integer left;
        private Integer right;
        private Integer bottom;
    }
}

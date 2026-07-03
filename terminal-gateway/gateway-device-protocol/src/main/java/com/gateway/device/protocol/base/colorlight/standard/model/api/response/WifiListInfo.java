package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/wifi.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WifiListInfo {

    private List<AccessPoint> accessPoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessPoint {
        private String ssid;
        private String pass;
        private String bssid;
        private Integer rssi;
        private String security;
        private Integer networkId;
        private String pskType;
    }
}

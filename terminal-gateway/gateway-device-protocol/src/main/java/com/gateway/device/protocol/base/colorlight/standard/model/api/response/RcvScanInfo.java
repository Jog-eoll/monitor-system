package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/smart_read_serial_info?intent=scanReceiverCard 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RcvScanInfo {

    private Integer portNumber;
    private List<ReceiveCard> receiveCard;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiveCard {
        private Integer cltIndex;
        private Integer errorRate;
        private Double humidity;
        private String productCode;
        private String productType;
        private Integer runTime;
        private Double temperature;
        private Double voltage;
    }
}

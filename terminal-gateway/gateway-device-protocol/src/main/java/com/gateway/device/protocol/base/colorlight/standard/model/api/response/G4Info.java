package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/4ginfo.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class G4Info {

    private Integer code;
    private FourGData data;
    private String msg;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FourGData {
        private String deviceid;
        private String phonetype;
        private String operatorname;
        private String operator;
        private String networktype;
        private String hasicc;
        private String simstate;
        private String simoperator;
        private String simoperatorname;
        private String simserial;
        private String linenumber;
        private String imsi;
        private String msisdn;
        private String dataactivity;
        private String datastate;
    }
}

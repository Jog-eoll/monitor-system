package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/apn.json 响应 POJO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApnInfo {

    private Integer id;
    private String name;
    private String apn;
    private String proxy;
    private Integer port;
    private String mmsproxy;
    private Integer mmsport;
    private String server;
    private String user;
    private String password;
    private String mmsc;
    private String mcc;
    private String mnc;
    private Integer numeric;
    private Integer authorType;
    private String type;
    private String protocal;
    private String roamingProtocal;
    private Integer current;
    private Integer carrierEnabled;
    private String bearer;
    private String mvnoType;
    private String mvnoMatchType;
}

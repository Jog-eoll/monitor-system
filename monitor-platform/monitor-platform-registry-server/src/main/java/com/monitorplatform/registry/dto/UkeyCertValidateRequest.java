package com.monitorplatform.registry.dto;

import lombok.Data;

@Data
public class UkeyCertValidateRequest {
    private String certSerialNo;
    private String certificateContent;
    private String clientId;
    private String clientIp;
    private String clientMac;
}

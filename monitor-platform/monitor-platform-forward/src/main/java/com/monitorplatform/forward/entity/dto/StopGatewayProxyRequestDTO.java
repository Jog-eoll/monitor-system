package com.monitorplatform.forward.entity.dto;

import lombok.Data;

@Data
public class StopGatewayProxyRequestDTO {
    private String reason;
    private String operator;
}

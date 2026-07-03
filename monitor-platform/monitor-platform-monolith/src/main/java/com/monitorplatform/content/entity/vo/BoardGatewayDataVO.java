package com.monitorplatform.content.entity.vo;

import lombok.Data;

@Data
public class BoardGatewayDataVO {
    private Long chainId;
    private String boardIp;
    private String terminalGatewayIp;
    private Integer terminalGatewayPort;
}

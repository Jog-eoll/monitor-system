package com.monitorplatform.forward.entity.vo;

import lombok.Data;

@Data
public class DeployBranchDetailVO {
    private String branchCode;
    private Boolean publishGatewaySuccess;
    private Boolean terminalGatewaySuccess;
    private Boolean publishClientSuccess;
    private String terminalGatewayIp;
}

package com.monitorplatform.forward.entity.vo;

import lombok.Data;

import java.util.List;

@Data
public class DeploySummaryVO {
    private Long chainId;
    private Integer totalBranches;
    private DeployGatewayStatsVO publishGateway;
    private DeployGatewayStatsVO terminalGateway;
    private DeployGatewayStatsVO publishClient;
    private List<DeployBranchDetailVO> details;
}

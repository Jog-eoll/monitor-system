package com.monitorplatform.forward.entity.vo;

import lombok.Data;

@Data
public class DeployGatewayStatsVO {
    private Integer successCount;
    private Integer failedCount;
}

package com.monitorplatform.forward.entity.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 网关分支配置下发DTO - 按「发布端加密网关 + 分支」粒度下发
 */
@Data
public class GatewayBranchConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // === 链路标识（网关校验用）===
    
    /**
     * 链路ID
     */
    private Long chainId;
    
    /**
     * 链路编码
     */
    private String chainCode;
    
    /**
     * 配置版本号
     */
    private Integer configVersion;

    // === 发布端加密网关信息 ===
    
    /**
     * 发布加密网关设备ID
     */
    private String publishGatewayId;
    
    /**
     * 发布加密网关IP
     */
    private String publishGatewayIp;
    
    /**
     * 发布加密网关端口
     */
    private Integer publishGatewayPort;

    // === 分支标识 ===
    
    /**
     * 分支编码（B1/B2/B3...，主链路为NULL）
     */
    private String branchCode;
    
    /**
     * 分支名称
     */
    private String branchName;

    // === 核心代理转发信息（网关必需）===
    
    /**
     * 终端加密网关IP ★核心
     */
    private String terminalGatewayIp;
    
    /**
     * 终端加密网关端口
     */
    private Integer terminalGatewayPort;
    
    /**
     * 情报板IP ★核心
     */
    private String infoBoardIp;
    
    /**
     * 情报板端口
     */
    private Integer infoBoardPort;

    // === 时间戳（一致性校验）===
    
    /**
     * 下发时间戳
     */
    private Long timestamp;
}

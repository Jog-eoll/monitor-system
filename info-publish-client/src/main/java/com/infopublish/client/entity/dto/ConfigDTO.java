package com.infopublish.client.entity.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;

/**
 * 配置DTO（接收平台下发的配置，与 publish-gateway 一致）
 * 用于配置UDP代理规则
 */
@Data
public class ConfigDTO {
    
    /**
     * 任务链路ID（业务标识）
     */
    @NotNull(message = "链路ID不能为空")
    private Long chainId;
    
    /**
     * 分支代码（可选，用于多分支场景，如：B1, B2）
     */
    private String branchCode;
    
    /**
     * 发布网关监听IP（本机虚拟IP，伪装成情报板IP）
     */
    @NotBlank(message = "发布网关监听IP不能为空")
    private String publishGatewayIp;
    
    /**
     * 发布网关监听端口
     */
    @NotNull(message = "发布网关监听端口不能为空")
    @Min(value = 1024, message = "端口号必须大于等于1024")
    @Max(value = 65535, message = "端口号必须小于等于65535")
    private Integer publishGatewayPort;
    
    /**
     * 情报板IP（最终目标设备IP）
     */
    @NotBlank(message = "情报板IP不能为空")
    private String infoBoardIp;
    
    /**
     * 情报板端口（最终目标设备端口）
     */
    @NotNull(message = "情报板端口不能为空")
    @Min(value = 1, message = "端口号必须大于0")
    @Max(value = 65535, message = "端口号必须小于等于65535")
    private Integer infoBoardPort;
    
    /**
     * 是否启用加密（true-启用，false-明文转发）
     */
    @NotNull(message = "加密开关不能为空")
    private Boolean encryptEnabled;
    
    /**
     * 终端网关IP（解密网关的IP）
     */
    private String terminalGatewayIp;
    
    /**
     * 终端网关端口（解密网关监听的端口）
     */
    private Integer terminalGatewayPort;
    
    /**
     * 加密密钥（可选，为空时使用默认密钥）
     */
    private String encryptionKey;
    
    /**
     * 授权的源IP（Sigma发布服务器IP），仅允许该IP的UDP数据通过；为空则不做IP限制
     * 支持多个IP，用英文逗号分隔，如："192.168.113.102,192.168.1.100"
     */
    private String sourceIp;

    /**
     * 超时时间（秒，可选，默认30秒）
     */
    @Min(value = 1, message = "超时时间必须大于0")
    private Integer timeout = 30;
    
    /**
     * 传输协议：UDP（默认）或 TCP
     */
    private String protocol = "UDP";

    /**
     * 情报板厂家标识（用于协议解析策略选择，如 sigma/nova）
     * 为空时默认使用 sigma
     */
    private String manufacturer;

    /**
     * 发布网关MAC地址（客户端ARP绑定使用）
     * 客户端特有字段，由管控平台下发
     */
    private String gatewayMac;

    /**
     * 备注信息（可选）
     */
    private String remark;
}

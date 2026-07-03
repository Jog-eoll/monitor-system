package com.publishgateway.udpproxy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UDP代理规则实体类
 * 用于定义UDP数据转发规则
 * 
 * 数据流向：
 * 消息发布服务器 --[UDP]--> [监听IP:端口] --[UDP]--> [目标IP:端口] --> 情报板
 */
@Data
@TableName("udp_proxy_rule")
public class UdpProxyRule {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 规则ID（唯一标识）
     */
    @TableField("rule_id")
    private String ruleId;

    /**
     * 规则名称
     */
    private String ruleName;
    
    /**
     * 监听IP（可选，为空时监听所有网卡）
     */
    private String listenIp;
    
    /**
     * 监听端口（消息发布服务器发送数据到此端口）
     */
    private Integer listenPort;
    
    /**
     * 允许接入的源IP（消息发布服务器IP），为空则允许所有
     */
    private String sourceIp;
    
    /**
     * 转发目标IP（情报板IP）
     */
    private String targetIp;
    
    /**
     * 转发目标端口（情报板端口）
     */
    private Integer targetPort;
    
    /**
     * 规则状态：ENABLED/DISABLED
     */
    private String status;
    
    /**
     * 关联的情报板ID（可选，用于标识最终目标）
     */
    private String displayBoardId;
    
    /**
     * 网关序列号（对接管控平台使用）
     */
    private String gatewaySn;
    
    /**
     * 配置来源: platform-管控平台下发, local-本地配置
     */
    private String configSource;
    
    /**
     * 配置ID（管控平台的配置ID）
     */
    private Long configId;
    
    /**
     * 链路ID（管控平台链路ID）
     */
    private Long chainId;
    
    /**
     * 链路编码
     */
    private String chainCode;
    
    /**
     * 分支编码
     */
    private String branchCode;
    
    /**
     * 分支名称
     */
    private String branchName;
    
    /**
     * 配置版本号
     */
    private Integer configVersion;
    
    /**
     * 是否启用加密转发
     */
    private Boolean encryptEnabled;
    
    /**
     * 终端网关IP（加密转发的目标）
     */
    private String terminalGatewayIp;
    
    /**
     * 终端网关端口
     */
    private Integer terminalGatewayPort;
    
    /**
     * 传输协议：UDP（默认）或 TCP
     */
    private String protocol;

    /**
     * 情报板厂家标识（用于协议解析策略选择，如 sigma/nova）
     * 由管控平台下发，为空时默认使用 sigma
     */
    private String manufacturer;

    /**
     * 附加TCP代理端口列表（逗号分隔，如 "16602,16610"）
     * 诺瓦大屏使用多端口通信，需同时代理这些端口。
     * 不持久化到数据库，每次由管控平台下发。
     */
    @TableField(exist = false)
    private String additionalTcpPorts;

    /**
     * Additional UDP proxy ports, comma separated, for Nova discovery/control.
     */
    @TableField(exist = false)
    private String additionalUdpPorts;

    /**
     * 是否启用动态端口透明代理（仅 Nova 大屏使用）
     * 不持久化到数据库，每次由管控平台下发。
     */
    @TableField(exist = false)
    private Boolean dynamicPortProxyEnabled;

    /**
     * CatchAll 代理端口（动态端口透明代理的监听端口，默认 19999）
     * 不持久化到数据库，每次由管控平台下发。
     */
    @TableField(exist = false)
    private Integer catchAllProxyPort;

    /**
     * 备注
     */
    private String remark;
    
    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 逻辑删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}

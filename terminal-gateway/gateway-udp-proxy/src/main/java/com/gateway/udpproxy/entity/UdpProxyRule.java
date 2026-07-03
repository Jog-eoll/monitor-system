package com.gateway.udpproxy.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UdpProxyRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 规则唯一标识
     */
    @TableField("rule_id")
    private String ruleId;

    /**
     * 协议类型（UDP/TCP）
     */
    private String protocol;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 本机监听IP
     */
    private String listenIp;

    /**
     * 本机监听端口
     */
    private Integer listenPort;

    /**
     * 发布网关IP
     */
    private String sourceIp;

    /**
     * 情报板IP
     */
    private String targetIp;

    /**
     * 情报板端口
     */
    private Integer targetPort;

    /**
     * 规则状态
     */
    private String status;

    /**
     * 情报板设备ID
     */
    private String displayBoardId;

    /**
     * 网关序列号
     */
    private String gatewaySn;

    /**
     * 配置来源（MANUAL/PUSH）
     */
    private String configSource;

    /**
     * 配置ID
     */
    private Long configId;

    /**
     * 链路ID
     */
    private Long chainId;

    /**
     * 厂商标识（nova/sigma/colorlight...）
     */
    @TableField("manufacturer")
    private String manufacturer;

    /**
     * 是否启用解密
     */
    private Boolean decryptEnabled;

    /**
     * 附加TCP代理端口列表（逗号分隔，如 "16602,16610"）
     * 诺瓦大屏使用多端口通信，需要同时代理这些端口。
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
     * CatchAll 代理端口
     * 动态端口透明代理的监听端口，默认 19999。
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
     * 逻辑删除标记
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}

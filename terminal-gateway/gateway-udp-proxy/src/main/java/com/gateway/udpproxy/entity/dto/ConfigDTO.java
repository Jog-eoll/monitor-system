package com.gateway.udpproxy.entity.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;

/**
 * 终端网关配置DTO（接收平台下发的配置）
 * 用于配置终端解密网关的UDP代理规则
 */
@Data
public class ConfigDTO {

    /**
     * 任务链路ID（业务标识，与发布网关的chainId对应）
     */
    @NotNull(message = "链路ID不能为空")
    private Long chainId;

    /**
     * 终端网关监听端口（接收来自发布网关的加密数据）
     */
    @NotNull(message = "监听端口不能为空")
    @Min(value = 1024, message = "端口号必须大于等于1024")
    @Max(value = 65535, message = "端口号必须小于等于65535")
    private Integer listenPort;

    /**
     * 发布网关IP（数据来源IP，用于验证和日志）
     */
    @NotBlank(message = "发布网关IP不能为空")
    private String publishGatewayIp;

    /**
     * 实际允许的数据来源IP白名单。
     * <p>
     * 兼容发布服务器 / Sigma 客户端经由中继发包的场景；为空时回退使用
     * publishGatewayIp，保持旧配置行为不变。多个IP用英文逗号分隔。
     * </p>
     */
    private String sourceIp;

    /**
     * 情报板IP（解密后转发的目标IP）
     */
    @NotBlank(message = "情报板IP不能为空")
    private String infoBoardIp;

    /**
     * 情报板端口（解密后转发的目标端口）
     */
    @NotNull(message = "情报板端口不能为空")
    @Min(value = 1, message = "端口号必须大于0")
    @Max(value = 65535, message = "端口号必须小于等于65535")
    private Integer infoBoardPort;

    /**
     * 是否启用解密（true-解密，false-明文转发）
     */
    @NotNull(message = "解密开关不能为空")
    private Boolean decryptEnabled;

    /**
     * 解密密钥（可选，为空时使用默认密钥）
     */
    private String decryptionKey;

    /**
     * 分支代码（可选，用于多分支场景的日志标识）
     */
    private String branchCode;

    /**
     * 超时时间（秒，可选，默认30秒）
     */
    @Min(value = 1, message = "超时时间必须大于0")
    private Integer timeout = 30;

    /**
     * 备注信息（可选）
     */
    private String remark;

    /**
     * 传输协议：UDP（默认）或 TCP
     */
    private String protocol = "UDP";

    /**
     * 厂商标识（nova/sigma/colorlight...）
     */
    private String manufacturer;

    /**
     * 附加TCP代理端口列表（逗号分隔，如 "16602,16610"）
     * 诺瓦大屏使用多端口通信，需要同时代理这些端口。
     * 为空时仅代理主端口（infoBoardPort）。
     */
    private String additionalTcpPorts;

    /**
     * Additional UDP proxy ports, comma separated, for Nova discovery/control.
     */
    private String additionalUdpPorts;

    /**
     * 是否启用动态端口透明代理（仅 Nova 大屏需要）
     * Nova 通过控制通道协商随机端口传输文件，
     * 开启后 CatchAll 代理接收动态端口流量。
     * Sigma 等其他厂商此字段为 null/false，不受影响。
     */
    private Boolean dynamicPortProxyEnabled;

    /**
     * CatchAll 代理端口（动态端口透明代理的监听端口，默认 19999）
     */
    private Integer catchAllProxyPort;
}

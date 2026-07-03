
package com.infopublish.client.entity;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class GatewayConfig {

    /** 加密网关IP */
    @NotBlank(message = "加密网关IP不能为空")
    private String gatewayIp;

    /** 加密网关API端口 */
    @NotNull(message = "加密网关端口不能为空")
    @Min(value = 1, message = "端口范围: 1-65535")
    @Max(value = 65535, message = "端口范围: 1-65535")
    private Integer gatewayApiPort = 8081;

    /** 转发通道监听端口（加密网关上的） */
    @NotNull(message = "转发监听端口不能为空")
    private Integer forwardListenPort = 9000;

    /** 显控网关IP（可选） */
    private String displayGatewayIp;

    /** 显控网关端口（可选） */
    private Integer displayGatewayPort;

    /** 本机IP（用于白名单） */
    private String localIp;

    /** 通道名称 */
    private String channelName = "信息发布转发通道";

    /** 网关序列号 */
    private String gatewaySn = "GW-001";
}
package com.monitorplatform.device.entity.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 转发通道配置DTO（管控平台使用）
 */
@Data
public class ForwardChannelConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "通道名称不能为空")
    private String channelName;

    @NotBlank(message = "网关序列号不能为空")
    private String gatewaySn;

    @NotNull(message = "监听端口不能为空")
    @Min(value = 1, message = "端口范围: 1-65535")
    @Max(value = 65535, message = "端口范围: 1-65535")
    private Integer listenPort;

    @NotBlank(message = "转发目标IP不能为空")
    private String forwardIp;

    @NotNull(message = "转发目标端口不能为空")
    @Min(value = 1, message = "端口范围: 1-65535")
    @Max(value = 65535, message = "端口范围: 1-65535")
    private Integer forwardPort;

    /** 白名单IP数组 */
    private String[] sourceWhitelist;

    /** 是否启用 */
    private Boolean enabled;

    /** 配置来源 */
    private String configSource;

    /** 生效策略: immediate-立即, manual-手动 */
    private String effectTime;

    /** 备注 */
    private String remark;
}

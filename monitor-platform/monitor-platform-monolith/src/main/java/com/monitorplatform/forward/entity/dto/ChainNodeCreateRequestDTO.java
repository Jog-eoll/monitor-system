package com.monitorplatform.forward.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ChainNodeCreateRequestDTO {

    @NotNull(message = "chainId不能为空")
    private Long chainId;

    private Long parentId;

    @NotBlank(message = "deviceId不能为空")
    private String deviceId;

    private String deviceName;

    @NotBlank(message = "deviceType不能为空")
    private String deviceType;

    private String deviceIp;

    private String remark;
}

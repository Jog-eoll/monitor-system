package com.monitorplatform.forward.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ChainNodeUpdateRequestDTO {

    @NotNull(message = "nodeId不能为空")
    private Long nodeId;

    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String deviceIp;
    private String remark;
}

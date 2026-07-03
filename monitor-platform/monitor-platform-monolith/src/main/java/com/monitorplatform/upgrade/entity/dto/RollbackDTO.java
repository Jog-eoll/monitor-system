package com.monitorplatform.upgrade.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RollbackDTO {

    @NotNull(message = "packageId不能为空")
    private Long packageId;

    private String targetVersion;

    private String operator;

    private String remark;
}

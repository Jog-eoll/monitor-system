package com.monitorplatform.role.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RegionIdReq {

    @NotNull(message = "id不能为空")
    private Long id;
}

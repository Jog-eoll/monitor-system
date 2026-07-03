package com.monitorplatform.role.entity.dto;

/*
区域删除入参

*/

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RegionDeleteReq {

    @NotNull(message = "id不能为空")
    private Long id;
}
